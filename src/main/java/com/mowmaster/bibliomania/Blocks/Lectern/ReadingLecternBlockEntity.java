package com.mowmaster.bibliomania.Blocks.Lectern;

import net.minecraft.core.component.DataComponents;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryMember;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLink;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.display.ISpinningItemHolder;
import com.mowmaster.mowlib.paging.IPageHolder;
import com.mowmaster.mowlib.paging.IPagedStorageItem;
import com.mowmaster.mowlib.paging.PagedContents;
import com.mowmaster.mowlib.tint.CBTintBlockEntity;
import com.mowmaster.mowlib.tint.CBTintData;
import com.mowmaster.mowlib.tint.ITintHolder;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.mowlib.workcard.IWorkCard;
import com.mowmaster.mowlib.workcard.IWorkCardHolder;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.Bookcase.ShelvedBook;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// A placed Reading Lectern: a window onto the Bookcases its work card points at.
// - Linked cases: the card's positions within RANGE that hold a Bookcase, in the card's order (rescanned every few
//   seconds and whenever the card changes; positions only, never saved).
// - Pages: every page of every book in the linked cases, in order: case by case, slot by slot, and for item books each
//   of their pages; any other book is one page of its own (the lectern then acts as that book: LecternActions).
//   No sorting and no merging: the n-th page is simply the n-th slot. The open page number is saved.
// - Only the server walks the cases. Clients get the open page's item, its amount and its book, and the page count:
//   enough to draw it (MowLib's spinning item) and for the Reading Goggles, whatever the size of the library.
public class ReadingLecternBlockEntity extends BlockEntity implements ITintHolder, IWorkCardHolder, IPageHolder, ISpinningItemHolder, LibraryMember
{
    // How far (each way) the work card's positions may be
    public static final int RANGE = 16;
    // How often the card's positions are looked through again, and the open page re-read, in ticks
    private static final int RESCAN_TICKS = 100;
    private static final int REFRESH_TICKS = 10;

    private static final String TAG_CARD = "card";
    private static final String TAG_PAGE = "page";
    private static final String TAG_PAGES = "pages";
    private static final String TAG_SHOWN = "shown";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_BOOK = "book";
    private static final String TAG_BOOK_PAGE = "book_page";

    private final CBTintData tints;
    private final LibraryLink libraryLink = new LibraryLink(this::libraryChanged);
    private ItemStack card = ItemStack.EMPTY;
    private int page;
    // Server: the linked cases' positions
    private List<BlockPos> cases = List.of();
    private long nextScan;
    // Synced: the page count, and what the open page shows
    private int pageCount;
    private ItemStack shown = ItemStack.EMPTY;
    private int shownAmount;
    private ItemStack shownBook = ItemStack.EMPTY;
    private int shownBookPage = -1;

    public ReadingLecternBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.READING_LECTERN.get(), pos, state);
        this.tints = new CBTintData(this, 1);
    }

    // One page: a slot of a linked case, and the page of that book (-1: the whole book, for books that aren't pages
    // of items)
    public record Page(BookcaseBlockEntity shelf, int slot, int bookPage) {}

    // ---- Work card ----

    public ItemStack getCard()
    {
        return card;
    }

    public boolean hasCard()
    {
        return !card.isEmpty();
    }

    // Puts a card in (EMPTY takes it out); returns the card it held before
    public ItemStack swapCard(ItemStack newCard)
    {
        ItemStack old = card;
        card = newCard;
        nextScan = 0; // look through the new card's positions straight away
        if (level instanceof ServerLevel serverLevel) refresh(serverLevel, true);
        setChanged();
        return old;
    }

    @Override
    public int getMaxAreaSize()
    {
        return RANGE * 2;
    }

    // The cases it reads: in a library, the library's Bookcases (its own work card is ignored); otherwise the work
    // card's, in the card's order (those still there)
    public List<BookcaseBlockEntity> cases()
    {
        LibraryControllerBlockEntity library = library(level, worldPosition);
        if (library != null) return library.bookcases();
        List<BookcaseBlockEntity> found = new ArrayList<>();
        if (level == null) return found;
        for (BlockPos pos : cases)
        {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BookcaseBlockEntity shelf) found.add(shelf);
        }
        return found;
    }

    private void rescan()
    {
        if (level == null || !(card.getItem() instanceof IWorkCard workCard))
        {
            cases = List.of();
            return;
        }
        // LinkedHashSet: no position twice, in the order they were added
        Set<BlockPos> found = new LinkedHashSet<>();
        for (BlockPos pos : workCard.getPositions(card, this, RANGE))
        {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BookcaseBlockEntity) found.add(pos.immutable());
        }
        cases = List.copyOf(found);
    }

    // ---- Pages ----

    // How many pages a book is: an item book's pages, any other book 1
    private static int pagesOf(ItemStack book)
    {
        if (book.isEmpty()) return 0;
        if (book.getItem() instanceof IPagedStorageItem paged) return paged.contents(book).pages();
        return 1;
    }

    private int countPages()
    {
        int total = 0;
        for (BookcaseBlockEntity shelf : cases())
        {
            for (int slot = 0; slot < BookcaseBlockEntity.SLOTS; slot++) total += pagesOf(shelf.getBook(slot));
        }
        return total;
    }

    // Page `index` (0-based), or null if there's no such page
    public @Nullable Page resolve(int index)
    {
        if (index < 0) return null;
        int remaining = index;
        for (BookcaseBlockEntity shelf : cases())
        {
            for (int slot = 0; slot < BookcaseBlockEntity.SLOTS; slot++)
            {
                ItemStack book = shelf.getBook(slot);
                int pages = pagesOf(book);
                if (remaining < pages) return new Page(shelf, slot, book.getItem() instanceof IPagedStorageItem ? remaining : -1);
                remaining -= pages;
            }
        }
        return null;
    }

    // Server: the open page
    public @Nullable Page currentPage()
    {
        return resolve(page);
    }

    // ---- Card catalogs (server): what an open drawer asks for ----

    // shownBookPage when it shows a card catalog item from the whole library (-1 is a whole book, 0+ a book's page)
    public static final int WHOLE_LIBRARY = -2;

    // The item an open card catalog drawer picked, or null (then the lectern shows its page)
    private @Nullable ItemStack selectedItem;

    public void selectItem(ServerLevel level, ItemStack item)
    {
        selectedItem = item.copyWithCount(1);
        refresh(level, true);
    }

    // Turns to a book (and leaves any card catalog item)
    public void showBook(ServerLevel level, BookcaseBlockEntity shelf, int slot)
    {
        selectedItem = null;
        int index = 0;
        for (BookcaseBlockEntity each : cases())
        {
            for (int s = 0; s < BookcaseBlockEntity.SLOTS; s++)
            {
                if (each == shelf && s == slot)
                {
                    page = index;
                    refresh(level, true);
                    return;
                }
                index += pagesOf(each.getBook(s));
            }
        }
        refresh(level, true);
    }

    public void clearSelection(ServerLevel level)
    {
        if (selectedItem == null) return;
        selectedItem = null;
        refresh(level, true);
    }

    // Whether the open page is a whole book (clients: from what was synced)
    public boolean showsWholeBook()
    {
        return shownBookPage == -1;
    }

    // Server: whether the open page is a whole book (the lectern then acts as that book)
    public boolean isBookOpen(ServerLevel level)
    {
        if (selectedItem != null) return false;
        Page current = currentPage();
        return current != null && current.bookPage() < 0;
    }

    // ---- IPageHolder: arrows and MowLib's page keys ----

    // MowLib's page keys call this (without saying who pressed them): if a sneaking player is looking at this
    // lectern, the open book's own pages turn instead (a Ledger's trades)
    @Override
    public void turnPage(int direction)
    {
        if (level instanceof ServerLevel serverLevel && sneakingReader(serverLevel) && LecternActions.turnBookPage(serverLevel, direction, this)) return;
        IPageHolder.super.turnPage(direction);
    }

    private boolean sneakingReader(ServerLevel level)
    {
        for (Player player : level.players())
        {
            if (!player.isShiftKeyDown() || player.distanceToSqr(center()) > 64) continue;
            // pick: what the player's crosshair is on, worked out on the server (blocks only: false = no fluids)
            if (player.pick(player.blockInteractionRange(), 1f, false) instanceof BlockHitResult hit && hit.getBlockPos().equals(worldPosition)) return true;
        }
        return false;
    }

    private Vec3 center()
    {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    @Override
    public int getPageCount()
    {
        return pageCount;
    }

    @Override
    public int getPage()
    {
        return page;
    }

    @Override
    public void setPage(int newPage)
    {
        // Turning a page leaves a card catalog item behind: back to the pages
        selectedItem = null;
        page = pageCount <= 0 ? 0 : Math.floorMod(newPage, pageCount);
        setChanged();
        if (level instanceof ServerLevel serverLevel) refresh(serverLevel, true);
    }

    // ---- What the open page shows (synced) ----

    public ItemStack getShown()
    {
        return shown;
    }

    public int getShownAmount()
    {
        return shownAmount;
    }

    // The book the open page is in (or is)
    public ItemStack getShownBook()
    {
        return shownBook;
    }

    // The open page's page in its book, or -1 when the page is a whole book
    public int getShownBookPage()
    {
        return shownBookPage;
    }

    // The spinning item above the lectern (MowLib's SpinningItemRenderer): the page's item, or the book itself
    @Override
    public ItemStack getDisplayedItem()
    {
        return shown;
    }

    @Override
    public double getDisplayHeight()
    {
        return 1.25;
    }

    // ---- The open book, as placed (client) ----
    // A client-only copy of the open book's block entity (made from the book item, never added to the level), so the
    // lectern can draw the book the way it looks placed: its block model and its own renderer (crop, villager...).
    // Made again whenever the open book changes.

    private @Nullable BlockEntity displayBook;
    private ItemStack displayBookStack = ItemStack.EMPTY;

    // The open whole book as a placed block entity, or null (no book open, or a page of items)
    public @Nullable BlockEntity getDisplayBook()
    {
        if (level == null || shownBookPage >= 0 || shownBook.isEmpty()) return null;
        if (displayBook != null && ItemStack.matches(displayBookStack, shownBook)) return displayBook;
        displayBookStack = shownBook.copy();
        displayBook = null;
        if (!(shownBook.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof EntityBlock entityBlock)) return null;
        BlockState state = item.getBlock().defaultBlockState();
        if (state.hasProperty(BookBlock.FACING)) state = state.setValue(BookBlock.FACING, getBlockState().getValue(ReadingLecternBlock.FACING));
        if (state.hasProperty(BookBlock.THICKNESS) && shownBook.getItem() instanceof ShelvedBook shelved)
        {
            float fullness = shelved.shelfFullness(shownBook);
            if (fullness >= 0) state = state.setValue(BookBlock.THICKNESS, BookBlock.thicknessFor(fullness));
        }
        BlockEntity book = entityBlock.newBlockEntity(worldPosition, state);
        if (book == null) return null;
        book.setLevel(level);
        // The item's data components (its contents) onto the block entity, as when it's placed
        book.applyComponentsFromItemStack(shownBook);
        displayBook = book;
        return book;
    }

    // ---- Ticking and refreshing (server) ----

    public void serverTick(ServerLevel level)
    {
        long now = level.getGameTime();
        if (now >= nextScan)
        {
            nextScan = now + RESCAN_TICKS;
            rescan();
        }
        if (now % REFRESH_TICKS == 0) refresh(level, false);
    }

    // Counts the pages again and reads the open page; tells clients if anything they see changed (or `force`)
    public void refresh(ServerLevel level, boolean force)
    {
        int count = countPages();
        // A card catalog item: that item from the whole library, shown with a glint
        LibraryControllerBlockEntity library = library(level, worldPosition);
        if (selectedItem != null && library == null) selectedItem = null;
        if (selectedItem != null)
        {
            ItemStack glinting = selectedItem.copyWithCount(1);
            // ENCHANTMENT_GLINT_OVERRIDE: true always shimmers like an enchanted item
            glinting.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            int total = (int) Math.min(Integer.MAX_VALUE, library.countItem(selectedItem));
            boolean changed = force || count != pageCount || total != shownAmount || shownBookPage != WHOLE_LIBRARY
                    || !ItemStack.matches(glinting, shown);
            pageCount = count;
            shown = glinting;
            shownAmount = total;
            shownBook = ItemStack.EMPTY;
            shownBookPage = WHOLE_LIBRARY;
            if (changed)
            {
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
            return;
        }
        int open = count <= 0 ? 0 : Math.min(page, count - 1);
        Page current = resolve(open);
        ItemStack newShown = ItemStack.EMPTY;
        int newAmount = 0;
        ItemStack newBook = ItemStack.EMPTY;
        int newBookPage = -1;
        if (current != null)
        {
            newBook = current.shelf().getBook(current.slot()).copy();
            newBookPage = current.bookPage();
            if (newBookPage < 0)
            {
                newShown = newBook.copyWithCount(1);
                newAmount = 1;
            }
            else if (newBook.getItem() instanceof IPagedStorageItem paged)
            {
                PagedContents contents = paged.contents(newBook);
                ResourceStack<ItemResource> stack = contents.stack(newBookPage);
                newShown = stack.isEmpty() ? ItemStack.EMPTY : stack.resource().toStack(1);
                newAmount = stack.amount();
            }
        }
        boolean changed = force || count != pageCount || open != page || newAmount != shownAmount || newBookPage != shownBookPage
                || !ItemStack.matches(newShown, shown) || !ItemStack.matches(newBook, shownBook);
        pageCount = count;
        page = open;
        shown = newShown;
        shownAmount = newAmount;
        shownBook = newBook;
        shownBookPage = newBookPage;
        if (changed)
        {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- Items in and out ----

    // Puts as much of `stack` as fits into the linked cases' item books (case by case, first book first), taking it
    // from the stack; returns how many went in
    public int store(ItemStack stack)
    {
        if (stack.isEmpty()) return 0;
        int stored = 0;
        for (BookcaseBlockEntity shelf : cases())
        {
            ResourceHandler<ItemResource> items = shelf.handler(TransportTypes.ITEMS.get());
            if (items == null) continue;
            // try-with-resources: the transaction closes at the end of the block, and only counts if committed
            try (Transaction transaction = Transaction.openRoot())
            {
                stored += items.insert(ItemResource.of(stack), stack.getCount() - stored, transaction);
                transaction.commit();
            }
            if (stored >= stack.getCount()) break;
        }
        stack.shrink(stored);
        if (stored > 0 && level instanceof ServerLevel serverLevel) refresh(serverLevel, false);
        return stored;
    }

    // Takes up to `amount` (no more than a stack) from the open page, if it's a page of items
    public ItemStack take(int amount)
    {
        // A card catalog item: from anywhere in the library
        LibraryControllerBlockEntity library = level instanceof ServerLevel ? library(level, worldPosition) : null;
        if (selectedItem != null && library != null)
        {
            int taken = library.extractItem(selectedItem, Math.min(amount, selectedItem.getMaxStackSize()));
            if (level instanceof ServerLevel serverLevel) refresh(serverLevel, false);
            return taken > 0 ? selectedItem.copyWithCount(taken) : ItemStack.EMPTY;
        }
        Page current = currentPage();
        if (current == null || current.bookPage() < 0) return ItemStack.EMPTY;
        ResourceHandler<ItemResource> items = current.shelf().bookHandler(current.slot(), TransportTypes.ITEMS.get());
        if (items == null || current.bookPage() >= items.size()) return ItemStack.EMPTY;
        ItemResource resource = items.getResource(current.bookPage());
        if (resource.isEmpty()) return ItemStack.EMPTY;
        int extracted;
        try (Transaction transaction = Transaction.openRoot())
        {
            extracted = items.extract(current.bookPage(), resource, Math.min(amount, resource.toStack(1).getMaxStackSize()), transaction);
            transaction.commit();
        }
        if (level instanceof ServerLevel serverLevel) refresh(serverLevel, false);
        return extracted > 0 ? resource.toStack(extracted) : ItemStack.EMPTY;
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

    // ---- Colour ----

    @Override public int getColor(int region) { return tints.getColor(region); }
    @Override public void setColor(int region, int color) { tints.setColor(region, color); }
    @Override public int getTint(int tintIndex) { return tints.getTint(tintIndex); }

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

    // Broken: the work card drops
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level != null && !card.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), card.copy());
    }

    // ---- Saving and syncing ----
    // ValueOutput#store / ValueInput#read save any value with a Codec (ItemStack.OPTIONAL_CODEC allows empty stacks)

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.store(TAG_CARD, ItemStack.OPTIONAL_CODEC, card);
        output.putInt(TAG_PAGE, page);
        output.putInt(TAG_PAGES, pageCount);
        output.store(TAG_SHOWN, ItemStack.OPTIONAL_CODEC, shown);
        output.putInt(TAG_AMOUNT, shownAmount);
        output.store(TAG_BOOK, ItemStack.OPTIONAL_CODEC, shownBook);
        output.putInt(TAG_BOOK_PAGE, shownBookPage);
        tints.save(output);
        libraryLink.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        card = input.read(TAG_CARD, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        page = input.getIntOr(TAG_PAGE, 0);
        pageCount = input.getIntOr(TAG_PAGES, 0);
        shown = input.read(TAG_SHOWN, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        shownAmount = input.getIntOr(TAG_AMOUNT, 0);
        shownBook = input.read(TAG_BOOK, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        shownBookPage = input.getIntOr(TAG_BOOK_PAGE, -1);
        libraryLink.load(input);
        // Only re-mesh the chunk on clients when the colour really changed (pages change often)
        if (level == null || !level.isClientSide() || input.getIntOr("color", CBTintBlockEntity.UNDYED) != tints.getColor(0))
        {
            tints.load(input);
        }
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
