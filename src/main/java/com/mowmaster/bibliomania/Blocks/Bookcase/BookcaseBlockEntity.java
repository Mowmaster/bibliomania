package com.mowmaster.bibliomania.Blocks.Bookcase;

import com.mowmaster.mowlib.blockentity.CBSyncedBlockEntity;
import java.util.stream.IntStream;
import java.util.Comparator;
import com.mowmaster.bibliomania.Blocks.Library.LibraryPriority;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryMember;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLink;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.tint.CBTintBlockEntity;
import com.mowmaster.mowlib.tint.CBTintData;
import com.mowmaster.mowlib.tint.ITintHolder;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// A placed Bookcase: six books (slots 0-5, top shelf from the left first) and the case's colour (a MowLib CBTintData).
// - Storage: for each transport type, the books' own handlers for it (ShelvedBook#shelfHandler) joined into one with
//   NeoForge's CombinedResourceHandler, which fills and empties them in order: the first book first. Offered as the
//   type's capability (BookcaseEvents); null (not offered) when no book holds that type.
//   Each book's handler works on its slot through ItemAccess.forHandlerIndexStrict: a changed book is swapped into
//   the slot inside the transaction, so a hopper's simulated insert changes nothing.
// - Ticking: each book's ShelvedBook#shelfTick; what they make goes into the item books (store), or out of the front.
// - Clients get the books (to draw them, and for the Reading Goggles) at most every 4 ticks.
public class BookcaseBlockEntity extends CBSyncedBlockEntity implements ITintHolder, LibraryMember, LibraryPriority.Holder
{
    public static final int SLOTS = 6;
    private static final String TAG_BOOKS = "books";

    private final CBTintData tints;
    private final BookSlots books = new BookSlots();
    private boolean needsSync;
    // Per slot: the game tick before which that book doesn't tick (it's waiting for room for its items)
    private final long[] pausedUntil = new long[SLOTS];
    private static final int PAUSE_TICKS = 20;
    private final LibraryLink libraryLink = new LibraryLink(this::libraryChanged);
    // Which Bookcases its library fills first (LibraryPriority)
    private int priority;
    private static final String TAG_PRIORITY = "priority";

    public BookcaseBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.BOOKCASE.get(), pos, state);
        this.tints = new CBTintData(this, 1);
    }

    // The six books: a NeoForge item stacks handler (so books can be swapped transactionally), one book per slot.
    // Never offered to hoppers itself; only the books' contents are.
    private class BookSlots extends ItemStacksResourceHandler
    {
        BookSlots()
        {
            super(SLOTS);
        }

        ItemStack get(int slot)
        {
            return stacks.get(slot);
        }

        @Override
        public boolean isValid(int index, ItemResource resource)
        {
            return BookcaseBlock.isShelfBook(resource.toStack(1));
        }

        @Override
        protected int getCapacity(int index, ItemResource resource)
        {
            return 1;
        }

        // After any change (a book in or out, or a book's contents changed through its handler)
        @Override
        protected void onContentsChanged(int index, ItemStack previous)
        {
            booksChanged(previous.getItem() != stacks.get(index).getItem());
        }
    }

    // ---- Books ----

    // The book in a slot (the stored stack itself: copy it before giving it away)
    public ItemStack getBook(int slot)
    {
        return books.get(slot);
    }

    public void setBook(int slot, ItemStack book)
    {
        books.set(slot, ItemResource.of(book), book.isEmpty() ? 0 : 1);
    }

    public List<ItemStack> getBooks()
    {
        List<ItemStack> list = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) list.add(books.get(slot));
        return list;
    }

    private void booksChanged(boolean bookAddedOrRemoved)
    {
        setChanged();
        needsSync = true;
        // Hoppers and pipes keep handlers they've asked for: tell them to ask again when the set of books changes
        if (bookAddedOrRemoved && level != null) level.invalidateCapabilities(worldPosition);
    }

    // How full the storage books are together (the average of each one's fullness, 0-1), or -1 if it has none
    public float fullness()
    {
        float total = 0;
        int counted = 0;
        for (int slot = 0; slot < SLOTS; slot++)
        {
            ItemStack book = books.get(slot);
            if (!(book.getItem() instanceof ShelvedBook shelved)) continue;
            float fullness = shelved.shelfFullness(book);
            if (fullness < 0) continue;
            total += fullness;
            counted++;
        }
        return counted == 0 ? -1f : total / counted;
    }

    // ---- Storage ----

    // The books' handlers for `type`, joined highest book priority first (equal ones in slot order), or null if no
    // book holds that type
    public <R extends Resource> @Nullable ResourceHandler<R> handler(TransportType<R> type)
    {
        List<ResourceHandler<R>> parts = new ArrayList<>();
        for (int slot : slotsByPriority())
        {
            ItemStack book = books.get(slot);
            if (book.isEmpty() || !(book.getItem() instanceof ShelvedBook shelved)) continue;
            ResourceHandler<R> part = shelved.shelfHandler(type, ItemAccess.forHandlerIndexStrict(books, slot));
            if (part != null) parts.add(part);
        }
        return parts.isEmpty() ? null : new CombinedResourceHandler<>(parts);
    }

    // One book's own handler for `type` (the Reading Lectern works on single books), or null if it has none
    public <R extends Resource> @Nullable ResourceHandler<R> bookHandler(int slot, TransportType<R> type)
    {
        ItemStack book = books.get(slot);
        if (book.isEmpty() || !(book.getItem() instanceof ShelvedBook shelved)) return null;
        return shelved.shelfHandler(type, ItemAccess.forHandlerIndexStrict(books, slot));
    }

    // A book was changed in place (by the Reading Lectern acting as it): save it and tell clients
    public void bookUpdated(int slot)
    {
        booksChanged(false);
    }

    // Puts all of `items` away, or none of them (a transaction that's only kept if everything fit). In a library: into
    // the whole library's item books (bookcase by bookcase); otherwise this bookcase's. Shelved books that make items
    // (harvests, eggs, barter goods) only go ahead when this works, so nothing is ever dropped on the ground.
    public boolean storeAll(List<ItemStack> items)
    {
        if (items.isEmpty()) return true;
        LibraryControllerBlockEntity library = library(level, worldPosition);
        ResourceHandler<ItemResource> handler = library != null ? null : handler(TransportTypes.ITEMS.get());
        if (library == null && handler == null) return false;
        // try-with-resources: the transaction is closed at the end of the block; closing without commit() undoes it
        try (Transaction transaction = Transaction.openRoot())
        {
            for (ItemStack item : items)
            {
                if (item.isEmpty()) continue;
                // In a library, the library's way in (enchanted books from a Book of Barter onto Chiseled Bookshelves)
                int stored = library != null ? library.insertItems(ItemResource.of(item), item.getCount(), transaction)
                        : handler.insert(ItemResource.of(item), item.getCount(), transaction);
                if (stored < item.getCount()) return false;
            }
            transaction.commit();
            return true;
        }
    }

    // A book couldn't put its items away: it waits a second before trying again (and doesn't do what made them)
    public void pause(int slot)
    {
        if (level != null && slot >= 0 && slot < SLOTS) pausedUntil[slot] = level.getGameTime() + PAUSE_TICKS;
    }

    // The middle of the block (where shelved books' mobs "are", for their drops and experience)
    public Vec3 center()
    {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    // ---- Ticking (server) ----

    public void serverTick(ServerLevel level)
    {
        for (int slot = 0; slot < SLOTS; slot++)
        {
            ItemStack book = books.get(slot);
            if (level.getGameTime() < pausedUntil[slot]) continue;
            if (!book.isEmpty() && book.getItem() instanceof ShelvedBook shelved && shelved.shelfTick(book, level, this, slot))
            {
                setChanged();
                needsSync = true;
            }
        }
        if (needsSync && level.getGameTime() % 4 == 0)
        {
            needsSync = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }



    // ---- Library priority (LibraryPriority) ----

    @Override
    public int getPriority()
    {
        return priority;
    }

    @Override
    public void setPriority(int newPriority)
    {
        if (newPriority == priority) return;
        priority = newPriority;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public int maxPriority()
    {
        return LibraryPriority.MAX_CASE;
    }

    // The slots, highest book priority first. A stream: 0-5 boxed to Integers, sorted (a stable sort keeps equal
    // ones in slot order) by the book's priority, highest first (hence the minus)
    private List<Integer> slotsByPriority()
    {
        return IntStream.range(0, SLOTS).boxed().sorted(Comparator.comparingInt((Integer slot) -> -LibraryPriority.ofBook(books.get(slot)))).toList();
    }

    // ---- Library (LibraryMember: which library it's in, and the Librarian's Seal lock) ----

    @Override public @Nullable BlockPos getLibrary() { return libraryLink.library(); }
    @Override public void setLibrary(@Nullable BlockPos controller) { libraryLink.setLibrary(controller); }
    @Override public boolean isLocked() { return libraryLink.locked(); }
    @Override public void setLocked(boolean locked) { libraryLink.setLocked(locked); }

    // Saved, and clients told (the Reading Goggles show it)
    private void libraryChanged()
    {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- Colour (ITintHolder, read by MowLib's tint source and the colour picker) ----

    @Override public int getColor(int region) { return tints.getColor(region); }
    @Override public void setColor(int region, int color) { tints.setColor(region, color); }
    @Override public int getTint(int tintIndex) { return tints.getTint(tintIndex); }

    // ---- Block <-> item: the colour goes with the item (dyed_color); the books don't, they drop ----

    @Override
    protected void applyImplicitComponents(DataComponentGetter components)
    {
        super.applyImplicitComponents(components);
        tints.applyItemColor(components, 0);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        tints.collectItemColor(components);
    }

    // Broken (or blown up): the books drop
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level == null) return;
        for (int slot = 0; slot < SLOTS; slot++)
        {
            ItemStack book = books.get(slot);
            if (!book.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), book.copy());
        }
    }

    // ---- Saving and syncing ----

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        // putChild: the handler saves itself into its own sub-object (NeoForge's ValueIOSerializable)
        output.putChild(TAG_BOOKS, books);
        tints.save(output);
        libraryLink.save(output);
        output.putInt(TAG_PRIORITY, priority);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        books.deserialize(input.childOrEmpty(TAG_BOOKS));
        libraryLink.load(input);
        priority = input.getIntOr(TAG_PRIORITY, 0);
        // CBTintData#load re-meshes the chunk on clients: only when the colour actually changed (book updates come
        // often while hoppers run)
        if (level == null || !level.isClientSide() || input.getIntOr("color", CBTintBlockEntity.UNDYED) != tints.getColor(0))
        {
            tints.load(input);
        }
    }

}
