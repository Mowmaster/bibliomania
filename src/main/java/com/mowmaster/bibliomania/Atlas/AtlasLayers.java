package com.mowmaster.bibliomania.Atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

// Which map an Atlas shows, and how a column of it is coloured. Worked out on the server only (AtlasExplorer), which
// tells the client the layer with every tile message (AtlasPayloads.Tiles), so the two always agree.
// - SURFACE: the ground seen from above, when the player is no more than UNDER blocks below the top of their column
//   (the MOTION_BLOCKING_NO_LEAVES heightmap: trees don't count)
// - otherwise a slice: SLICE blocks of height (y 0-31 is slice 0, y -32 to -1 slice -1...), each column showing its
//   highest floor in the slice (a block you could stand on, with open space above it), as if looking down from the
//   slice's top. Under the Nether's bedrock roof the player is always "under" (the heightmap is the roof), so the
//   Nether maps in slices too; anywhere open to the sky (the End) maps its surface.
public final class AtlasLayers
{
    private AtlasLayers() {}

    public static final int SURFACE = Integer.MIN_VALUE;
    public static final int SLICE = 32;
    private static final int UNDER = 4;

    // A column with no floor in the slice (solid rock all the way): drawn as dark stone
    private static final byte SOLID = MapColor.DEEPSLATE.getPackedId(MapColor.Brightness.LOW);
    // Nothing there at all (the void): black
    private static final byte VOID = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOW);

    // Stepping over a boundary (a slice's top or bottom, or the surface line) and straight back is common in caves and
    // on stairs; MARGIN blocks of slack keep the map from flicking between layers (and the tiles being sent again)
    private static final int MARGIN = 2;

    // The layer for a player at pos. previous: the layer they were last shown (null: none yet), which they keep while
    // within MARGIN blocks of it
    public static int layerAt(Level level, BlockPos pos, @Nullable Integer previous)
    {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        int y = pos.getY();
        if (previous == null) return y >= ground - UNDER ? SURFACE : slice(y);
        if (previous == SURFACE) return y >= ground - UNDER - MARGIN ? SURFACE : slice(y);
        if (y >= ground - UNDER + MARGIN) return SURFACE;
        int bottom = bottom(previous);
        return y >= bottom - MARGIN && y < bottom + SLICE + MARGIN ? previous : slice(y);
    }

    // The slice a y is in. floorDiv rounds down (towards -infinity), so y -1 is slice -1, not 0 as y / 32 would give
    public static int slice(int y)
    {
        return Math.floorDiv(y, SLICE);
    }

    // The slice's lowest y
    public static int bottom(int layer)
    {
        return layer * SLICE;
    }

    // ---- Colours (server) ----

    // The surface's packed colour (colour id * 4 + shade; never 0, which means "not explored"): the top block's map
    // colour, lighter or darker by its height against the column to the north (as vanilla maps do). A column with
    // nothing in it at all (the End's void) is black.
    public static byte surfaceColor(Level level, int x, int z, BlockPos.MutableBlockPos pos)
    {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (top < level.getMinY()) return VOID;
        int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1;
        return shaded(level, pos.set(x, top, z), top, north);
    }

    // The highest floor in the slice at a column (its y); NO_FLOOR if it's solid all the way down, OPEN if it's open
    // all the way (a drop into the void, or a cave taller than the slice).
    // The slice's top block counts if the block above the slice is open (lava lakes, the Nether's lava sea at y 31),
    // except bedrock: the top of the Nether's roof (y 127, open sky above) would cover the whole slice under it.
    public static final int NO_FLOOR = Integer.MIN_VALUE, OPEN = Integer.MIN_VALUE + 1;

    public static int floor(Level level, int x, int z, int layer, BlockPos.MutableBlockPos pos)
    {
        int bottom = bottom(layer), top = bottom + SLICE - 1;
        boolean openAbove = open(level, pos.set(x, top + 1, z));
        boolean anySolid = false;
        for (int y = top; y >= bottom; y--)
        {
            boolean open = open(level, pos.set(x, y, z));
            if (!open)
            {
                anySolid = true;
                if (openAbove && !(y == top && level.getBlockState(pos).is(Blocks.BEDROCK))) return y;
            }
            openAbove = open;
        }
        return anySolid ? NO_FLOOR : OPEN;
    }

    // A slice column's packed colour: its floor's, shaded against the floor to the north; dark stone if it's solid,
    // black if it's open all the way down
    public static byte sliceColor(Level level, int x, int z, int floor, int northFloor, BlockPos.MutableBlockPos pos)
    {
        if (floor == NO_FLOOR) return SOLID;
        if (floor == OPEN) return VOID;
        return shaded(level, pos.set(x, floor, z), floor, northFloor == NO_FLOOR || northFloor == OPEN ? floor : northFloor);
    }

    private static byte shaded(Level level, BlockPos pos, int height, int northHeight)
    {
        BlockState state = level.getBlockState(pos);
        MapColor color = state.getMapColor(level, pos);
        if (color == MapColor.NONE) return 3;
        MapColor.Brightness shade = height > northHeight ? MapColor.Brightness.HIGH
                : height < northHeight ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        return color.getPackedId(shade);
    }

    // Space you could be in: nothing to bump into, and no fluid (water and lava count as floors, so lakes show)
    private static boolean open(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
    }
}
