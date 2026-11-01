package com.mowmaster.bibliomania.Blocks.Requestor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Where the pattern grid is on a Patterned Storage Requestor or Storage Dispenser, which can face all six ways.
// Their model is built facing south (the front, the output, is its south face; the grid is its up face, its arrow
// pointing to the front), and the blockstate turns it for each facing (rotation): y turns for the four sides, x turns
// for up and down. The same turn is used to draw the grid's items and to work out which cell a click is on, by turning
// the click back into the model's own frame.
// The grid's cells (storage_requester_patterned_top.png): centres at pixels 5, 8 and 11 across (x) and 4, 7 and 10
// down (z, north to south); slot = row * 3 + column.
public final class RequestorGrid
{
    public static final float[] CELL_X = {5f, 8f, 11f};
    public static final float[] CELL_Z = {4f, 7f, 10f};
    // The Storage Dispenser's one cell (storage_requester_patterned_top_dispensor.png): its centre
    public static final float SINGLE_X = 8f;
    public static final float SINGLE_Z = 7f;
    // How far from a cell's centre still counts as on it, in pixels
    private static final float CELL_REACH = 1.5f;

    private RequestorGrid() {}

    // The blockstate rotation for a facing, in degrees: {x, y}. Must match the blockstate files.
    public static int[] rotation(Direction facing)
    {
        return switch (facing)
        {
            case SOUTH -> new int[]{0, 0};
            case WEST -> new int[]{0, 90};
            case NORTH -> new int[]{0, 180};
            case EAST -> new int[]{0, 270};
            case UP -> new int[]{90, 0};
            case DOWN -> new int[]{270, 0};
        };
    }

    // The face the grid ends up on: the top for the four sides; facing up the model's top turns to the north, facing
    // down to the south
    public static Direction gridFace(Direction facing)
    {
        return switch (facing)
        {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    // A point in the block (0-1 each way) turned back into the model's frame. The blockstate turns the model by y
    // then x, both clockwise (negative angles); undoing it is x then y the other way.
    public static Vec3 toModel(Direction facing, Vec3 inBlock)
    {
        int[] rotation = rotation(facing);
        Vector3f point = new Vector3f((float) inBlock.x - 0.5f, (float) inBlock.y - 0.5f, (float) inBlock.z - 0.5f);
        new Quaternionf().rotateX((float) Math.toRadians(rotation[0])).rotateY((float) Math.toRadians(rotation[1])).transform(point);
        return new Vec3(point.x + 0.5, point.y + 0.5, point.z + 0.5);
    }

    // The cell a click (or the crosshair) is on: 0-8, or -1 off the grid. With one slot (the Storage Dispenser),
    // anywhere on the grid face is slot 0.
    public static int cellAt(Direction facing, BlockHitResult hit, int slots)
    {
        if (hit.getDirection() != gridFace(facing)) return -1;
        if (slots == 1) return 0;
        BlockPos pos = hit.getBlockPos();
        Vec3 model = toModel(facing, hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        int column = nearest(CELL_X, (float) model.x * 16f);
        int row = nearest(CELL_Z, (float) model.z * 16f);
        return column < 0 || row < 0 ? -1 : row * 3 + column;
    }

    private static int nearest(float[] centres, float at)
    {
        for (int index = 0; index < centres.length; index++)
        {
            if (Math.abs(at - centres[index]) <= CELL_REACH) return index;
        }
        return -1;
    }
}
