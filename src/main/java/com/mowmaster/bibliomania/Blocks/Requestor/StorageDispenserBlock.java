package com.mowmaster.bibliomania.Blocks.Requestor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// The Storage Dispenser: set to one item. On a redstone pulse it uses that item like a Dispenser would (bone meal on
// a crop, a bottle on a beehive, shears on a sheep, a bucket on water...), taking it from its library when it has none
// in hand, and stores what the item turns into (see StorageDispenserBlockEntity#dispense).
public class StorageDispenserBlock extends PatternDispenserBlock
{

    public StorageDispenserBlock(Properties properties)
    {
        super(properties);
    }


    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new StorageDispenserBlockEntity(pos, state);
    }

    // What a Dispenser would do with the item: DispenserBlock's own choice (its registry of behaviours: buckets,
    // bone meal, shears, projectiles...; anything else is thrown out as an item)
    public DispenseItemBehavior behaviourFor(Level level, ItemStack stack)
    {
        return getDispenseMethod(level, stack);
    }

    @Override
    protected void dispenseFrom(ServerLevel level, BlockState state, BlockPos pos)
    {
        if (!(level.getBlockEntity(pos) instanceof StorageDispenserBlockEntity dispenser)) return;
        // The behaviours play the dispenser's own sounds and smoke
        if (!dispenser.dispense(level, state, this)) level.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, pos, 0);
    }
}
