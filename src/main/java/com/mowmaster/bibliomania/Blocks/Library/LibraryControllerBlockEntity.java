package com.mowmaster.bibliomania.Blocks.Library;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookItem;
import com.mowmaster.bibliomania.Blocks.CardCatalog.DrawerEntry;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlockEntity;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlock;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.Blocks.Lectern.ReadingLecternBlockEntity;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// A Library Controller: the core of a library. Its members are found by walking from the controller through
// touching library members (LibraryMember: Bookcases, Reading Lecterns...), one block to the next, and from each block
// linked with the Librarian's Quill (with whatever touches those). Blocks the Quill removed are skipped (and not walked
// through). A member already in another working library stays in that one.
// - The walk runs on the server every SCAN_TICKS (and straight after a Quill change), at most MAX_MEMBERS blocks.
//   Members are told which library they're in (LibraryMember#setLibrary); ones that left are told they're out.
// - Everything else asks the controller: its bookcases in walk order (the order a lectern pages through them), its
//   lecterns. Only member counts go to clients (for the Reading Goggles), never the lists.
// - Linked and removed positions, and the lock, are saved.
public class LibraryControllerBlockEntity extends BlockEntity implements LibraryLockable
{
    public static final int MAX_MEMBERS = 1024;
    // How far a linked block may be (each way)
    public static final int LINK_RANGE = 64;
    private static final int SCAN_TICKS = 40;

    private static final String TAG_LINKED = "linked";
    private static final String TAG_REMOVED = "removed";
    private static final String TAG_LOCKED = "locked";
    private static final String TAG_BOOKCASES = "bookcases";
    private static final String TAG_LECTERNS = "lecterns";

    // LinkedHashSet: no duplicates, kept in the order they were added
    private final Set<BlockPos> linked = new LinkedHashSet<>();
    private final Set<BlockPos> removed = new LinkedHashSet<>();
    private boolean locked;
    // Server: the members found by the last walk, in walk order
    private List<BlockPos> members = List.of();
    private Set<BlockPos> memberSet = Set.of();
    private long nextScan;
    // Synced: how many of each
    private int bookcaseCount;
    private int lecternCount;

    public LibraryControllerBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.LIBRARY_CONTROLLER.get(), pos, state);
    }

    // ---- Members ----

    public boolean isMember(BlockPos pos)
    {
        return memberSet.contains(pos);
    }

    public List<BlockPos> members()
    {
        return members;
    }

    // The library's Bookcases, in walk order (those loaded)
    public List<BookcaseBlockEntity> bookcases()
    {
        List<BookcaseBlockEntity> found = new ArrayList<>();
        if (level == null) return found;
        for (BlockPos pos : members)
        {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BookcaseBlockEntity shelf) found.add(shelf);
        }
        return found;
    }

    public List<ReadingLecternBlockEntity> lecterns()
    {
        List<ReadingLecternBlockEntity> found = new ArrayList<>();
        if (level == null) return found;
        for (BlockPos pos : members)
        {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ReadingLecternBlockEntity lectern) found.add(lectern);
        }
        return found;
    }

    public List<CardCatalogBlockEntity> catalogs()
    {
        List<CardCatalogBlockEntity> found = new ArrayList<>();
        if (level == null) return found;
        for (BlockPos pos : members)
        {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CardCatalogBlockEntity catalog) found.add(catalog);
        }
        return found;
    }

    // ---- The whole library's items and books (server; used by Card Catalogs and Reading Lecterns) ----

    // How many of `item` (exactly: same components) the library's item books hold together
    public long countItem(ItemStack item)
    {
        ItemResource wanted = ItemResource.of(item);
        long total = 0;
        for (BookcaseBlockEntity shelf : bookcases())
        {
            ResourceHandler<ItemResource> items = shelf.handler(TransportTypes.ITEMS.get());
            if (items == null) continue;
            for (int index = 0; index < items.size(); index++)
            {
                if (items.getResource(index).equals(wanted)) total += items.getAmountAsLong(index);
            }
        }
        return total;
    }

    // Takes up to `amount` of `item` from the whole library (bookcase by bookcase, first book first)
    public int extractItem(ItemStack item, int amount)
    {
        ItemResource wanted = ItemResource.of(item);
        int taken = 0;
        for (BookcaseBlockEntity shelf : bookcases())
        {
            if (taken >= amount) break;
            ResourceHandler<ItemResource> items = shelf.handler(TransportTypes.ITEMS.get());
            if (items == null) continue;
            try (Transaction transaction = Transaction.openRoot())
            {
                taken += items.extract(wanted, amount - taken, transaction);
                transaction.commit();
            }
        }
        return taken;
    }

    // Where the book with this bibliomania:book_id is on the library's shelves
    public record BookPlace(BookcaseBlockEntity shelf, int slot) {}

    public @Nullable BookPlace findBook(UUID id)
    {
        for (BookcaseBlockEntity shelf : bookcases())
        {
            for (int slot = 0; slot < BookcaseBlockEntity.SLOTS; slot++)
            {
                if (id.equals(shelf.getBook(slot).get(DataComponentRegistry.BOOK_ID.get()))) return new BookPlace(shelf, slot);
            }
        }
        return null;
    }

    // Whether a card catalog drawer's item or book is in the library
    public boolean has(DrawerEntry entry)
    {
        return switch (entry.kind())
        {
            case ITEM -> countItem(entry.icon()) > 0;
            case BOOK, TRADE -> entry.book().map(this::findBook).isPresent();
        };
    }

    // ---- Card catalog drawers and lecterns ----

    // Closes every open drawer in the library except the catalog at `keep` (each with the barrel sound)
    public void closeDrawers(ServerLevel level, BlockPos keep)
    {
        for (CardCatalogBlockEntity catalog : catalogs())
        {
            BlockPos pos = catalog.getBlockPos();
            BlockState state = catalog.getBlockState();
            if (pos.equals(keep) || state.getValue(CardCatalogBlock.OPEN) == 0) continue;
            level.setBlock(pos, state.setValue(CardCatalogBlock.OPEN, 0), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.8f, 1f);
        }
    }

    // A drawer was opened: every lectern of the library shows its item (from the whole library) or turns to its book
    public void select(ServerLevel level, DrawerEntry entry)
    {
        BookPlace place = entry.book().map(this::findBook).orElse(null);
        // A trade: the Ledger's own page set to that trade first
        if (entry.kind() == DrawerEntry.Kind.TRADE && place != null)
        {
            ItemStack ledger = place.shelf().getBook(place.slot());
            if (ledger.getItem() instanceof VillagerBookItem item)
            {
                item.setPage(ledger, entry.trade());
                place.shelf().bookUpdated(place.slot());
            }
        }
        for (ReadingLecternBlockEntity lectern : lecterns())
        {
            if (entry.kind() == DrawerEntry.Kind.ITEM) lectern.selectItem(level, entry.icon());
            else if (place != null) lectern.showBook(level, place.shelf(), place.slot());
        }
    }

    // A drawer was closed: the lecterns go back to their pages
    public void clearSelection(ServerLevel level)
    {
        for (ReadingLecternBlockEntity lectern : lecterns()) lectern.clearSelection(level);
    }

    public int getBookcaseCount()
    {
        return bookcaseCount;
    }

    public int getLecternCount()
    {
        return lecternCount;
    }

    // ---- The walk (server) ----

    public void serverTick(ServerLevel level)
    {
        if (level.getGameTime() >= nextScan) scan(level);
    }

    public void scan(ServerLevel level)
    {
        nextScan = level.getGameTime() + SCAN_TICKS;
        Set<BlockPos> found = new LinkedHashSet<>();
        // ArrayDeque: a queue; walking breadth-first finds the nearest members first
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) queue.add(worldPosition.relative(direction));
        queue.addAll(linked);
        while (!queue.isEmpty() && found.size() < MAX_MEMBERS)
        {
            BlockPos pos = queue.poll();
            if (pos.equals(worldPosition) || found.contains(pos) || removed.contains(pos) || !level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof LibraryMember member)) continue;
            if (inOtherLibrary(level, member, pos)) continue;
            found.add(pos.immutable());
            for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }

        // Tell the ones that left, then the ones in it
        for (BlockPos old : members)
        {
            if (!found.contains(old) && level.isLoaded(old) && level.getBlockEntity(old) instanceof LibraryMember member
                    && worldPosition.equals(member.getLibrary()))
            {
                member.setLibrary(null);
            }
        }
        int bookcases = 0, lecterns = 0;
        for (BlockPos pos : found)
        {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof LibraryMember member) member.setLibrary(worldPosition);
            if (blockEntity instanceof BookcaseBlockEntity) bookcases++;
            if (blockEntity instanceof ReadingLecternBlockEntity) lecterns++;
        }
        members = List.copyOf(found);
        memberSet = Set.copyOf(found);
        if (bookcases != bookcaseCount || lecterns != lecternCount)
        {
            bookcaseCount = bookcases;
            lecternCount = lecterns;
            sync();
        }
    }

    // In another library whose controller is still there (and still has it)
    private boolean inOtherLibrary(ServerLevel level, LibraryMember member, BlockPos pos)
    {
        BlockPos other = member.getLibrary();
        if (other == null || other.equals(worldPosition) || !level.isLoaded(other)) return false;
        return level.getBlockEntity(other) instanceof LibraryControllerBlockEntity controller && controller.isMember(pos);
    }

    // ---- The Librarian's Quill ----

    public enum LinkChange { LINKED, UNLINKED, REMOVED, RESTORED, TOO_FAR, NOT_A_MEMBER }

    // A Quill click on `pos`: a linked block is unlinked; a touching member is removed; a removed block comes back; any
    // other library block within LINK_RANGE is linked
    public LinkChange toggle(ServerLevel level, BlockPos pos)
    {
        BlockPos target = pos.immutable();
        LinkChange change;
        if (linked.remove(target)) change = LinkChange.UNLINKED;
        else if (removed.remove(target)) change = LinkChange.RESTORED;
        else if (memberSet.contains(target))
        {
            removed.add(target);
            change = LinkChange.REMOVED;
        }
        else if (!(level.getBlockEntity(target) instanceof LibraryMember)) return LinkChange.NOT_A_MEMBER;
        else if (Math.abs(target.getX() - worldPosition.getX()) > LINK_RANGE || Math.abs(target.getY() - worldPosition.getY()) > LINK_RANGE
                || Math.abs(target.getZ() - worldPosition.getZ()) > LINK_RANGE) return LinkChange.TOO_FAR;
        else
        {
            linked.add(target);
            change = LinkChange.LINKED;
        }
        setChanged();
        scan(level);
        return change;
    }

    // ---- Lock ----

    @Override
    public boolean isLocked()
    {
        return locked;
    }

    @Override
    public void setLocked(boolean newLocked)
    {
        locked = newLocked;
        sync();
    }

    // Broken: every member is told it's no longer in a library
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level == null) return;
        for (BlockPos member : members)
        {
            if (level.isLoaded(member) && level.getBlockEntity(member) instanceof LibraryMember libraryMember
                    && pos.equals(libraryMember.getLibrary()))
            {
                libraryMember.setLibrary(null);
            }
        }
    }

    // ---- Saving and syncing ----

    private void sync()
    {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        // listOf(): a codec for a list of positions
        output.store(TAG_LINKED, BlockPos.CODEC.listOf(), List.copyOf(linked));
        output.store(TAG_REMOVED, BlockPos.CODEC.listOf(), List.copyOf(removed));
        output.putBoolean(TAG_LOCKED, locked);
        output.putInt(TAG_BOOKCASES, bookcaseCount);
        output.putInt(TAG_LECTERNS, lecternCount);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        linked.clear();
        linked.addAll(input.read(TAG_LINKED, BlockPos.CODEC.listOf()).orElse(List.of()));
        removed.clear();
        removed.addAll(input.read(TAG_REMOVED, BlockPos.CODEC.listOf()).orElse(List.of()));
        locked = input.getBooleanOr(TAG_LOCKED, false);
        bookcaseCount = input.getIntOr(TAG_BOOKCASES, 0);
        lecternCount = input.getIntOr(TAG_LECTERNS, 0);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
