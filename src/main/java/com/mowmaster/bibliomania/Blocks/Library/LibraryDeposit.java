package com.mowmaster.bibliomania.Blocks.Library;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import com.mowmaster.bibliomania.Blocks.Bookcase.ShelvedBook;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import com.mowmaster.mowlib.transport.AmountResource;
import com.mowmaster.mowlib.transport.EnergyTransportType;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

// Moving things in and out of a library's storage books from outside the library's own blocks:
// - in: the Storage Deposit (by hand, and hoppers/pipes through InsertOnly), and what the Storage Dispenser's
//   dispensing turns its item into
// - out: the Patterned Storage Requestor and the Storage Dispenser taking what they're set to (take)
// Fluid buckets are special both ways: going in, the fluid goes to the fluid books and the empty bucket to the item
// books; coming out, a bucket the library doesn't have as an item is filled from an empty bucket and the fluid books.
// Everything here runs inside a transaction the caller opened (NeoForge's transfer API): nothing is really changed
// until that's committed, and closing it without commit() undoes it all.
public final class LibraryDeposit
{
    private LibraryDeposit() {}

    // ---- In ----

    // Puts as much of `stack` as fits into the library (enchanted and written books onto its Chiseled Bookshelves
    // first, everything else into the item books: LibraryControllerBlockEntity#insertItems). Returns how many went in.
    // The stack itself isn't changed. It's paid for (LibraryCosts; `at`: where it goes in): if the library can't pay,
    // nothing goes in.
    public static int insertItems(LibraryControllerBlockEntity library, ItemStack stack, BlockPos at, TransactionContext transaction)
    {
        if (stack.isEmpty()) return 0;
        return paidInsert(library, TransportTypes.ITEMS.get(), ItemResource.of(stack), stack.getCount(), at, transaction);
    }

    // Puts up to `amount` in (LibraryControllerBlockEntity#insert) and pays for what went in, in a nested transaction
    // that's only kept if both worked. Returns how many went in.
    public static <R extends Resource> int paidInsert(LibraryControllerBlockEntity library, TransportType<R> type, R resource, int amount,
                                                      BlockPos at, TransactionContext transaction)
    {
        try (Transaction paid = Transaction.open(transaction))
        {
            int inserted = library.insert(type, resource, amount, paid);
            if (inserted <= 0 || !library.payFor(LibraryCosts.Operation.INSERT, type, resource, inserted, at, paid)) return 0;
            paid.commit();
            return inserted;
        }
    }

    // Takes up to `amount` out of `from` (one of the library's handlers) and pays for it, the same way
    public static <R extends Resource> int paidExtract(LibraryControllerBlockEntity library, TransportType<R> type, ResourceHandler<R> from,
                                                       int index, R resource, int amount, BlockPos at, TransactionContext transaction)
    {
        try (Transaction paid = Transaction.open(transaction))
        {
            int taken = index < 0 ? from.extract(resource, amount, paid) : from.extract(index, resource, amount, paid);
            if (taken <= 0 || !library.payFor(LibraryCosts.Operation.EXTRACT, type, resource, taken, at, paid)) return 0;
            paid.commit();
            return taken;
        }
    }

    // ---- Emptying a held container into the library ----

    // The item in the player's hand, if it holds things, is emptied into the library (as much as fits; the rest stays
    // in it): Bibliomania's storage books (an item book's pages, a Book of Fluids' fluid, experience, energy) and
    // anything with an item handler (bundles, shulker boxes...). Returns how much moved, or -1 if the item doesn't hold
    // anything (then it's stored as an item like any other).
    // ItemAccess.forPlayerInteraction: the held stack as the item's own handlers see it; their changes (a book with
    // fewer pages filled, a lighter bundle) are written back into the hand.
    public static int emptyHeld(LibraryControllerBlockEntity library, Player player, InteractionHand hand, BlockPos at)
    {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getCount() != 1) return -1;
        ItemAccess access = ItemAccess.forPlayerInteraction(player, hand);
        if (held.getItem() instanceof ShelvedBook shelved)
        {
            int moved = -1;
            for (TransportType<?> type : TransportTypes.REGISTRY)
            {
                int each = emptyBook(library, shelved, type, access, at);
                if (each >= 0) moved = Math.max(moved, 0) + each;
            }
            if (moved >= 0) return moved;
        }
        ResourceHandler<ItemResource> items = access.getCapability(Capabilities.Item.ITEM);
        return items == null ? -1 : emptyInto(library, TransportTypes.ITEMS.get(), items, at);
    }

    // One type of a book's contents (-1: the book holds nothing of that type). A generic method so the type and the
    // book's handler agree on R (a TransportType<?> from the registry can't be used for that directly).
    private static <R extends Resource> int emptyBook(LibraryControllerBlockEntity library, ShelvedBook book, TransportType<R> type, ItemAccess access, BlockPos at)
    {
        ResourceHandler<R> from = book.shelfHandler(type, access);
        return from == null ? -1 : emptyInto(library, type, from, at);
    }

    // Moves everything it can from `from` into the library, index by index (each one all-or-what-fits, in its own
    // nested transaction). Returns how much moved.
    private static <R extends Resource> int emptyInto(LibraryControllerBlockEntity library, TransportType<R> type, ResourceHandler<R> from, BlockPos at)
    {
        int moved = 0;
        try (Transaction transaction = Transaction.openRoot())
        {
            for (int index = 0; index < from.size(); index++)
            {
                R resource = from.getResource(index);
                int amount = from.getAmountAsInt(index);
                if (resource.isEmpty() || amount <= 0) continue;
                try (Transaction each = Transaction.open(transaction))
                {
                    int inserted = paidInsert(library, type, resource, amount, at, each);
                    if (inserted <= 0 || from.extract(index, resource, inserted, each) < inserted) continue;
                    each.commit();
                    moved += inserted;
                }
            }
            transaction.commit();
        }
        return moved;
    }

    // A bucket of fluid (water, lava, a modded fluid's bucket); not a bucket of fish or axolotl (MobBucketItem extends
    // BucketItem: its mob would be lost)
    public static boolean isFluidBucket(ItemStack stack)
    {
        return stack.getItem() instanceof BucketItem && !(stack.getItem() instanceof MobBucketItem)
                && !FluidUtil.getFirstStackContained(stack).isEmpty();
    }

    // Empties the fluid container at `access` (a bucket in a player's hand...) into the library's fluid books: all of
    // its fluid or none (a bucket can't be part emptied). The container's own handler turns it into its empty form (an
    // empty bucket) where it is. Returns whether it was emptied.
    public static boolean emptyFluid(LibraryControllerBlockEntity library, ItemAccess access, BlockPos at, TransactionContext transaction)
    {
        ResourceHandler<FluidResource> container = access.getCapability(Capabilities.Fluid.ITEM);
        ResourceHandler<FluidResource> fluids = library.typeHandler(TransportTypes.FLUIDS.get());
        if (container == null || fluids == null) return false;
        // A nested transaction: its changes only count if it's committed, and then only if the outer one is too
        try (Transaction inner = Transaction.open(transaction))
        {
            boolean emptied = false;
            for (int index = 0; index < container.size(); index++)
            {
                FluidResource fluid = container.getResource(index);
                int amount = container.getAmountAsInt(index);
                if (fluid.isEmpty() || amount <= 0) continue;
                if (paidInsert(library, TransportTypes.FLUIDS.get(), fluid, amount, at, inner) < amount) return false;
                if (container.extract(index, fluid, amount, inner) < amount) return false;
                emptied = true;
            }
            if (emptied) inner.commit();
            return emptied;
        }
    }

    // Stores `stack` in the library's item books as it is (a water bucket stays a water bucket), committed. Returns
    // what didn't fit (EMPTY if it all did). The stack itself isn't changed.
    public static ItemStack storeItems(LibraryControllerBlockEntity library, ItemStack stack, BlockPos at)
    {
        ItemStack rest = stack.copy();
        if (rest.isEmpty()) return ItemStack.EMPTY;
        try (Transaction transaction = Transaction.openRoot())
        {
            int inserted = insertItems(library, rest, at, transaction);
            transaction.commit();
            rest.shrink(inserted);
        }
        return rest;
    }

    // Stores `stack` in the library (its own transactions, committed): fluid buckets are emptied into the fluid books
    // one at a time and the empty buckets stored as items; everything else goes to the item books. Returns what didn't
    // fit (EMPTY if it all did). The stack itself isn't changed.
    public static ItemStack store(LibraryControllerBlockEntity library, ItemStack stack, BlockPos at)
    {
        ItemStack rest = stack.copy();
        while (!rest.isEmpty() && isFluidBucket(rest))
        {
            // The bucket in a one-slot handler of its own, so its handler can swap it for the empty bucket there
            ItemStacksResourceHandler scratch = new ItemStacksResourceHandler(1);
            scratch.set(0, ItemResource.of(rest), 1);
            try (Transaction transaction = Transaction.openRoot())
            {
                if (!emptyFluid(library, ItemAccess.forHandlerIndexStrict(scratch, 0), at, transaction)) break;
                ItemStack emptied = ItemUtil.getStack(scratch, 0);
                if (insertItems(library, emptied, at, transaction) < emptied.getCount()) break;
                transaction.commit();
            }
            rest.shrink(1);
        }
        if (rest.isEmpty()) return ItemStack.EMPTY;
        try (Transaction transaction = Transaction.openRoot())
        {
            int inserted = insertItems(library, rest, at, transaction);
            transaction.commit();
            rest.shrink(inserted);
        }
        return rest;
    }

    // ---- Out ----

    // Whether a stored item counts as `wanted`. Exact: the same item with the same components. Fuzzy: the same apart
    // from damage (worn shears are still shears; enchantments, names and the rest still have to match).
    public static boolean matches(ItemResource resource, ItemStack wanted, boolean exact)
    {
        if (!resource.is(wanted.getItem())) return false;
        ItemStack stored = resource.toStack(1);
        if (exact) return ItemStack.isSameItemSameComponents(stored, wanted);
        ItemStack want = wanted.copyWithCount(1);
        stored.remove(DataComponents.DAMAGE);
        want.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(stored, want);
    }

    // Takes up to `count` of `wanted` from the library (matches: exact or fuzzy). What it got, as the real stacks
    // (fuzzy: possibly several, each worn differently). A fluid bucket the library doesn't have enough of as items is
    // made from an empty bucket (item books) and a bucket's worth of its fluid (fluid books), one at a time.
    public static List<ItemStack> take(LibraryControllerBlockEntity library, ItemStack wanted, int count, boolean exact, BlockPos at, TransactionContext transaction)
    {
        return take(library, wanted, count, exact, true, at, transaction);
    }

    // The same; fillBuckets false: only bucket items already stored count (the Storage Dispenser). Paid for
    // (LibraryCosts; `at`: where it comes out): if the library can't pay, nothing is taken (an empty list).
    public static List<ItemStack> take(LibraryControllerBlockEntity library, ItemStack wanted, int count, boolean exact, boolean fillBuckets,
                                       BlockPos at, TransactionContext transaction)
    {
        try (Transaction paid = Transaction.open(transaction))
        {
            List<ItemStack> got = takeUnpaid(library, wanted, count, exact, fillBuckets, paid);
            int taken = total(got);
            if (taken <= 0 || !library.payFor(LibraryCosts.Operation.EXTRACT, TransportTypes.ITEMS.get(), ItemResource.of(wanted), taken, at, paid)) return List.of();
            paid.commit();
            return got;
        }
    }

    private static List<ItemStack> takeUnpaid(LibraryControllerBlockEntity library, ItemStack wanted, int count, boolean exact, boolean fillBuckets, TransactionContext transaction)
    {
        List<ItemStack> got = new ArrayList<>();
        ResourceHandler<ItemResource> items = library.typeHandler(TransportTypes.ITEMS.get());
        if (items == null || wanted.isEmpty() || count <= 0) return got;
        int taken = 0;
        while (taken < count)
        {
            // extractFirst: takes from the first stored resource the test accepts (a lambda: resource -> true/false)
            ResourceStack<ItemResource> each = ResourceHandlerUtil.extractFirst(items, resource -> matches(resource, wanted, exact), count - taken, transaction);
            if (each == null || each.amount() <= 0) break;
            got.add(each.resource().toStack(each.amount()));
            taken += each.amount();
        }
        if (taken >= count || !fillBuckets || !isFluidBucket(wanted)) return got;
        ResourceHandler<FluidResource> fluids = library.typeHandler(TransportTypes.FLUIDS.get());
        if (fluids == null) return got;
        FluidStack fluid = FluidUtil.getFirstStackContained(wanted);
        FluidResource resource = FluidResource.of(fluid);
        while (taken < count)
        {
            try (Transaction fill = Transaction.open(transaction))
            {
                if (items.extract(ItemResource.of(Items.BUCKET), 1, fill) < 1) break;
                if (fluids.extract(resource, fluid.getAmount(), fill) < fluid.getAmount()) break;
                fill.commit();
            }
            got.add(wanted.copyWithCount(1));
            taken++;
        }
        return got;
    }

    // How many items in all
    public static int total(List<ItemStack> stacks)
    {
        int total = 0;
        for (ItemStack stack : stacks) total += stack.getCount();
        return total;
    }

    // ---- Hoppers and pipes ----

    // A handler that puts things into the library's books of one type and never gives anything out: what a Storage
    // Deposit offers hoppers and pipes. It asks for the library on every call (`library` may give null: not in one),
    // so a pipe can keep it for as long as it likes. Items go in the library's way (insertItems: enchanted and written
    // books onto Chiseled Bookshelves first).
    public static class InsertOnly<R extends Resource> implements ResourceHandler<R>
    {
        private final Supplier<@Nullable LibraryControllerBlockEntity> library;
        private final TransportType<R> type;
        // The library's handler for the type (null: no library, or no books of the type)
        private final Supplier<@Nullable ResourceHandler<R>> target;

        // Where the transfers happen (for the distance cost)
        protected final BlockPos at;

        public InsertOnly(Supplier<@Nullable LibraryControllerBlockEntity> library, TransportType<R> type, BlockPos at)
        {
            this.library = library;
            this.type = type;
            this.at = at.immutable();
            this.target = () -> {
                LibraryControllerBlockEntity found = library.get();
                return found == null ? null : found.typeHandler(type);
            };
        }

        // The library's handler if `index` is one of its slots, else null. Callers should only ask for indices below
        // size() (0 with no library), but not every mod does (MowLib's EnergyView always asks for slot 0), so a slot
        // that isn't there reads as empty and takes nothing instead of crashing.
        protected @Nullable ResourceHandler<R> handler(int index)
        {
            ResourceHandler<R> handler = target.get();
            return handler == null || index < 0 || index >= handler.size() ? null : handler;
        }

        @Override
        public int size()
        {
            ResourceHandler<R> handler = target.get();
            return handler == null ? 0 : handler.size();
        }

        // getResource has no "nothing" to give for a slot that isn't there (R could be anything), so that one still
        // throws; the rest read as empty
        @Override
        public R getResource(int index)
        {
            ResourceHandler<R> handler = handler(index);
            if (handler == null) throw new IndexOutOfBoundsException("No library storage slot " + index);
            return handler.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index)
        {
            ResourceHandler<R> handler = handler(index);
            return handler == null ? 0 : handler.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, R resource)
        {
            ResourceHandler<R> handler = handler(index);
            return handler == null ? 0 : handler.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, R resource)
        {
            ResourceHandler<R> handler = handler(index);
            return handler != null && handler.isValid(index, resource);
        }

        @Override
        public int insert(int index, R resource, int amount, TransactionContext transaction)
        {
            LibraryControllerBlockEntity found = library.get();
            ResourceHandler<R> handler = handler(index);
            if (found == null || handler == null) return 0;
            try (Transaction paid = Transaction.open(transaction))
            {
                int inserted = handler.insert(index, resource, amount, paid);
                if (inserted <= 0 || !found.payFor(LibraryCosts.Operation.INSERT, type, resource, inserted, at, paid)) return 0;
                paid.commit();
                return inserted;
            }
        }

        // Without an index: the library's own way in (highest priority shelves first), paid for
        @Override
        public int insert(R resource, int amount, TransactionContext transaction)
        {
            LibraryControllerBlockEntity found = library.get();
            return found == null ? 0 : paidInsert(found, type, resource, amount, at, transaction);
        }

        protected @Nullable LibraryControllerBlockEntity library()
        {
            return library.get();
        }

        protected TransportType<R> type()
        {
            return type;
        }

        @Override
        public int extract(int index, R resource, int amount, TransactionContext transaction)
        {
            return 0;
        }

        @Override
        public int extract(R resource, int amount, TransactionContext transaction)
        {
            return 0;
        }
    }

    // The same, but taking out works too: what a Storage Access Point offers hoppers, pipes and copper golems
    // (in: the library's way, highest priority first; out: from its books of the type, first found first)
    public static class FullAccess<R extends Resource> extends InsertOnly<R>
    {
        private final Supplier<@Nullable ResourceHandler<R>> books;

        public FullAccess(Supplier<@Nullable LibraryControllerBlockEntity> library, TransportType<R> type, BlockPos at)
        {
            super(library, type, at);
            this.books = () -> {
                LibraryControllerBlockEntity found = library.get();
                return found == null ? null : found.typeHandler(type);
            };
        }

        @Override
        public int extract(int index, R resource, int amount, TransactionContext transaction)
        {
            LibraryControllerBlockEntity found = library();
            ResourceHandler<R> handler = handler(index);
            return found == null || handler == null ? 0 : paidExtract(found, type(), handler, index, resource, amount, at, transaction);
        }

        @Override
        public int extract(R resource, int amount, TransactionContext transaction)
        {
            LibraryControllerBlockEntity found = library();
            ResourceHandler<R> handler = books.get();
            return found == null || handler == null ? 0 : paidExtract(found, type(), handler, -1, resource, amount, at, transaction);
        }
    }

    // Energy as one pool: what the Deposit and Access Point offer as a NeoForge energy handler. MowLib's EnergyView
    // (which makes a ResourceHandler of energy into an EnergyHandler) only looks at slot 0, which would be the first
    // energy book alone (or nothing, with none). This shows it one slot holding all the library's energy; putting in
    // and taking out go the library's way (paid, highest priority first) through the wrapped handler.
    // Can go once MowLib's EnergyView adds up every slot itself (see mowlib-from-bibliomania).
    public record EnergyPool(ResourceHandler<AmountResource> library) implements ResourceHandler<AmountResource>
    {
        @Override
        public int size()
        {
            return 1;
        }

        @Override
        public AmountResource getResource(int index)
        {
            return index == 0 && getAmountAsLong(0) > 0 ? EnergyTransportType.ENERGY : AmountResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index)
        {
            if (index != 0) return 0;
            long total = 0;
            for (int slot = 0; slot < library.size(); slot++) total += library.getAmountAsLong(slot);
            return total;
        }

        @Override
        public long getCapacityAsLong(int index, AmountResource resource)
        {
            if (index != 0) return 0;
            long total = 0;
            for (int slot = 0; slot < library.size(); slot++) total += library.getCapacityAsLong(slot, EnergyTransportType.ENERGY);
            return total;
        }

        @Override
        public boolean isValid(int index, AmountResource resource)
        {
            return index == 0 && EnergyTransportType.ENERGY.equals(resource);
        }

        @Override
        public int insert(int index, AmountResource resource, int amount, TransactionContext transaction)
        {
            return index != 0 || amount <= 0 ? 0 : library.insert(resource, amount, transaction);
        }

        @Override
        public int extract(int index, AmountResource resource, int amount, TransactionContext transaction)
        {
            return index != 0 || amount <= 0 ? 0 : library.extract(resource, amount, transaction);
        }
    }
}
