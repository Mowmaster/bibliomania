package com.mowmaster.bibliomania.Blocks.CardCatalog;

import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLink;
import com.mowmaster.bibliomania.Blocks.Library.LibraryMember;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.tint.CBTintBlockEntity;
import com.mowmaster.mowlib.tint.CBTintData;
import com.mowmaster.mowlib.tint.ITintHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// A placed Card Catalog: what each drawer is set to (DrawerEntry, or nothing), which drawers point at things the
// library doesn't have (drawn "cancelled", and won't open), the colours (region 0 the case, 1-16 each drawer), and its
// library link and lock. Which drawer is open is the block state (CardCatalogBlock.OPEN).
// Every CHECK_TICKS the server checks each set drawer against the library and tells clients if that changed.
public class CardCatalogBlockEntity extends BlockEntity implements ITintHolder, LibraryMember
{
    public static final int MAX_DRAWERS = 16;
    private static final int CHECK_TICKS = 40;
    private static final String TAG_DRAWERS = "drawers";
    private static final String TAG_MISSING = "missing";

    private final DrawerEntry[] drawers = new DrawerEntry[MAX_DRAWERS];
    // Bit n: drawer n's item or book isn't in the library
    private int missing;
    private final CBTintData tints;
    private final LibraryLink libraryLink = new LibraryLink(this::sync);

    public CardCatalogBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.CARD_CATALOG.get(), pos, state);
        this.tints = new CBTintData(this, MAX_DRAWERS + 1);
    }

    public int size()
    {
        return getBlockState().getBlock() instanceof CardCatalogBlock block ? block.size() : 1;
    }

    // ---- Drawers ----

    public @Nullable DrawerEntry drawer(int drawer)
    {
        return drawer >= 0 && drawer < MAX_DRAWERS ? drawers[drawer] : null;
    }

    public void setDrawer(int drawer, @Nullable DrawerEntry entry)
    {
        if (drawer < 0 || drawer >= MAX_DRAWERS) return;
        drawers[drawer] = entry;
        if (level instanceof ServerLevel serverLevel) check(serverLevel);
        sync();
    }

    // Whether the drawer's item or book is missing from the library (or there's no library)
    public boolean isMissing(int drawer)
    {
        return (missing & (1 << drawer)) != 0;
    }

    // ---- Checking against the library (server) ----

    public void serverTick(ServerLevel level)
    {
        if (level.getGameTime() % CHECK_TICKS == 0) check(level);
    }

    public void check(ServerLevel level)
    {
        LibraryControllerBlockEntity library = library(level, worldPosition);
        int newMissing = 0;
        for (int drawer = 0; drawer < size(); drawer++)
        {
            DrawerEntry entry = drawers[drawer];
            // Out of knowledge (LibraryCosts): every drawer shows as missing
            if (entry != null && (library == null || !library.isFueled() || !library.has(entry))) newMissing |= 1 << drawer;
        }
        if (newMissing != missing)
        {
            missing = newMissing;
            sync();
        }
    }

    // ---- Library (LibraryMember) ----

    @Override public @Nullable BlockPos getLibrary() { return libraryLink.library(); }
    @Override public void setLibrary(@Nullable BlockPos controller) { libraryLink.setLibrary(controller); }
    @Override public boolean isLocked() { return libraryLink.locked(); }
    @Override public void setLocked(boolean locked) { libraryLink.setLocked(locked); }

    // ---- Colour (ITintHolder) ----

    @Override public int getColor(int region) { return tints.getColor(region); }
    @Override public void setColor(int region, int color) { tints.setColor(region, color); }
    // A drawer with a fixed colour (Barter: gold, trades: emerald) shows that, whatever it was dyed
    @Override
    public int getTint(int tintIndex)
    {
        DrawerEntry entry = drawer(tintIndex - 1);
        if (entry != null && entry.hasTint()) return entry.tint();
        return tints.getTint(tintIndex);
    }

    // The case colour goes with the item (dyed_color), like the other dyeable blocks; drawer settings don't
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
        List<DrawerEntry.Slot> set = new ArrayList<>();
        for (int drawer = 0; drawer < MAX_DRAWERS; drawer++)
        {
            if (drawers[drawer] != null) set.add(new DrawerEntry.Slot(drawer, drawers[drawer]));
        }
        output.store(TAG_DRAWERS, DrawerEntry.Slot.CODEC.listOf(), set);
        output.putInt(TAG_MISSING, missing);
        tints.save(output);
        libraryLink.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        int[] fixedBefore = fixedTints();
        java.util.Arrays.fill(drawers, null);
        for (DrawerEntry.Slot slot : input.read(TAG_DRAWERS, DrawerEntry.Slot.CODEC.listOf()).orElse(List.of()))
        {
            if (slot.drawer() >= 0 && slot.drawer() < MAX_DRAWERS) drawers[slot.drawer()] = slot.entry();
        }
        missing = input.getIntOr(TAG_MISSING, 0);
        libraryLink.load(input);
        // Only re-mesh on clients when a colour changed
        // (a drawer's fixed colour changing counts too)
        boolean colours = level == null || !level.isClientSide() || !java.util.Arrays.equals(fixedBefore, fixedTints());
        for (int region = 0; region <= MAX_DRAWERS && !colours; region++)
        {
            String key = region == 0 ? "color" : "color" + region;
            if (input.getIntOr(key, CBTintBlockEntity.UNDYED) != tints.getColor(region)) colours = true;
        }
        if (colours) tints.load(input);
    }

    // Each drawer's fixed colour, or -1
    private int[] fixedTints()
    {
        int[] fixed = new int[MAX_DRAWERS];
        for (int drawer = 0; drawer < MAX_DRAWERS; drawer++) fixed[drawer] = drawers[drawer] == null ? -1 : drawers[drawer].tint();
        return fixed;
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
