package com.mowmaster.bibliomania.Blocks.Library;

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
    // The stack itself isn't changed.
    public static int insertItems(LibraryControllerBlockEntity library, ItemStack stack, TransactionContext transaction)
    {
        if (stack.isEmpty()) return 0;
        return library.insertItems(ItemResource.of(stack), stack.getCount(), transaction);
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
    public static boolean emptyFluid(LibraryControllerBlockEntity library, ItemAccess access, TransactionContext transaction)
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
                if (ResourceHandlerUtil.insertStacking(fluids, fluid, amount, inner) < amount) return false;
                if (container.extract(index, fluid, amount, inner) < amount) return false;
                emptied = true;
            }
            if (emptied) inner.commit();
            return emptied;
        }
    }

    // Stores `stack` in the library (its own transactions, committed): fluid buckets are emptied into the fluid books
    // one at a time and the empty buckets stored as items; everything else goes to the item books. Returns what didn't
    // fit (EMPTY if it all did). The stack itself isn't changed.
    public static ItemStack store(LibraryControllerBlockEntity library, ItemStack stack)
    {
        ItemStack rest = stack.copy();
        while (!rest.isEmpty() && isFluidBucket(rest))
        {
            // The bucket in a one-slot handler of its own, so its handler can swap it for the empty bucket there
            ItemStacksResourceHandler scratch = new ItemStacksResourceHandler(1);
            scratch.set(0, ItemResource.of(rest), 1);
            try (Transaction transaction = Transaction.openRoot())
            {
                if (!emptyFluid(library, ItemAccess.forHandlerIndexStrict(scratch, 0), transaction)) break;
                ItemStack emptied = ItemUtil.getStack(scratch, 0);
                if (insertItems(library, emptied, transaction) < emptied.getCount()) break;
                transaction.commit();
            }
            rest.shrink(1);
        }
        if (rest.isEmpty()) return ItemStack.EMPTY;
        try (Transaction transaction = Transaction.openRoot())
        {
            int inserted = insertItems(library, rest, transaction);
            transaction.commit();
            rest.shrink(inserted);
        }
        return rest;
    }

    // ---- Out ----

    // Takes up to `count` of `wanted` (its exact components) from the library. A fluid bucket the library doesn't
    // have enough of as items is made from an empty bucket (item books) and a bucket's worth of its fluid (fluid
    // books), one at a time. Returns how many it got.
    public static int take(LibraryControllerBlockEntity library, ItemStack wanted, int count, TransactionContext transaction)
    {
        ResourceHandler<ItemResource> items = library.typeHandler(TransportTypes.ITEMS.get());
        if (items == null || wanted.isEmpty() || count <= 0) return 0;
        int taken = items.extract(ItemResource.of(wanted), count, transaction);
        if (taken >= count || !isFluidBucket(wanted)) return taken;
        ResourceHandler<FluidResource> fluids = library.typeHandler(TransportTypes.FLUIDS.get());
        if (fluids == null) return taken;
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
            taken++;
        }
        return taken;
    }

    // Takes one of `wanted`'s item, whatever its damage (a worn pair of shears is still shears). Returns what it got,
    // or EMPTY.
    public static ItemStack takeAnyDamage(LibraryControllerBlockEntity library, ItemStack wanted, TransactionContext transaction)
    {
        ResourceHandler<ItemResource> items = library.typeHandler(TransportTypes.ITEMS.get());
        if (items == null || wanted.isEmpty()) return ItemStack.EMPTY;
        // extractFirst: the first resource the test accepts (a lambda: resource -> true or false)
        ResourceStack<ItemResource> got = ResourceHandlerUtil.extractFirst(items, resource -> resource.is(wanted.getItem()), 1, transaction);
        return got == null ? ItemStack.EMPTY : got.resource().toStack(got.amount());
    }

    // ---- Hoppers and pipes ----

    // A handler that puts things into the library's books of one type and never gives anything out: what a Storage
    // Deposit offers hoppers and pipes. It asks for the library on every call (`library` may give null: not in one),
    // so a pipe can keep it for as long as it likes. Items go in the library's way (insertItems: enchanted and written
    // books onto Chiseled Bookshelves first).
    public static final class InsertOnly<R extends Resource> implements ResourceHandler<R>
    {
        private final Supplier<@Nullable LibraryControllerBlockEntity> library;
        private final TransportType<R> type;
        // The library's handler for the type (null: no library, or no books of the type)
        private final Supplier<@Nullable ResourceHandler<R>> target;

        public InsertOnly(Supplier<@Nullable LibraryControllerBlockEntity> library, TransportType<R> type)
        {
            this.library = library;
            this.type = type;
            this.target = () -> {
                LibraryControllerBlockEntity found = library.get();
                return found == null ? null : found.typeHandler(type);
            };
        }

        // Callers only ask for indices below size(), and it's 0 with no library, so the others always have a handler
        private ResourceHandler<R> handler()
        {
            ResourceHandler<R> handler = target.get();
            if (handler == null) throw new IndexOutOfBoundsException("No library storage");
            return handler;
        }

        @Override
        public int size()
        {
            ResourceHandler<R> handler = target.get();
            return handler == null ? 0 : handler.size();
        }

        @Override public R getResource(int index) { return handler().getResource(index); }
        @Override public long getAmountAsLong(int index) { return handler().getAmountAsLong(index); }
        @Override public long getCapacityAsLong(int index, R resource) { return handler().getCapacityAsLong(index, resource); }
        @Override public boolean isValid(int index, R resource) { return handler().isValid(index, resource); }

        @Override
        public int insert(int index, R resource, int amount, TransactionContext transaction)
        {
            return handler().insert(index, resource, amount, transaction);
        }

        // Without an index: onto what's already there first
        @Override
        public int insert(R resource, int amount, TransactionContext transaction)
        {
            // Items: the library's own way in (the cast is safe: R is ItemResource when the type is ITEMS)
            if (type == TransportTypes.ITEMS.get())
            {
                LibraryControllerBlockEntity found = library.get();
                return found == null ? 0 : found.insertItems((ItemResource) resource, amount, transaction);
            }
            ResourceHandler<R> handler = target.get();
            return handler == null ? 0 : ResourceHandlerUtil.insertStacking(handler, resource, amount, transaction);
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
}
