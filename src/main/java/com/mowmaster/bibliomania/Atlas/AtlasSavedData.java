package com.mowmaster.bibliomania.Atlas;

import com.mojang.serialization.Codec;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// The explored maps of every Atlas, in one dimension: for each Atlas (by its bibliomania:book_id), each layer
// (AtlasLayers: the surface, or a 32-block underground slice), the chunks it has seen, each a 16x16 tile of vanilla
// map colours (packed: colour id * 4 + shade, as vanilla maps keep them; 0 is "not explored"). Kept by the level
// (each dimension its own), saved in <world>/dimensions/<namespace>/<path>/data/bibliomania/atlas_maps.dat.
public class AtlasSavedData extends SavedData
{
    // Map keys must be strings: a chunk's long as text. A tile is saved as a byte array (Codec.BYTE_BUFFER).
    private static final Codec<Long> CHUNK_KEY = Codec.STRING.xmap(Long::parseLong, String::valueOf);
    private static final Codec<byte[]> TILE = Codec.BYTE_BUFFER.xmap(buffer -> {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }, ByteBuffer::wrap);
    private static final Codec<Integer> LAYER_KEY = Codec.STRING.xmap(Integer::parseInt, String::valueOf);
    private static final Codec<Map<UUID, Map<Integer, Map<Long, byte[]>>>> ATLASES =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(LAYER_KEY, Codec.unboundedMap(CHUNK_KEY, TILE)));
    private static final Codec<AtlasSavedData> CODEC = ATLASES.xmap(AtlasSavedData::new, data -> data.atlases);

    // SavedDataType: how the level's data storage finds, makes and saves it
    public static final SavedDataType<AtlasSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "atlas_maps"), AtlasSavedData::new, CODEC);

    private final Map<UUID, Map<Integer, Map<Long, byte[]>>> atlases = new HashMap<>();

    public AtlasSavedData()
    {
    }

    // Loaded: copied into mutable maps (what a codec gives back may not be changeable)
    private AtlasSavedData(Map<UUID, Map<Integer, Map<Long, byte[]>>> loaded)
    {
        loaded.forEach((id, layers) -> {
            Map<Integer, Map<Long, byte[]>> copy = new HashMap<>();
            layers.forEach((layer, tiles) -> copy.put(layer, new HashMap<>(tiles)));
            atlases.put(id, copy);
        });
    }

    // A chunk as one long (x in the low 32 bits, z in the high), the same layout vanilla uses; our own so it doesn't
    // depend on ChunkPos (a record since 26.1, its packing methods renamed)
    public static long chunkKey(int chunkX, int chunkZ)
    {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static int chunkX(long key)
    {
        return (int) key;
    }

    public static int chunkZ(long key)
    {
        return (int) (key >>> 32);
    }

    public static AtlasSavedData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // Copies one Atlas's explored map (every layer) to another id. The copy is its own from then on: the tiles are
    // cloned, so exploring with either doesn't change the other.
    public void copy(UUID from, UUID to)
    {
        Map<Integer, Map<Long, byte[]>> source = atlases.get(from);
        if (source == null || source.isEmpty()) return;
        Map<Integer, Map<Long, byte[]>> copied = new HashMap<>();
        source.forEach((layer, tiles) -> {
            Map<Long, byte[]> layerCopy = new HashMap<>();
            tiles.forEach((chunk, tile) -> layerCopy.put(chunk, tile.clone()));
            copied.put(layer, layerCopy);
        });
        atlases.put(to, copied);
        setDirty();
    }

    // An Atlas's tiles in one layer (an empty map if it has none yet)
    public Map<Long, byte[]> tiles(UUID atlas, int layer)
    {
        return atlases.computeIfAbsent(atlas, id -> new HashMap<>()).computeIfAbsent(layer, key -> new HashMap<>());
    }

    // The tile of a chunk in a layer, made if it doesn't exist
    public byte[] tile(UUID atlas, int layer, long chunk)
    {
        return tiles(atlas, layer).computeIfAbsent(chunk, key -> new byte[256]);
    }
}
