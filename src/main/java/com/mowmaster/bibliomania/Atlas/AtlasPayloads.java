package com.mowmaster.bibliomania.Atlas;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The Atlas's network messages:
// - Tiles (server -> client): explored map tiles of one Atlas in one dimension and layer (AtlasLayers): the newly
//   explored ones as the player walks, or (`replace`: the client drops what it had) all the ones near them, when asked
//   or when their layer changes
// - Request (client -> server): "send me this Atlas's explored tiles near me" (when the Atlas or the dimension
//   changes, or after walking a long way). The server picks the layer: where the player is.
public final class AtlasPayloads
{
    private AtlasPayloads() {}

    // How far (in chunks, each way) a request sends tiles: the map's widest view (64 pixels x 8 blocks) and some
    private static final int REQUEST_RADIUS = 18;

    public record Tile(long chunk, byte[] colors)
    {
        public static final StreamCodec<ByteBuf, Tile> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Tile::chunk,
                ByteBufCodecs.BYTE_ARRAY, Tile::colors,
                Tile::new);
    }

    public record Tiles(UUID atlas, ResourceKey<Level> dimension, int layer, boolean replace, List<Tile> tiles) implements CustomPacketPayload
    {
        public static final Type<Tiles> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "atlas_tiles"));
        // INT, not VAR_INT: the surface layer is Integer.MIN_VALUE, which as a var int would take 5 bytes anyway
        public static final StreamCodec<ByteBuf, Tiles> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Tiles::atlas,
                ResourceKey.streamCodec(Registries.DIMENSION), Tiles::dimension,
                ByteBufCodecs.INT, Tiles::layer,
                ByteBufCodecs.BOOL, Tiles::replace,
                Tile.STREAM_CODEC.apply(ByteBufCodecs.list()), Tiles::tiles,
                Tiles::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record Request(UUID atlas, int chunkX, int chunkZ) implements CustomPacketPayload
    {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "atlas_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Request::atlas,
                ByteBufCodecs.VAR_INT, Request::chunkX,
                ByteBufCodecs.VAR_INT, Request::chunkZ,
                Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // ---- Server ----

    // Only an Atlas the player carries; the server works out their layer and sends what's explored round them
    public static void handleRequest(Request request, IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!AtlasItem.carries(player, request.atlas())) return;
        AtlasExplorer.layer(level, player, request.atlas(), true);
    }

    // All an Atlas's explored tiles in a layer within REQUEST_RADIUS chunks of a chunk, replacing what the client has
    public static Tiles near(ServerLevel level, UUID atlas, int layer, int chunkX, int chunkZ)
    {
        Map<Long, byte[]> tiles = AtlasSavedData.get(level).tiles(atlas, layer);
        List<Tile> near = new ArrayList<>();
        for (Map.Entry<Long, byte[]> entry : tiles.entrySet())
        {
            if (Math.abs(AtlasSavedData.chunkX(entry.getKey()) - chunkX) > REQUEST_RADIUS
                    || Math.abs(AtlasSavedData.chunkZ(entry.getKey()) - chunkZ) > REQUEST_RADIUS) continue;
            near.add(new Tile(entry.getKey(), entry.getValue().clone()));
        }
        return new Tiles(atlas, level.dimension(), layer, true, near);
    }

    // ---- Client ----

    public static void handleTiles(Tiles tiles, IPayloadContext context)
    {
        AtlasClientCache.receive(tiles);
    }
}
