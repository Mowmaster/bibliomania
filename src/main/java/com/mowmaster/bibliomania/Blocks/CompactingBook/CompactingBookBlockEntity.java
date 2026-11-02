package com.mowmaster.bibliomania.Blocks.CompactingBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
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
public class CompactingBookBlockEntity extends BlockEntity implements PickupableBook, IPageHolder, ISpinningItemHolder, LibraryPriority.Holder
{
    private static final String TAG_CONTENTS = "compacting";
    private static final String TAG_PRIORITY = "priority";

    private CompactingContents contents = CompactingContents.EMPTY;
    private boolean pickedUp;
    private int priority;

    public CompactingBookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.COMPACTING_BOOK.get(), pos, state);
    }

    public CompactingContents contents()
    {
        return contents;
    }

    // ---- In and out (server) ----

    // As much of the stack as fits; an empty book first takes the stack's chain. Returns how many went in.
    public int insert(ItemStack stack)
    {
        if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty()) return 0;
        CompactingContents working = contents;
        if (!working.isSet())
        {
            List<CompactingContents.Tier> chain = CompactingChains.find(stack.getItem());
            if (chain == null) return 0;
            working = CompactingContents.of(chain, contents.page());
        }
        int tier = working.tierOf(ItemResource.of(stack));
        if (tier < 0) return 0;
        int inserted = Math.min(stack.getCount(), working.room(tier));
        if (inserted <= 0) return 0;
        // A new chain opens at the tier that went in
        if (!contents.isSet()) working = working.withPage(tier);
        setContents(working.plus(tier, inserted));
        return inserted;
    }

    // Up to `max` of the open tier (no more than a stack)
    public ItemStack extractOpenTier(int max)
    {
        int tier = contents.page();
        int taken = Math.min(Math.min(max, contents.amount(tier)), contents.stack(tier, 1).getMaxStackSize());
        if (taken <= 0) return ItemStack.EMPTY;
        ItemStack out = contents.stack(tier, taken);
        setContents(contents.plus(tier, -taken));
        return out;
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
        CompactingContents saved = components.get(DataComponentRegistry.COMPACTING_CONTENTS.get());
        contents = saved == null ? CompactingContents.EMPTY : saved;
        Integer itemPriority = components.get(DataComponentRegistry.PRIORITY.get());
        priority = itemPriority == null ? 0 : itemPriority;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        if (contents.isSet()) components.set(DataComponentRegistry.COMPACTING_CONTENTS.get(), contents);
        if (priority > 0) components.set(DataComponentRegistry.PRIORITY.get(), priority);
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
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        contents = input.read(TAG_CONTENTS, CompactingContents.CODEC).orElse(CompactingContents.EMPTY);
        priority = input.getIntOr(TAG_PRIORITY, 0);
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
