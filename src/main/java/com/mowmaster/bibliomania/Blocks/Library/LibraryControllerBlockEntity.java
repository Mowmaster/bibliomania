package com.mowmaster.bibliomania.Blocks.Library;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.mowlib.tint.ITintHolder;
import com.mowmaster.mowlib.tint.CBTintBlockEntity;
import com.mowmaster.mowlib.tint.CBTintData;
import java.util.Comparator;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import com.mowmaster.bibliomania.BibliomaniaTags;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookItem;
import com.mowmaster.bibliomania.Blocks.StorageBook.BookStorage;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import com.mowmaster.mowlib.transport.TransportType;
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
// - The walk runs on the server every SCAN_TICKS (and straight after a Quill change), at most maxMembers() blocks (server config).
//   Members are told which library they're in (LibraryMember#setLibrary); ones that left are told they're out.
// - Everything else asks the controller: its bookcases in walk order (the order a lectern pages through them), its
//   lecterns. Only member counts go to clients (for the Reading Goggles), never the lists.
// - Linked and removed positions, and the lock, are saved.
// - Vanilla Chiseled Bookshelves join too (touching, or linked with the Quill), each one its own small store of six
//   books: enchanted and written books (#bibliomania:library_shelf_books) go onto them first, then into the item books
//   (insertItems); everything that takes items out (lecterns, requestors, the dispenser) finds books on them too. A
//   vanilla block can't be told which library it's in, so a chiseled shelf touching two libraries is in both.
public class LibraryControllerBlockEntity extends BlockEntity implements LibraryLockable, ITintHolder
{
    // Most members, and how far a linked block may be (each way): server config ([library] maxMembers, linkRange)
    public static int maxMembers() { return ServerConfig.libraryMaxMembers(); }
    public static int linkRange() { return ServerConfig.libraryLinkRange(); }
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

    // ---- Knowledge Points (LibraryCosts): the balance, and whether the library can still pay ----
    private static final String TAG_KNOWLEDGE = "knowledge";
    private static final String TAG_FUELED = "fueled";
    private long knowledge;
    // Synced: false when costs are on and there's no balance or fuel left ("out of knowledge")
    private boolean fueled = true;

    // Transactions: the balance changes inside the transfer's transaction; this remembers it before the first change,
    // so closing the transaction without commit (the transfer failed) puts it back. As MowLib's AmountStorage does.
    private final SnapshotJournal<Long> knowledgeJournal = new SnapshotJournal<>()
    {
        @Override
        protected Long createSnapshot()
        {
            return knowledge;
        }

        @Override
        protected void revertToSnapshot(Long snapshot)
        {
            knowledge = snapshot;
        }

        @Override
        protected void onRootCommit(Long originalState)
        {
            // Outer.this: the block entity this anonymous journal belongs to
            if (knowledge != originalState) LibraryControllerBlockEntity.this.setChanged();
        }
    };

    public long getKnowledge()
    {
        return knowledge;
    }

    public boolean isFueled()
    {
        return fueled;
    }

    // Pays for a transfer that has just happened inside `transaction`: false if the library can't (the caller then
    // undoes the transfer by not committing). Fuel going in is free. `at`: the block the transfer happens at.
    public boolean payFor(LibraryCosts.Operation operation, TransportType<?> type, Resource resource, int amount, BlockPos at, TransactionContext transaction)
    {
        if (operation == LibraryCosts.Operation.INSERT && LibraryCosts.isFuel(type, resource)) return true;
        long cost = LibraryCosts.cost(operation, type, amount, worldPosition, at);
        if (cost <= 0) return true;
        if (knowledge < cost) convert(cost - knowledge, transaction);
        if (knowledge < cost)
        {
            setFueled(false);
            return false;
        }
        knowledgeJournal.updateSnapshots(transaction);
        knowledge -= cost;
        return true;
    }

    // Turns fuel from the library's storage into at least `needed` points (fewer if it runs out), fuel by fuel
    private void convert(long needed, TransactionContext transaction)
    {
        long still = needed;
        for (LibraryCosts.Fuel fuel : LibraryCosts.fuels())
        {
            if (still <= 0) break;
            long units = (still + fuel.points() - 1) / fuel.points();
            int made = convertFuel(fuel, fuel.type(), (int) Math.min(Integer.MAX_VALUE / Math.max(1, fuel.amount()), units), transaction);
            if (made <= 0) continue;
            knowledgeJournal.updateSnapshots(transaction);
            knowledge += (long) made * fuel.points();
            still -= (long) made * fuel.points();
        }
    }

    // Up to `units` of one fuel taken from storage (whole units only: a part unit is put back). Returns the units.
    // A generic method so the type, its handler and its resources agree (the casts are safe: they all come from it).
    @SuppressWarnings("unchecked")
    private <R extends Resource> int convertFuel(LibraryCosts.Fuel fuel, TransportType<R> type, int units, TransactionContext transaction)
    {
        ResourceHandler<R> handler = typeHandler(type);
        if (handler == null || units <= 0) return 0;
        int wanted = units * fuel.amount();
        int taken = 0;
        R kind = null;
        while (taken < wanted)
        {
            ResourceStack<R> got = ResourceHandlerUtil.extractFirst(handler, resource -> fuel.matches(type, resource), wanted - taken, transaction);
            if (got == null || got.amount() <= 0) break;
            kind = got.resource();
            taken += got.amount();
        }
        int leftover = taken % fuel.amount();
        if (leftover > 0 && kind != null) insert(type, kind, leftover, transaction);
        return taken / fuel.amount();
    }

    // Whether there's a balance, or fuel to make one (or costs are off)
    private boolean hasFuel()
    {
        if (!LibraryCosts.enabled() || knowledge > 0) return true;
        for (LibraryCosts.Fuel fuel : LibraryCosts.fuels())
        {
            ResourceHandler<?> handler = typeHandler(fuel.type());
            if (handler == null) continue;
            for (int index = 0; index < handler.size(); index++)
            {
                if (fuel.matches(fuel.type(), handler.getResource(index)) && handler.getAmountAsLong(index) >= fuel.amount()) return true;
            }
        }
        return false;
    }

    private void setFueled(boolean now)
    {
        if (fueled == now) return;
        fueled = now;
        sync();
    }

    // ---- Looks: an end crystal's turning frames (dyeable, region 0) round one of the mod's books ----
    private static final String TAG_DISPLAY_BOOK = "display_book";
    private final CBTintData tints;
    // The book shown in the middle (EMPTY until one is picked: a random book the first time it's loaded on the server)
    private ItemStack displayBook = ItemStack.EMPTY;

    public LibraryControllerBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.LIBRARY_CONTROLLER.get(), pos, state);
        // Region 0 the base, 1 the crystal (LibraryControllerBlock.BASE / CRYSTAL)
        this.tints = new CBTintData(this, 2);
    }

    public ItemStack getDisplayBook()
    {
        return displayBook;
    }

    // Shows this book in the middle (its plain item: the look only)
    public void setDisplayBook(ItemStack book)
    {
        displayBook = new ItemStack(book.getItem());
        sync();
    }

    // Every Bibliomania book item (the blocks that are books), for the random pick
    public static List<Item> bookItems()
    {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof BlockItem blockItem && blockItem.getBlock() instanceof BookBlock)
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(BibliomaniaReferences.MODID))
                .toList();
    }

    // First loaded on the server with no book yet: pick one at random
    @Override
    public void onLoad()
    {
        super.onLoad();
        if (displayBook.isEmpty() && level instanceof ServerLevel serverLevel)
        {
            List<Item> books = bookItems();
            if (!books.isEmpty()) setDisplayBook(new ItemStack(books.get(serverLevel.getRandom().nextInt(books.size()))));
        }
    }

    // ---- Colour (ITintHolder): the frames and the base ----

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

    // ---- Members ----

    public boolean isMember(BlockPos pos)
    {
        return memberSet.contains(pos);
    }

    public List<BlockPos> members()
    {
        return members;
    }

    // The library's Bookcases (those loaded)
    // Highest priority first (LibraryPriority); equal ones in walk order (List#sort is stable)
    public List<BookcaseBlockEntity> bookcases()
    {
        List<BookcaseBlockEntity> found = new ArrayList<>();
        if (level == null) return found;
        for (BlockPos pos : members)
        {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BookcaseBlockEntity shelf) found.add(shelf);
        }
        found.sort(Comparator.comparingInt((BookcaseBlockEntity shelf) -> -shelf.getPriority()));
        return found;
    }

    // The library's Chiseled Bookshelves, in walk order (those loaded)
    public List<ChiseledBookShelfBlockEntity> chiseledShelves()
    {
        List<ChiseledBookShelfBlockEntity> found = new ArrayList<>();
        if (level == null) return found;
        for (BlockPos pos : members)
        {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf) found.add(shelf);
        }
        return found;
    }

    // A block the walk takes in: a library member, or a Chiseled Bookshelf
    private static boolean joins(@Nullable BlockEntity blockEntity)
    {
        return blockEntity instanceof LibraryMember || blockEntity instanceof ChiseledBookShelfBlockEntity;
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
        for (ResourceHandler<ItemResource> items : itemHandlers(true))
        {
            for (int index = 0; index < items.size(); index++)
            {
                if (items.getResource(index).equals(wanted)) total += items.getAmountAsLong(index);
            }
        }
        return total;
    }

    // How many items count as `item` in the whole library: exact, the same components; fuzzy, at any damage
    // (LibraryDeposit#matches)
    public long countMatching(ItemStack item, boolean exact)
    {
        long total = 0;
        for (ResourceHandler<ItemResource> items : itemHandlers(true))
        {
            for (int index = 0; index < items.size(); index++)
            {
                if (LibraryDeposit.matches(items.getResource(index), item, exact)) total += items.getAmountAsLong(index);
            }
        }
        return total;
    }

    // Takes up to `amount` of the first stored item that counts as `item` (exact or fuzzy). One kind only, so it's
    // one stack: with fuzzy matching, a worn tool comes out as it is. EMPTY if there's none.
    public ItemStack extractMatching(ItemStack item, int amount, boolean exact, BlockPos at)
    {
        ResourceHandler<ItemResource> items = typeHandler(TransportTypes.ITEMS.get());
        if (items == null || amount <= 0) return ItemStack.EMPTY;
        try (Transaction transaction = Transaction.openRoot())
        {
            ResourceStack<ItemResource> got = ResourceHandlerUtil.extractFirst(items, resource -> LibraryDeposit.matches(resource, item, exact), amount, transaction);
            if (got == null || got.amount() <= 0) return ItemStack.EMPTY;
            // Paid for (LibraryCosts; `at`: the lectern): if the library can't, nothing comes out
            if (!payFor(LibraryCosts.Operation.EXTRACT, TransportTypes.ITEMS.get(), got.resource(), got.amount(), at, transaction)) return ItemStack.EMPTY;
            transaction.commit();
            return got.resource().toStack(got.amount());
        }
    }

    // Takes up to `amount` of `item` from the whole library (bookcase by bookcase, first book first)
    public int extractItem(ItemStack item, int amount)
    {
        ItemResource wanted = ItemResource.of(item);
        int taken = 0;
        for (ResourceHandler<ItemResource> items : itemHandlers(true))
        {
            if (taken >= amount) break;
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

    // Every book of `type` in the library as one handler (bookcase by bookcase, first book first), or null if there's
    // none. A Reading Lectern with a TYPE drawer open uses this with the books' own actions.
    // For items, the Chiseled Bookshelves come after the item books (so taking out finds books on them too; putting in
    // should go through insertItems, which puts the right books on them first).
    @SuppressWarnings("unchecked")
    public <R extends Resource> @Nullable ResourceHandler<R> typeHandler(TransportType<R> type)
    {
        List<ResourceHandler<R>> parts = new ArrayList<>();
        if (type == TransportTypes.ITEMS.get())
        {
            // The cast is safe: R is ItemResource when the type is ITEMS
            for (ResourceHandler<ItemResource> part : itemHandlers(true)) parts.add((ResourceHandler<R>) part);
        }
        else
        {
            for (BookcaseBlockEntity shelf : bookcases())
            {
                ResourceHandler<R> part = shelf.handler(type);
                if (part != null) parts.add(part);
            }
        }
        return parts.isEmpty() ? null : new CombinedResourceHandler<>(parts);
    }

    // ---- Priority (LibraryPriority): which shelves are filled first ----

    // One shelf's storage of a type, with its priority. chiseled: a Chiseled Bookshelf (only some books go onto those).
    private record Store<R extends Resource>(int priority, ResourceHandler<R> handler, boolean chiseled) {}

    // Every shelf's item storage (with withChiseled, the Chiseled Bookshelves' six slots too), highest priority first,
    // equal ones in walk order. VanillaContainerWrapper (NeoForge): a vanilla Container (the chiseled shelf's block
    // entity) as a ResourceHandler, one index per slot; it only accepts what the container does (books, one a slot).
    private List<Store<ItemResource>> itemStores(boolean withChiseled)
    {
        List<Store<ItemResource>> stores = new ArrayList<>();
        if (level == null) return stores;
        for (BlockPos pos : members)
        {
            if (!level.isLoaded(pos)) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BookcaseBlockEntity shelf)
            {
                ResourceHandler<ItemResource> part = shelf.handler(TransportTypes.ITEMS.get());
                if (part != null) stores.add(new Store<>(shelf.getPriority(), part, false));
            }
            else if (withChiseled && blockEntity instanceof ChiseledBookShelfBlockEntity shelf)
            {
                stores.add(new Store<>(LibraryPriority.of(shelf), VanillaContainerWrapper.of(shelf), true));
            }
        }
        // List#sort is stable: equal priorities keep the walk order
        stores.sort(Comparator.comparingInt((Store<ItemResource> store) -> -store.priority()));
        return stores;
    }

    private List<ResourceHandler<ItemResource>> itemHandlers(boolean withChiseled)
    {
        List<ResourceHandler<ItemResource>> handlers = new ArrayList<>();
        for (Store<ItemResource> store : itemStores(withChiseled)) handlers.add(store.handler());
        return handlers;
    }

    // Puts up to `amount` into stores already in priority order: each priority in turn (all shelves of the same
    // priority together, onto what they already hold first: insertStacking), until it's all in. Returns how many.
    private static <R extends Resource> int insertByPriority(List<Store<R>> stores, R resource, int amount, TransactionContext transaction)
    {
        int inserted = 0;
        int index = 0;
        while (index < stores.size() && inserted < amount)
        {
            int priority = stores.get(index).priority();
            List<ResourceHandler<R>> band = new ArrayList<>();
            while (index < stores.size() && stores.get(index).priority() == priority) band.add(stores.get(index++).handler());
            inserted += ResourceHandlerUtil.insertStacking(new CombinedResourceHandler<>(band), resource, amount - inserted, transaction);
        }
        return inserted;
    }

    // Puts up to `amount` of `resource` into the library, within `transaction`: highest priority shelves first.
    // Enchanted and written books (#bibliomania:library_shelf_books) may go onto Chiseled Bookshelves (priority 100
    // unless changed, so before Bookcases left at 0); nothing else ever does (a plain book goes to the item books like
    // any stackable item). Returns how many went in.
    public int insertItems(ItemResource resource, int amount, TransactionContext transaction)
    {
        if (resource.isEmpty() || amount <= 0) return 0;
        boolean shelfBook = resource.toStack(1).is(BibliomaniaTags.LIBRARY_SHELF_BOOKS);
        return insertByPriority(itemStores(shelfBook), resource, amount, transaction);
    }

    // Any type into the library, highest priority Bookcases first (items: insertItems). The casts are safe: R is
    // ItemResource when the type is ITEMS.
    @SuppressWarnings("unchecked")
    public <R extends Resource> int insert(TransportType<R> type, R resource, int amount, TransactionContext transaction)
    {
        if (type == TransportTypes.ITEMS.get()) return insertItems((ItemResource) resource, amount, transaction);
        if (resource.isEmpty() || amount <= 0) return 0;
        List<Store<R>> stores = new ArrayList<>();
        for (BookcaseBlockEntity shelf : bookcases())
        {
            ResourceHandler<R> part = shelf.handler(type);
            if (part != null) stores.add(new Store<>(shelf.getPriority(), part, false));
        }
        return insertByPriority(stores, resource, amount, transaction);
    }

    // How much of `type` the library holds and has room for: {amount, capacity}. With a filter (a fluid), only that
    // resource, and the room is its books plus every empty one (any of them can take it).
    public long[] typeTotals(TransportType<?> type, @Nullable Resource filter)
    {
        ResourceHandler<?> handler = typeHandler(type);
        long amount = 0, capacity = 0;
        if (handler != null)
        {
            for (int index = 0; index < handler.size(); index++)
            {
                Resource resource = handler.getResource(index);
                if (filter != null && !resource.isEmpty() && !resource.equals(filter)) continue;
                amount += handler.getAmountAsLong(index);
                capacity += capacityOf(handler, index, filter != null ? filter : resource);
            }
        }
        return new long[]{amount, capacity};
    }

    // getCapacityAsLong wants the handler's own resource type: the cast is safe, every resource here came from it
    @SuppressWarnings("unchecked")
    private static <R extends Resource> long capacityOf(ResourceHandler<R> handler, int index, Resource resource)
    {
        return handler.getCapacityAsLong(index, (R) resource);
    }

    // The library's storage books of `type`, best first for `filter` (a fluid): books holding it, then empty ones,
    // then the rest
    public List<BookPlace> typeBooks(TransportType<?> type, @Nullable Resource filter)
    {
        List<BookPlace> holding = new ArrayList<>(), empty = new ArrayList<>(), others = new ArrayList<>();
        for (BookcaseBlockEntity shelf : bookcases())
        {
            for (int slot = 0; slot < BookcaseBlockEntity.SLOTS; slot++)
            {
                ItemStack book = shelf.getBook(slot);
                if (!(book.getItem() instanceof StorageBookItem item) || item.book().type() != type) continue;
                BookStorage storage = BookStorage.open(book);
                Resource resource = storage == null ? null : storage.storage().getResource(0);
                BookPlace place = new BookPlace(shelf, slot);
                if (resource == null || resource.isEmpty()) empty.add(place);
                else if (filter == null || resource.equals(filter)) holding.add(place);
                else others.add(place);
            }
        }
        holding.addAll(empty);
        holding.addAll(others);
        return holding;
    }

    // A storage book of `type`, filled to show the library's fullness of it (amount / room, as a share of one book):
    // what a Reading Lectern with a type drawer open draws. EMPTY if the library has no book of the type.
    public ItemStack typeDisplayBook(TransportType<?> type, @Nullable Resource filter, long[] totals)
    {
        List<BookPlace> books = typeBooks(type, filter);
        if (books.isEmpty()) return ItemStack.EMPTY;
        ItemStack display = new ItemStack(books.getFirst().shelf().getBook(books.getFirst().slot()).getItem());
        BookStorage storage = BookStorage.open(display);
        ResourceHandler<?> handler = typeHandler(type);
        if (storage == null || handler == null || totals[1] <= 0 || totals[0] <= 0) return display;
        Resource resource = null;
        for (int index = 0; index < handler.size() && resource == null; index++)
        {
            Resource each = handler.getResource(index);
            if (!each.isEmpty() && (filter == null || each.equals(filter))) resource = each;
        }
        if (resource == null) return display;
        int amount = (int) Math.max(1, Math.round((double) totals[0] / totals[1] * storage.capacity()));
        fill(storage, type, resource, amount);
        return display;
    }

    // Puts `amount` of `resource` into a book's storage (which writes it onto the book). The cast is safe: the
    // resource came from a handler of this type.
    @SuppressWarnings("unchecked")
    private static <R extends Resource> void fill(BookStorage storage, TransportType<R> type, Resource resource, int amount)
    {
        try (Transaction transaction = Transaction.openRoot())
        {
            storage.handler(type).insert((R) resource, amount, transaction);
            transaction.commit();
        }
    }

    // Whether a card catalog drawer's item or book is in the library
    public boolean has(DrawerEntry entry)
    {
        return switch (entry.kind())
        {
            case ITEM -> countMatching(entry.icon(), false) > 0;
            case BOOK, TRADE -> entry.book().map(this::findBook).isPresent();
            case TYPE -> entry.transportType() != null && typeHandler(entry.transportType()) != null;
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
            // Sealed lecterns keep showing what they show
            if (lectern.isLocked()) continue;
            if (entry.kind() == DrawerEntry.Kind.ITEM) lectern.selectItem(level, entry.icon());
            else if (entry.kind() == DrawerEntry.Kind.TYPE) lectern.selectType(level, entry);
            else if (place != null) lectern.showBook(level, place.shelf(), place.slot());
        }
    }

    // A drawer was closed: the lecterns go back to their pages
    public void clearSelection(ServerLevel level)
    {
        for (ReadingLecternBlockEntity lectern : lecterns())
        {
            if (!lectern.isLocked()) lectern.clearSelection(level);
        }
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
        while (!queue.isEmpty() && found.size() < maxMembers())
        {
            BlockPos pos = queue.poll();
            if (pos.equals(worldPosition) || found.contains(pos) || removed.contains(pos) || !level.isLoaded(pos)) continue;
            BlockEntity joining = level.getBlockEntity(pos);
            if (!joins(joining)) continue;
            if (joining instanceof LibraryMember member && inOtherLibrary(level, member, pos)) continue;
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
            if (blockEntity instanceof BookcaseBlockEntity || blockEntity instanceof ChiseledBookShelfBlockEntity) bookcases++;
            if (blockEntity instanceof ReadingLecternBlockEntity) lecterns++;
        }
        members = List.copyOf(found);
        memberSet = Set.copyOf(found);
        setFueled(hasFuel());
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
    // other library block within linkRange() (server config) is linked
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
        else if (!joins(level.getBlockEntity(target))) return LinkChange.NOT_A_MEMBER;
        else if (Math.abs(target.getX() - worldPosition.getX()) > linkRange() || Math.abs(target.getY() - worldPosition.getY()) > linkRange()
                || Math.abs(target.getZ() - worldPosition.getZ()) > linkRange()) return LinkChange.TOO_FAR;
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
        output.store(TAG_DISPLAY_BOOK, ItemStack.OPTIONAL_CODEC, displayBook);
        output.putLong(TAG_KNOWLEDGE, knowledge);
        output.putBoolean(TAG_FUELED, fueled);
        tints.save(output);
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
        displayBook = input.read(TAG_DISPLAY_BOOK, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        knowledge = input.getLongOr(TAG_KNOWLEDGE, 0L);
        fueled = input.getBooleanOr(TAG_FUELED, true);
        // CBTintData#load re-meshes the chunk on clients (the base is tinted): only when the colour really changed
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
