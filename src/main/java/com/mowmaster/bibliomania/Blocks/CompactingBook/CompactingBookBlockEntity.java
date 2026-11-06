package com.mowmaster.bibliomania.Blocks.CompactingBook;

import com.mowmaster.mowlib.compacting.Compacting;
import com.mowmaster.mowlib.compacting.CompactingChains;
import com.mowmaster.mowlib.compacting.CompactingContents;
import com.mowmaster.mowlib.blockentity.CBSyncedBlockEntity;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.filter.HeldFilters;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.mowlib.filter.IFilterHolder;
import net.minecraft.world.item.ItemStackTemplate;
import com.mowmaster.bibliomania.Blocks.Book.PickupableBook;
import com.mowmaster.bibliomania.Blocks.Library.LibraryPriority;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.mowlib.display.ISpinningItemHolder;
import com.mowmaster.mowlib.paging.IPageHolder;
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
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// A placed Compacting Book: its CompactingContents (chain, pool, open tier). The open tier's item spins above it
// (MowLib's SpinningItemRenderer); the page keys and the corner arrows turn through the tiers.
public class CompactingBookBlockEntity extends CBSyncedBlockEntity implements PickupableBook, IPageHolder, ISpinningItemHolder, LibraryPriority.Holder, IFilterHolder
{
    private static final String TAG_CONTENTS = "compacting";
    private static final String TAG_PRIORITY = "priority";
    private static final String TAG_FILTER = "filter";

    private CompactingContents contents = CompactingContents.EMPTY;
    private boolean pickedUp;
    private int priority;
    // A MowLib filter (MowLib HeldFilters): which chains an empty book may start; EMPTY for any
    private ItemStack filter = ItemStack.EMPTY;

    public CompactingBookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.COMPACTING_BOOK.get(), pos, state);
    }

    public CompactingContents contents()
    {
        return contents;
    }

    // ---- In and out (server) ----

    // As much of the stack as fits; an empty book first takes the stack's chain, if its filter allows it (MowLib
    // Compacting.insert). Returns how many went in.
    public int insert(ItemStack stack)
    {
        Compacting.Inserted result = Compacting.insert(contents, ItemResource.of(stack), stack.getCount(), CompactingBookItem.CAPACITY,
                chain -> CompactingChains.filterAllows(filter, chain));
        if (result.count() > 0) setContents(result.contents());
        return result.count();
    }

    // Up to `max` of the open tier (no more than a stack)
    public ItemStack extractOpenTier(int max)
    {
        Compacting.Extracted result = Compacting.extract(contents, contents.page(), max);
        if (!result.stack().isEmpty()) setContents(result.contents());
        return result.stack();
    }

    private void setContents(CompactingContents changed)
    {
        contents = changed;
        setChanged();
        if (level == null) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        if (getBlockState().getBlock() instanceof BookBlock book) book.updateThickness(level, worldPosition, getBlockState(), contents.fullness());
    }

    // ---- Pages: the tiers (IPageHolder) ----

    @Override public int getPageCount() { return Math.max(1, contents.size()); }
    @Override public int getPage() { return contents.page(); }

    @Override
    public void setPage(int page)
    {
        if (contents.isSet()) setContents(contents.withPage(page));
    }

    // ---- The open tier spinning above (ISpinningItemHolder) ----

    @Override public ItemStack getDisplayedItem() { return contents.stack(contents.page(), 1); }
    @Override public double getDisplayHeight() { return (getBlockState().getValue(BookBlock.THICKNESS) + 4) / 16.0; }
    @Override public float getDisplayScale() { return 1.5f; }

    // ---- Filter (IFilterHolder): which chains an empty book may start (MowLib CompactingChains.filterAllows) ----

    @Override
    public ItemStack getFilter()
    {
        return filter;
    }

    // One filter from the stack, if it's a filter and there isn't one yet
    @Override
    public boolean insertFilter(ItemStack stack)
    {
        if (!filter.isEmpty() || !HeldFilters.isFilter(stack)) return false;
        filter = stack.split(1);
        filterChanged();
        return true;
    }

    @Override
    public ItemStack removeFilter()
    {
        ItemStack removed = filter;
        filter = ItemStack.EMPTY;
        if (!removed.isEmpty()) filterChanged();
        return removed;
    }

    private void filterChanged()
    {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- Library priority (LibraryPriority) ----

    @Override public int getPriority() { return priority; }
    @Override public int maxPriority() { return LibraryPriority.MAX_BOOK; }

    @Override
    public void setPriority(int newPriority)
    {
        if (newPriority == priority) return;
        priority = newPriority;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- Block <-> item (PickupableBook) ----

    @Override
    public ItemStack toItem()
    {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
        stack.applyComponents(collectComponents());
        return stack;
    }

    @Override
    public void markPickedUp()
    {
        pickedUp = true;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components)
    {
        super.applyImplicitComponents(components);
        CompactingContents saved = components.get(MowLibDataComponents.COMPACTING_CONTENTS.get());
        contents = saved == null ? CompactingContents.EMPTY : saved;
        Integer itemPriority = components.get(DataComponentRegistry.PRIORITY.get());
        priority = itemPriority == null ? 0 : itemPriority;
        ItemStackTemplate itemFilter = components.get(MowLibDataComponents.HELD_FILTER.get());
        filter = itemFilter == null ? ItemStack.EMPTY : itemFilter.create();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        if (contents.isSet()) components.set(MowLibDataComponents.COMPACTING_CONTENTS.get(), contents);
        if (priority > 0) components.set(DataComponentRegistry.PRIORITY.get(), priority);
        if (!filter.isEmpty()) components.set(MowLibDataComponents.HELD_FILTER.get(), ItemStackTemplate.fromNonEmptyStack(filter));
    }

    // Removed by anything other than a pickup: drop the book with its contents
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (pickedUp || !(level instanceof ServerLevel)) return;
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), toItem());
    }

    // ---- Saving and syncing ----

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        if (contents.isSet()) output.store(TAG_CONTENTS, CompactingContents.CODEC, contents);
        output.putInt(TAG_PRIORITY, priority);
        // OPTIONAL_CODEC: an ItemStack codec that also allows EMPTY
        if (!filter.isEmpty()) output.store(TAG_FILTER, ItemStack.OPTIONAL_CODEC, filter);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        contents = input.read(TAG_CONTENTS, CompactingContents.CODEC).orElse(CompactingContents.EMPTY);
        priority = input.getIntOr(TAG_PRIORITY, 0);
        filter = input.read(TAG_FILTER, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

}
