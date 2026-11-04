package com.mowmaster.bibliomania.Atlas;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Maps the ground round every player carrying an Atlas (anywhere in their inventory; the first one found), on the
// server. Every dimension works the same way: which map is drawn is the player's layer (AtlasLayers), the surface or
// the 32-block underground slice they're in (the Nether is always sliced: its bedrock roof is "the surface" there).
// Only maps the Atlas has unlocked are explored (AtlasUnlocks).
// - the surface, twice a second: every column within the radius (ServerConfig atlas exploreRadius) gets the map
//   colour of its top block
// - a slice, once a second (it reads up to 32 blocks per column): every column gets its highest floor in the slice
// Each column is shaded by whether it's higher or lower than the column to its north (as vanilla maps do). Changed
// tiles are saved (AtlasSavedData) and sent to that player (AtlasPayloads.Tiles). When their layer changes (or the
// Atlas, or the dimension), the explored tiles round them in the new layer are sent first.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class AtlasExplorer
{
    private AtlasExplorer() {}

    private static final int SURFACE_TICKS = 10;
    private static final int SLICE_TICKS = 20;

    // What each player was last shown: which Atlas, in which dimension, which layer (by player UUID)
    private record Shown(UUID atlas, ResourceKey<Level> dimension, int layer) {}
    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % SURFACE_TICKS != 0) return;
        ItemStack atlas = AtlasItem.carried(player);
        if (atlas.isEmpty()) return;
        AtlasItem.finishCopy(level, atlas);
        UUID id = AtlasItem.id(atlas);
        int layer = layer(level, player, id, false);
        if (layer != AtlasLayers.SURFACE && player.tickCount % SLICE_TICKS != 0) return;
        if (!AtlasUnlocks.unlocked(AtlasItem.data(atlas), level.dimension(), layer)) return;
        explore(level, player, id, layer);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        SHOWN.remove(event.getEntity().getUUID());
    }

    // The player's layer now; if it changed (or they're looking at another Atlas or dimension), or `resend` (the
    // client asked: AtlasPayloads.Request), the explored tiles round them in it are sent, and the client's map starts
    // over with them
    public static int layer(ServerLevel level, ServerPlayer player, UUID atlas, boolean resend)
    {
        Shown last = SHOWN.get(player.getUUID());
        boolean same = last != null && last.atlas().equals(atlas) && last.dimension().equals(level.dimension());
        int layer = AtlasLayers.layerAt(level, player.blockPosition(), same ? last.layer() : null);
        if (resend || !same || layer != last.layer())
        {
            SHOWN.put(player.getUUID(), new Shown(atlas, level.dimension(), layer));
            PacketDistributor.sendToPlayer(player, AtlasPayloads.near(level, atlas, layer, player.getBlockX() >> 4, player.getBlockZ() >> 4));
        }
        return layer;
    }

    private static void explore(ServerLevel level, ServerPlayer player, UUID id, int layer)
    {
        AtlasSavedData data = AtlasSavedData.get(level);
        int radius = ServerConfig.atlasExploreRadius();
        int centerX = player.getBlockX(), centerZ = player.getBlockZ();
        // A slice's floors, read once each: floors[dx][dz + 1] for dz from -radius - 1 (the row north of the circle,
        // for shading) to radius
        int[][] floors = layer == AtlasLayers.SURFACE ? null : new int[radius * 2 + 1][radius * 2 + 2];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (floors != null)
        {
            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dz = -radius - 1; dz <= radius; dz++)
                {
                    int x = centerX + dx, z = centerZ + dz;
                    floors[dx + radius][dz + radius + 1] = level.isLoaded(pos.set(x, 0, z))
                            ? AtlasLayers.floor(level, x, z, layer, pos) : AtlasLayers.NO_FLOOR;
                }
            }
        }
        // LinkedHashSet: each changed chunk once, in the order found
        Set<Long> changed = new LinkedHashSet<>();
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                if (dx * dx + dz * dz > radius * radius) continue;
                int x = centerX + dx, z = centerZ + dz;
                if (!level.isLoaded(pos.set(x, 0, z))) continue;
                byte color = floors == null ? AtlasLayers.surfaceColor(level, x, z, pos)
                        : AtlasLayers.sliceColor(level, x, z, floors[dx + radius][dz + radius + 1], floors[dx + radius][dz + radius], pos);
                long chunk = AtlasSavedData.chunkKey(x >> 4, z >> 4);
                byte[] tile = data.tile(id, layer, chunk);
                int index = (z & 15) * 16 + (x & 15);
                if (tile[index] == color) continue;
                tile[index] = color;
                changed.add(chunk);
            }
        }
        if (changed.isEmpty()) return;
        data.setDirty();
        Map<Long, byte[]> tiles = data.tiles(id, layer);
        List<AtlasPayloads.Tile> sent = new ArrayList<>();
        for (long chunk : changed) sent.add(new AtlasPayloads.Tile(chunk, tiles.get(chunk).clone()));
        PacketDistributor.sendToPlayer(player, new AtlasPayloads.Tiles(id, level.dimension(), layer, false, sent));
    }
}
