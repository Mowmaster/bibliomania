package com.mowmaster.bibliomania.Blocks.Bookcase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

// A half-depth Bookcase: the same six spaces, in either the back half of the block (against a wall) or the front half
// (towards the player), picked where it's placed, like a slab's top or bottom: clicking a wall or the far half of
// the floor puts it at the back, clicking the near half of the floor at the front.
// FRONT=false is the model as drawn (bookcase_half, back half); FRONT=true is bookcase_half_front (moved forwards).
public class HalfBookcaseBlock extends BookcaseBlock
{
    public static final BooleanProperty FRONT = BooleanProperty.create("front");

    // The half a block taken up on each side (north: z 0-8...), in pixels
    private static final Map<Direction, VoxelShape> HALVES = new EnumMap<Direction, VoxelShape>(Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, 8),
            Direction.SOUTH, Block.box(0, 0, 8, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, 8, 16, 16),
            Direction.EAST, Block.box(8, 0, 0, 16, 16, 16)));

    public HalfBookcaseBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FRONT, false));
    }

    // Runs inside the Block constructor (before this class's fields exist), so it only adds the property
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(FRONT);
    }

    // Front or back: which half of the block the click landed in, measured along the way it faces
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Direction facing = state.getValue(FACING);
        BlockPos pos = context.getClickedPos();
        Vec3 at = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        // Positive: past the middle towards the open side (the player's side)
        double towardsFront = facing.getStepX() * (at.x() - 0.5) + facing.getStepZ() * (at.z() - 0.5);
        return state.setValue(FRONT, towardsFront > 0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        Direction facing = state.getValue(FACING);
        return HALVES.get(state.getValue(FRONT) ? facing : facing.getOpposite());
    }

    // The model's spaces: z 8-15.5 at the back, 0-7.5 at the front; slightly thinner shelves than the full one
    @Override
    public float shelfFront(BlockState state)
    {
        return state.getValue(FRONT) ? 0f : 8f;
    }

    @Override
    public float shelfFloor(int row)
    {
        return row == 0 ? 8.25f : 0.5f;
    }
}
