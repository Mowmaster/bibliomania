package com.mowmaster.bibliomania.Atlas;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Maps the ground round every player carrying an Atlas (anywhere in their inventory; the first one found), on the
// server, twice a second: every column within RADIUS blocks gets the vanilla map colour of its top block, shaded by
// whether it's higher or lower than the column to its north (as vanilla maps do). Changed tiles are saved
// (AtlasSavedData) and sent to that player (AtlasPayloads.Tiles).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class AtlasExplorer
{
    private AtlasExplorer() {}

    public static final int RADIUS = 32;
    private static final int EVERY_TICKS = 10;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % EVERY_TICKS != 0) return;
        ItemStack atlas = AtlasItem.carried(player);
        if (atlas.isEmpty()) return;
        UUID id = AtlasItem.id(atlas);
        explore(level, player, id);
    }

    private static void explore(ServerLevel level, ServerPlayer player, UUID id)
    {
        AtlasSavedData data = AtlasSavedData.get(level);
        int centerX = player.getBlockX(), centerZ = player.getBlockZ();
        // LinkedHashSet: each changed chunk once, in the order found
        Set<Long> changed = new LinkedHashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                int x = centerX + dx, z = centerZ + dz;
                if (!level.isLoaded(pos.set(x, 0, z))) continue;
                byte color = colorAt(level, x, z, pos);
                long chunk = AtlasSavedData.chunkKey(x >> 4, z >> 4);
                byte[] tile = data.tile(id, chunk);
                int index = (z & 15) * 16 + (x & 15);
                if (tile[index] == color) continue;
                tile[index] = color;
                changed.add(chunk);
            }
        }
        if (changed.isEmpty()) return;
        data.setDirty();
        Map<Long, byte[]> tiles = data.tiles(id);
        List<AtlasPayloads.Tile> sent = new ArrayList<>();
        for (long chunk : changed) sent.add(new AtlasPayloads.Tile(chunk, tiles.get(chunk).clone()));
        PacketDistributor.sendToPlayer(player, new AtlasPayloads.Tiles(id, level.dimension(), sent));
    }

    // A column's packed map colour (colour id * 4 + shade). Never 0 (0 means "not explored"): a column with nothing
    // to show is MapColor.NONE in its darkest shade (3).
    private static byte colorAt(ServerLevel level, int x, int z, BlockPos.MutableBlockPos pos)
    {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockState state = level.getBlockState(pos.set(x, top, z));
        MapColor color = state.getMapColor(level, pos);
        if (color == MapColor.NONE) return 3;
        int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1;
        MapColor.Brightness shade = top > north ? MapColor.Brightness.HIGH : top < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        return color.getPackedId(shade);
    }
}
