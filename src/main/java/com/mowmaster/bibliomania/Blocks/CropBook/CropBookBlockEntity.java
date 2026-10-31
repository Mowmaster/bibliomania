package com.mowmaster.bibliomania.Blocks.CropBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
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
import org.jetbrains.annotations.NotNull;

// A placed Farmer's Almanac: its CropContents (soil, seed, stage), saved, synced to clients for the renderer, and
// carried to and from the item like the other books.
public class CropBookBlockEntity extends BlockEntity
{
    private static final String TAG_CROP = "crop";

    private CropContents contents = CropContents.EMPTY;
    // Set while the book is being picked up, so removing the block doesn't also drop it
    private boolean pickedUp = false;

    public CropBookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.CROP_BOOK.get(), pos, state);
    }

    public CropContents getContents()
    {
        return contents;
    }

    public void setContents(CropContents newContents)
    {
        contents = newContents;
        setChanged();
        if (level != null && !level.isClientSide())
        {
            // The book is thicker the further the crop has grown
            if (getBlockState().getBlock() instanceof BookBlock book)
            {
                CropProfile profile = contents.profile();
                float growth = profile == null ? 0f : (float) contents.stage() / Math.max(1, profile.maxStage());
                book.updateThickness(level, worldPosition, getBlockState(), growth);
            }
            // Sends getUpdatePacket() to nearby clients
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- Block <-> item ----

    public ItemStack toItem()
    {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
        // collectComponents() runs collectImplicitComponents below
        stack.applyComponents(collectComponents());
        return stack;
    }

    public void markPickedUp()
    {
        pickedUp = true;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components)
    {
        super.applyImplicitComponents(components);
        CropContents fromItem = components.get(DataComponentRegistry.CROP_CONTENTS.get());
        if (fromItem != null) contents = fromItem.withNextTick(0L);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        components.set(DataComponentRegistry.CROP_CONTENTS.get(), contents);
    }

    // Removed by anything other than a pickup (explosion, /setblock...): drop the book with its contents
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
        output.store(TAG_CROP, CropContents.CODEC, contents);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        contents = input.read(TAG_CROP, CropContents.CODEC).orElse(CropContents.EMPTY);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        return saveCustomOnly(registries); // runs saveAdditional into a tag
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this); // uses getUpdateTag
    }
}
