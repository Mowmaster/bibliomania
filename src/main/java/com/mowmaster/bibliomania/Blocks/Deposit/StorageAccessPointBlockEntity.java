package com.mowmaster.bibliomania.Blocks.Deposit;

import com.mowmaster.bibliomania.Blocks.Library.LibraryDeposit;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLink;
import com.mowmaster.bibliomania.Blocks.Library.LibraryMember;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.tint.CBTintBlockEntity;
import com.mowmaster.mowlib.tint.CBTintData;
import com.mowmaster.mowlib.tint.ITintHolder;
import com.mowmaster.mowlib.transport.TransportType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// A Storage Access Point: a library member that keeps nothing itself but its colour, library link and lock.
public class StorageAccessPointBlockEntity extends BlockEntity implements LibraryMember, ITintHolder
{
    private final LibraryLink libraryLink = new LibraryLink(this::sync);
    private final CBTintData tints;

    public StorageAccessPointBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.STORAGE_ACCESS_POINT.get(), pos, state);
        this.tints = new CBTintData(this, 1);
    }

    // What automation gets for `type`: the library's books of that type, in and out (LibraryDeposit.FullAccess)
    public <R extends Resource> ResourceHandler<R> accessHandler(TransportType<R> type)
    {
        return new LibraryDeposit.FullAccess<>(() -> library(level, worldPosition), type, worldPosition);
    }

    // ---- Library (LibraryMember) ----

    @Override public @Nullable BlockPos getLibrary() { return libraryLink.library(); }
    @Override public void setLibrary(@Nullable BlockPos controller) { libraryLink.setLibrary(controller); }
    @Override public boolean isLocked() { return libraryLink.locked(); }
    @Override public void setLocked(boolean locked) { libraryLink.setLocked(locked); }

    // ---- Colour (ITintHolder) ----

    @Override public int getColor(int region) { return tints.getColor(region); }
    @Override public void setColor(int region, int color) { tints.setColor(region, color); }
    @Override public int getTint(int tintIndex) { return tints.getTint(tintIndex); }

    // The colour goes with the item (dyed_color), like the other dyeable blocks
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
        tints.save(output);
        libraryLink.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        libraryLink.load(input);
        // CBTintData#load re-meshes the chunk on clients: only when the colour actually changed
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
