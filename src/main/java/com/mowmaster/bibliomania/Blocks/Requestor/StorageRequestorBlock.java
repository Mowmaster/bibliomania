package com.mowmaster.bibliomania.Blocks.Requestor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import static net.minecraft.world.level.levelgen.structure.Structure.simpleCodec;

// The Patterned Storage Requestor: up to 9 items on its grid, each with a count. On a redstone pulse the whole pattern
// comes out of its library into the container in front, or out into the world like a Dropper if there's none; all of
// it or nothing (see StorageRequestorBlockEntity#request). Setting the pattern: PatternDispenserBlock.
public class StorageRequestorBlock extends PatternDispenserBlock
{
    public StorageRequestorBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new StorageRequestorBlockEntity(pos, state);
    }

    // The pulse (from DispenserBlock#tick)
    @Override
    protected void dispenseFrom(ServerLevel level, BlockState state, BlockPos pos)
    {
        if (!(level.getBlockEntity(pos) instanceof StorageRequestorBlockEntity requestor)) return;
        if (requestor.request(level, state))
        {
            level.levelEvent(LevelEvent.SOUND_DISPENSER_DISPENSE, pos, 0);
            level.levelEvent(LevelEvent.PARTICLES_SHOOT_SMOKE, pos, state.getValue(FACING).get3DDataValue());
        }
        else
        {
            level.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, pos, 0);
        }
    }
}
