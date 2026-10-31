package com.mowmaster.bibliomania.Blocks.MobBook;

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
import net.minecraft.world.entity.AgeableMob;
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

// A placed Bestiary: the mobs (MobBookContents) and when one was last killed (for the red flash on the display).
// On the client it also keeps the two display mobs MobBookRenderer draws: fake copies that are never added to the
// world and never tick, made once and reused every frame (like a mob spawner's spinning mob).
public class MobBookBlockEntity extends BlockEntity
{
    private static final String TAG_MOBS = "mobs";
    private static final String TAG_LAST_KILL = "last_kill";

    private MobBookContents contents = MobBookContents.EMPTY;
    private long lastKill = -100;
    // Set while the book is being picked up, so removing the block doesn't also drop it
    private boolean pickedUp = false;

    // Client only, not saved
    @Nullable private Entity displayAdult;
    @Nullable private Entity displayBaby;

    public MobBookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.MOB_BOOK.get(), pos, state);
    }

    // ---- Contents ----

    // What it holds now (babies whose cooldown is over count as adults)
    public MobBookContents getContents()
    {
        return level == null ? contents : contents.settle(level.getGameTime());
    }

    public void setContents(MobBookContents newContents)
    {
        contents = newContents;
        changed();
    }

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
            if (getBlockState().getBlock() instanceof MobBookBlock book)
            {
                book.updateThickness(level, worldPosition, getBlockState(), getContents().fullness());
            }
            // Sends getUpdatePacket() to nearby clients
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- Display mobs (client) ----

    // The fake adult or baby to draw, made the first time it's needed (and again if the type changed)
    @Nullable
    public Entity getDisplayMob(boolean baby)
    {
        if (level == null || contents.type().isEmpty()) return null;
        EntityType<?> type = contents.type().get();
        Entity existing = baby ? displayBaby : displayAdult;
        if (existing != null && existing.getType() == type) return existing;

        Entity created = type.create(level, EntitySpawnReason.LOAD);
        if (created == null) return null;
        // Positioned at the book, so it's lit by the light where the book is
        created.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        if (baby && created instanceof AgeableMob ageable) ageable.setBaby(true);
        if (baby) displayBaby = created; else displayAdult = created;
        return created;
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
        MobBookContents fromItem = components.get(DataComponentRegistry.MOB_CONTENTS.get());
        if (fromItem != null) contents = fromItem;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        components.set(DataComponentRegistry.MOB_CONTENTS.get(), getContents());
    }

    // Removed by anything other than a pickup (explosion, /setblock...): drop the book with its mobs
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
        output.store(TAG_MOBS, MobBookContents.CODEC, contents);
        output.putLong(TAG_LAST_KILL, lastKill);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        // read returns an Optional: orElse gives an empty book if nothing was saved
        contents = input.read(TAG_MOBS, MobBookContents.CODEC).orElse(MobBookContents.EMPTY);
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
