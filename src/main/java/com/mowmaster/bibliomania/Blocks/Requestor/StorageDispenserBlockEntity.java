package com.mowmaster.bibliomania.Blocks.Requestor;

import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.item.Items;
import net.minecraft.core.Direction;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookItem;
import com.mowmaster.bibliomania.Blocks.StorageBook.BookStorage;
import java.util.List;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryDeposit;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLink;
import com.mowmaster.bibliomania.Blocks.Library.LibraryMember;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// A Storage Dispenser: its one-item pattern, its library link and lock, and the item it's using (slot 0).
// It's a DispenserBlockEntity underneath because the Dispenser's behaviours are handed one (in their BlockSource) and
// some use it: a bottle filled from a beehive, out of a stack, puts the honey bottle into it (insertItem). Those extra
// items are stored in the library straight after. Hoppers can neither put anything in nor take anything out
// (canPlaceItem / canTakeItem), and it never opens the Dispenser's screen.
public class StorageDispenserBlockEntity extends DispenserBlockEntity implements LibraryMember, PatternHolder
{
    private final RequestPattern pattern = new RequestPattern(1, 1, this::sync);
    private final LibraryLink libraryLink = new LibraryLink(this::sync);

    public StorageDispenserBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.STORAGE_DISPENSER.get(), pos, state);
    }

    @Override
    public RequestPattern pattern()
    {
        return pattern;
    }

    @Override
    public ItemStack inUse()
    {
        return getItem(0);
    }

    // ---- The pulse ----

    // 1. Anything that isn't the set item (what an earlier use turned it into, if the library was full then; the old
    //    item after the setting changed) goes back to the library first.
    // 2. Set to a Book of Fluids: the dispenser works the library's fluid books directly (workFluid), no items.
    // 3. No item in hand: one is taken from the library's items (fuzzy: at any damage; exact: only one just like the
    //    set one). Buckets too: an empty bucket set needs an empty bucket stored, a water bucket a water bucket.
    // 4. It's used as a Dispenser would use it. The result: the same item (worn shears, bone meal that found nothing
    //    to grow) stays in hand for next time; nothing (used up, or broken) leaves the hand empty; anything else (an
    //    empty bucket, a honey bottle, a water bucket) is stored in the library as an item.
    // Returns whether it did anything.
    public boolean dispense(ServerLevel level, BlockState state, StorageDispenserBlock block)
    {
        LibraryControllerBlockEntity library = library(level, worldPosition);
        ItemStack wanted = pattern.item(0);
        // Out of knowledge (LibraryCosts): it does nothing until the library is refuelled
        if (library == null || !library.isFueled()) return false;
        storeExtras(library);
        ItemStack held = getItem(0);
        if (!held.isEmpty() && (wanted.isEmpty() || !ItemStack.isSameItem(held, wanted)))
        {
            ItemStack left = LibraryDeposit.storeItems(library, held, worldPosition);
            setItem(0, left);
            if (!left.isEmpty()) return false;
        }
        if (wanted.isEmpty()) return false;
        if (isFluidBook(wanted)) return workFluid(level, state, library, bookFluid(wanted));
        if (getItem(0).isEmpty() && !load(library, wanted)) return false;

        ItemStack loaded = getItem(0);
        DispenseItemBehavior behaviour = block.behaviourFor(level, loaded);
        if (behaviour == DispenseItemBehavior.NOOP) return false;
        // BlockSource: the dispenser as the behaviours see it (where it is, its state, its block entity)
        ItemStack result = behaviour.dispense(new BlockSource(level, worldPosition, state, this), loaded);
        if (result.isEmpty() || ItemStack.isSameItem(result, wanted)) setItem(0, result);
        else setItem(0, LibraryDeposit.storeItems(library, result, worldPosition));
        storeExtras(library);
        return true;
    }

    // ---- Set to a Book of Fluids ----

    // A Book of Fluids as the set item (by an empty one: the pump; by one holding a fluid: placing that fluid)
    public static boolean isFluidBook(ItemStack stack)
    {
        return stack.getItem() instanceof StorageBookItem book && book.book().type() == TransportTypes.FLUIDS.get();
    }

    // The fluid in a Book of Fluids (EMPTY for an empty one)
    public static FluidResource bookFluid(ItemStack book)
    {
        BookStorage storage = BookStorage.open(book);
        return storage != null && storage.storage().getResource(0) instanceof FluidResource fluid ? fluid : FluidResource.EMPTY;
    }

    // What's drawn for the set item: a Book of Fluids as a bucket (its fluid's, or an empty one) with the book small in
    // its corner (displayBadge); anything else as itself
    public static ItemStack displayIcon(ItemStack set)
    {
        if (!isFluidBook(set)) return set;
        FluidResource fluid = bookFluid(set);
        ItemStack bucket = fluid.isEmpty() ? ItemStack.EMPTY : new ItemStack(fluid.getFluid().getBucket());
        return bucket.isEmpty() ? new ItemStack(Items.BUCKET) : bucket;
    }

    public static ItemStack displayBadge(ItemStack set)
    {
        return isFluidBook(set) ? new ItemStack(set.getItem()) : ItemStack.EMPTY;
    }

    // No fluid: the fluid in front is picked up into the library's fluid books (FluidUtil.tryPickupFluid, into an
    // insert-only handler of them; null transaction: it opens and commits its own). A fluid: a bucket's worth of it is
    // taken from the fluid books and placed in front, in one transaction that's only kept if the placing worked.
    private boolean workFluid(ServerLevel level, BlockState state, LibraryControllerBlockEntity library, FluidResource fluid)
    {
        Direction facing = state.getValue(DispenserBlock.FACING);
        BlockPos front = worldPosition.relative(facing);
        if (fluid.isEmpty())
        {
            ResourceHandler<FluidResource> into = new LibraryDeposit.InsertOnly<>(() -> library, TransportTypes.FLUIDS.get(), worldPosition);
            return !FluidUtil.tryPickupFluid(into, null, level, front, facing.getOpposite(), null).isEmpty();
        }
        ResourceHandler<FluidResource> fluids = library.typeHandler(TransportTypes.FLUIDS.get());
        if (fluids == null) return false;
        try (Transaction transaction = Transaction.openRoot())
        {
            // Paid for, like any extract (LibraryCosts)
            if (LibraryDeposit.paidExtract(library, TransportTypes.FLUIDS.get(), fluids, -1, fluid, FluidType.BUCKET_VOLUME, worldPosition, transaction) < FluidType.BUCKET_VOLUME) return false;
            // false: like a bucket, it counts as placed even into a block that takes fluid but says no
            if (!FluidUtil.tryPlaceFluid(fluid, null, level, front, false)) return false;
            transaction.commit();
        }
        return true;
    }

    // One of the set item from the library into the hand
    private boolean load(LibraryControllerBlockEntity library, ItemStack wanted)
    {
        try (Transaction transaction = Transaction.openRoot())
        {
            // false: a bucket must be stored as a bucket (never filled from the fluid books)
            List<ItemStack> got = LibraryDeposit.take(library, wanted, 1, pattern.exact(), false, worldPosition, transaction);
            if (got.isEmpty()) return false;
            transaction.commit();
            // getFirst (Java 21): the list's first element
            setItem(0, got.getFirst());
            return true;
        }
    }

    // Slots 1-8: what behaviours put in (insertItem), into the library (whatever doesn't fit stays)
    private void storeExtras(LibraryControllerBlockEntity library)
    {
        for (int slot = 1; slot < getContainerSize(); slot++)
        {
            ItemStack extra = getItem(slot);
            if (!extra.isEmpty()) setItem(slot, LibraryDeposit.storeItems(library, extra, worldPosition));
        }
    }

    // ---- Not a container for hoppers ----

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack)
    {
        return false;
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack)
    {
        return false;
    }

    // Broken: what it holds goes back to the library if it can; the rest drops (DispenserBlockEntity's own removal)
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        LibraryControllerBlockEntity library = library(level, pos);
        if (library != null)
        {
            for (int slot = 0; slot < getContainerSize(); slot++)
            {
                ItemStack stack = getItem(slot);
                if (!stack.isEmpty()) setItem(slot, LibraryDeposit.storeItems(library, stack, worldPosition));
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    // ---- Library (LibraryMember) ----

    @Override public @Nullable BlockPos getLibrary() { return libraryLink.library(); }
    @Override public void setLibrary(@Nullable BlockPos controller) { libraryLink.setLibrary(controller); }
    @Override public boolean isLocked() { return libraryLink.locked(); }
    @Override public void setLocked(boolean locked) { libraryLink.setLocked(locked); }

    // ---- Saving and syncing (the items in it are saved by DispenserBlockEntity) ----

    private void sync()
    {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // The item in hand changing tells clients too (the Reading Goggles show it)
    @Override
    public void setItem(int slot, ItemStack stack)
    {
        super.setItem(slot, stack);
        if (slot == 0) sync();
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        pattern.save(output);
        libraryLink.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        pattern.load(input);
        libraryLink.load(input);
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
