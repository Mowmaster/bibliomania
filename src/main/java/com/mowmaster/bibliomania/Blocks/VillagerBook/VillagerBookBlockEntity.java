package com.mowmaster.bibliomania.Blocks.VillagerBook;

import com.mowmaster.mowlib.blockentity.CBSyncedBlockEntity;
import com.mowmaster.bibliomania.Blocks.Book.PickupableBook;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// A placed Trader's Ledger: its VillagerContents, saved and synced to clients for the renderer, carried to and from
// the item like the other books. IPageHolder lets MowLib's page keys (and the page arrows) turn the trade on show.
// The server ticks it for restocking; the client keeps a display villager for the renderer.
public class VillagerBookBlockEntity extends CBSyncedBlockEntity implements IPageHolder, PickupableBook
{
    private static final String TAG_VILLAGER = "villager";

    private VillagerContents contents = VillagerContents.EMPTY;
    private boolean pickedUp = false;

    // Client only: the villager drawn above the book, made again when its data changes
    // (a Villager, or a WanderingTrader for a book holding one)
    @Nullable private LivingEntity displayVillager;

    public VillagerBookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.VILLAGER_BOOK.get(), pos, state);
    }

    public VillagerContents getContents()
    {
        return contents;
    }

    public void setContents(VillagerContents newContents)
    {
        contents = newContents;
        setChanged();
        // Sends getUpdatePacket() to nearby clients
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- IPageHolder: the trade on show ----

    @Override
    public int getPageCount()
    {
        return contents.pageCount();
    }

    @Override
    public int getPage()
    {
        return contents.page();
    }

    @Override
    public void setPage(int page)
    {
        setContents(contents.withPage(page));
    }

    // ---- Restocking (server, every tick) ----

    // When the timer runs out every trade is usable again, and the bell rings
    public void serverTick(ServerLevel serverLevel)
    {
        if (contents.restockAt() == 0 || serverLevel.getGameTime() < contents.restockAt()) return;
        setContents(VillagerBookActions.restock(contents));
        serverLevel.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1f);
    }

    // ---- The display villager (client) ----

    public @Nullable LivingEntity getDisplayVillager()
    {
        if (level == null || contents.villager().isEmpty()) return null;
        if (contents.wandering())
        {
            // `instanceof Villager` false: a wandering trader is already showing (or nothing is)
            if (displayVillager == null || displayVillager instanceof Villager) displayVillager = VillagerBookActions.temporaryWanderingTrader(level);
        }
        // Pattern matching: `!(x instanceof Villager v) || ...` - v only exists where the instanceof was true
        else if (!(displayVillager instanceof Villager villager) || !villager.getVillagerData().equals(contents.villager().get()))
        {
            displayVillager = VillagerBookActions.temporaryVillager(level, contents.villager().get(), 0);
        }
        return displayVillager;
    }

    // ---- Block <-> item ----

    public ItemStack toItem()
    {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
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
        VillagerContents fromItem = components.get(DataComponentRegistry.VILLAGER_CONTENTS.get());
        if (fromItem != null) contents = fromItem;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        components.set(DataComponentRegistry.VILLAGER_CONTENTS.get(), contents);
    }

    // Removed by anything other than a pickup (explosion, /setblock...): drop the book with its villager
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
        output.store(TAG_VILLAGER, VillagerContents.CODEC, contents);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        contents = input.read(TAG_VILLAGER, VillagerContents.CODEC).orElse(VillagerContents.EMPTY);
    }

}
