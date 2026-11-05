package com.mowmaster.bibliomania.Blocks.Detector;

import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLink;
import com.mowmaster.bibliomania.Blocks.Library.LibraryMember;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

// A Storage Detector: the item set on each of its six faces (by Direction#get3DDataValue: down, up, north, south,
// west, east), its library link and lock. Once a second it asks its library how many of each face's item it holds
// and lights the faces whose item has run out (StorageDetectorBlock#setFaces).
public class StorageDetectorBlockEntity extends BlockEntity implements LibraryMember
{
    private static final int CHECK_TICKS = 20;
    private static final String[] TAGS = {"down", "up", "north", "south", "west", "east"};

    private final ItemStack[] items = new ItemStack[6];
    private final LibraryLink libraryLink = new LibraryLink(this::sync);

    public StorageDetectorBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.STORAGE_DETECTOR.get(), pos, state);
        Arrays.fill(items, ItemStack.EMPTY);
    }

    public ItemStack item(Direction face)
    {
        return items[face.get3DDataValue()];
    }

    // Sets (or with EMPTY, clears) a face's item: one of it, its look only
    public void setItem(Direction face, ItemStack stack)
    {
        items[face.get3DDataValue()] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        sync();
        if (level instanceof ServerLevel serverLevel) check(serverLevel);
    }

    // ---- The check (server) ----

    public void serverTick(ServerLevel level)
    {
        if ((level.getGameTime() + worldPosition.asLong()) % CHECK_TICKS == 0) check(level);
    }

    // Each face: NONE with no item; ON if the library holds none of it (exactly that item, components and all); OFF
    // if it holds some, or there's no library to ask
    private void check(ServerLevel level)
    {
        LibraryControllerBlockEntity library = library(level, worldPosition);
        DetectorFace[] faces = new DetectorFace[6];
        for (Direction face : Direction.values())
        {
            ItemStack wanted = item(face);
            DetectorFace shown;
            if (wanted.isEmpty()) shown = DetectorFace.NONE;
            else if (library == null) shown = DetectorFace.OFF;
            else
            {
                // typeTotals with a filter: [0] is how much of exactly that resource the item books hold
                long stocked = library.typeTotals(TransportTypes.ITEMS.get(), ItemResource.of(wanted))[0];
                shown = stocked > 0 ? DetectorFace.OFF : DetectorFace.ON;
            }
            faces[face.get3DDataValue()] = shown;
        }
        StorageDetectorBlock.setFaces(level, worldPosition, getBlockState(), faces);
    }

    // ---- Library (LibraryMember) ----

    @Override public @Nullable BlockPos getLibrary() { return libraryLink.library(); }
    @Override public void setLibrary(@Nullable BlockPos controller) { libraryLink.setLibrary(controller); }
    @Override public boolean isLocked() { return libraryLink.locked(); }
    @Override public void setLocked(boolean locked) { libraryLink.setLocked(locked); }

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
        for (int index = 0; index < 6; index++)
        {
            if (!items[index].isEmpty()) output.store(TAGS[index], ItemStack.OPTIONAL_CODEC, items[index]);
        }
        libraryLink.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        for (int index = 0; index < 6; index++)
        {
            items[index] = input.read(TAGS[index], ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        }
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
