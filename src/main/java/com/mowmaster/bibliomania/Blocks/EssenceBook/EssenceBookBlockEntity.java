package com.mowmaster.bibliomania.Blocks.EssenceBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.MobBook.IMobBookDisplay;
import com.mowmaster.bibliomania.Blocks.MobBook.MobAppearance;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// A placed Book of Essence: its EssenceContents and when a mob was last killed (red flash). Like the Bestiary's block
// entity (MobBookBlockEntity), it ticks on the server (spawns) and keeps a fake display mob on the client.
public class EssenceBookBlockEntity extends BlockEntity implements IMobBookDisplay
{
    private static final String TAG_ESSENCE = "essence";
    private static final String TAG_LAST_KILL = "last_kill";

    private EssenceContents contents = EssenceContents.EMPTY;
    private long lastKill = -100;
    // Set while the book is being picked up, so removing the block doesn't also drop it
    private boolean pickedUp = false;

    // Client only, not saved
    @Nullable private Entity displayMob;
    @Nullable private DataComponentMap displayLook;

    public EssenceBookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.ESSENCE_BOOK.get(), pos, state);
    }

    // ---- Contents ----

    public EssenceContents getContents()
    {
        return contents;
    }

    public void setContents(EssenceContents newContents)
    {
        contents = newContents;
        changed();
    }

    @Override
    public long getLastKill()
    {
        return lastKill;
    }

    // A kill just happened: the display flashes red (sent to clients)
    public void markKilled()
    {
        if (level != null) lastKill = level.getGameTime();
        changed();
    }

    private void changed()
    {
        setChanged();
        if (level != null && !level.isClientSide())
        {
            if (getBlockState().getBlock() instanceof BookBlock book)
            {
                book.updateThickness(level, worldPosition, getBlockState(), contents.fullness());
            }
            // Sends getUpdatePacket() to nearby clients
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- Spawning (server, every tick; see EssenceContents#tick) ----

    public void serverTick(ServerLevel serverLevel)
    {
        EssenceContents updated = contents.tick(serverLevel.getGameTime(), serverLevel.getRandom());
        if (updated != null) setContents(updated);
    }

    // ---- IMobBookDisplay: the mob spinning above the book (client) ----

    @Override
    public @Nullable Entity getDisplayMob()
    {
        if (level == null || contents.type().isEmpty()) return null;
        EntityType<?> type = contents.type().get();
        if (displayMob == null || displayMob.getType() != type || !contents.appearance().equals(displayLook))
        {
            displayMob = MobBookActions.createTemporary(type, level, EntitySpawnReason.LOAD);
            if (displayMob == null) return null;
            MobAppearance.apply(displayMob, contents.appearance());
            // Positioned at the book, so it's lit by the light where the book is
            displayMob.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
            displayLook = contents.appearance();
        }
        return displayMob;
    }

    @Override
    public @Nullable EntityType<?> getDisplayType()
    {
        return contents.type().orElse(null);
    }

    // The mob grows from 40% to full size as the essence fills
    @Override
    public float getDisplayScale()
    {
        return 0.4f + 0.6f * Math.min(1f, contents.essence());
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
        EssenceContents fromItem = components.get(DataComponentRegistry.ESSENCE_CONTENTS.get());
        if (fromItem != null) contents = fromItem;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        components.set(DataComponentRegistry.ESSENCE_CONTENTS.get(), contents);
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
        output.store(TAG_ESSENCE, EssenceContents.CODEC, contents);
        output.putLong(TAG_LAST_KILL, lastKill);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        contents = input.read(TAG_ESSENCE, EssenceContents.CODEC).orElse(EssenceContents.EMPTY);
        lastKill = input.getLongOr(TAG_LAST_KILL, -100);
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
