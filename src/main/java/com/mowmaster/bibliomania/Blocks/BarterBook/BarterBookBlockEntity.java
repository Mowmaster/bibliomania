package com.mowmaster.bibliomania.Blocks.BarterBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.ServerConfig;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// A placed Book of Barter: its BarterContents, saved and synced, carried to and from the item. Barters on the server
// tick; the client keeps a display piglin (holding gold while there's gold stored) for the renderer.
public class BarterBookBlockEntity extends BlockEntity
{
    private static final String TAG_BARTER = "barter";

    private BarterContents contents = BarterContents.EMPTY;
    private boolean pickedUp = false;

    // Client only: the piglin drawn above the book
    @Nullable private Entity displayPiglin;

    public BarterBookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.BARTER_BOOK.get(), pos, state);
    }

    public BarterContents getContents()
    {
        return contents;
    }

    public void setContents(BarterContents newContents)
    {
        contents = newContents;
        setChanged();
        if (level != null && !level.isClientSide())
        {
            // Thicker with more gold
            if (getBlockState().getBlock() instanceof BookBlock book)
            {
                book.updateThickness(level, worldPosition, getBlockState(), (float) contents.gold() / ServerConfig.barterMaxGold());
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- Bartering (server, every tick) ----

    public void serverTick(ServerLevel serverLevel)
    {
        BarterActions.Result result = BarterActions.tick(serverLevel, contents, Vec3.atCenterOf(worldPosition));
        if (result.contents() == null) return;
        // Placed: the items drop on the book, like a piglin throwing them
        for (ItemStack item : result.items()) Block.popResource(serverLevel, worldPosition, item);
        if (!result.items().isEmpty()) serverLevel.playSound(null, worldPosition, SoundEvents.PIGLIN_CELEBRATE, SoundSource.BLOCKS, 0.6f, 1f);
        setContents(result.contents());
    }

    // ---- The display piglin (client) ----

    public @Nullable Entity getDisplayPiglin()
    {
        if (level == null || !contents.piglin()) return null;
        if (displayPiglin == null) displayPiglin = MobBookActions.createTemporary(EntityTypes.PIGLIN, level, EntitySpawnReason.LOAD);
        // Gold in its offhand while there's gold stored (where piglins hold gold they're looking at)
        if (displayPiglin instanceof LivingEntity piglin)
        {
            ItemStack held = contents.gold() > 0 ? new ItemStack(Items.GOLD_INGOT) : ItemStack.EMPTY;
            if (!ItemStack.matches(piglin.getItemBySlot(EquipmentSlot.OFFHAND), held)) piglin.setItemSlot(EquipmentSlot.OFFHAND, held);
        }
        return displayPiglin;
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
        BarterContents fromItem = components.get(DataComponentRegistry.BARTER_CONTENTS.get());
        if (fromItem != null) contents = fromItem.withNextDrop(0L);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        components.set(DataComponentRegistry.BARTER_CONTENTS.get(), contents);
    }

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
        output.store(TAG_BARTER, BarterContents.CODEC, contents);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        contents = input.read(TAG_BARTER, BarterContents.CODEC).orElse(BarterContents.EMPTY);
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
