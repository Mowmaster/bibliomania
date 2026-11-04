package com.mowmaster.bibliomania.Atlas;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// The client's copy of the shown Atlas's explored tiles, in the dimension and layer the player is in (ClientSide/AtlasHud
// reads it). No client-only classes, so this class is safe to load on a server too (the payload handler names it).
// Tiles for another Atlas or dimension than the one being shown are ignored; the layer is the server's to say: a
// `replace` message sets it (and drops the old tiles), and newly explored tiles of any other layer are ignored.
public final class AtlasClientCache
{
    private AtlasClientCache() {}

    public static @Nullable UUID atlas;
    public static @Nullable ResourceKey<Level> dimension;
    // AtlasLayers.SURFACE, or an underground slice
    public static int layer = AtlasLayers.SURFACE;
    public static final Map<Long, byte[]> TILES = new HashMap<>();

    // Starts over for another Atlas or dimension
    public static void reset(@Nullable UUID newAtlas, @Nullable ResourceKey<Level> newDimension)
    {
        atlas = newAtlas;
        dimension = newDimension;
        layer = AtlasLayers.SURFACE;
        TILES.clear();
    }

    public static void receive(AtlasPayloads.Tiles tiles)
    {
        if (!tiles.atlas().equals(atlas) || !tiles.dimension().equals(dimension)) return;
        if (tiles.replace())
        {
            layer = tiles.layer();
            TILES.clear();
        }
        else if (tiles.layer() != layer) return;
        for (AtlasPayloads.Tile tile : tiles.tiles()) TILES.put(tile.chunk(), tile.colors());
    }

    // The packed colour at a block column, or 0 (not explored)
    public static int color(int x, int z)
    {
        byte[] tile = TILES.get(AtlasSavedData.chunkKey(x >> 4, z >> 4));
        return tile == null ? 0 : tile[(z & 15) * 16 + (x & 15)] & 0xFF;
    }
}
