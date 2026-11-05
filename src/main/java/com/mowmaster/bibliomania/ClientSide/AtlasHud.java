package com.mowmaster.bibliomania.ClientSide;

import com.mowmaster.mowlib.client.HudAnchor;
import com.mowmaster.mowlib.reading.ReadingSight;
import com.mowmaster.bibliomania.Atlas.AtlasClientCache;
import com.mowmaster.bibliomania.Atlas.AtlasData;
import com.mowmaster.bibliomania.Atlas.AtlasItem;
import com.mowmaster.bibliomania.Atlas.AtlasLayers;
import com.mowmaster.bibliomania.Atlas.AtlasPayloads;
import com.mowmaster.bibliomania.Atlas.AtlasUnlocks;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.ClientConfig;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import net.minecraft.world.InteractionHand;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.UUID;

// The Atlas's map, while an Atlas is held (or, wearing Reading Goggles, anywhere in the inventory: see shown). Placed
// and sized by the client config [atlas_map] (by default the top right of the screen, 64 GUI pixels):
// 64 x 64 pixels, centred on the player (north up), each pixel `scale` blocks (the Atlas's zoom), in vanilla map
// colours; unexplored ground is plain paper. Its markers on top: points of interest as their item (half size),
// banners as a little flag in their colour, lodestones as a red X; the player as a white dot. Under it, the nearest
// lodestone marker in this dimension: its distance and an arrow pointing to it.
// The tiles come from the server (AtlasClientCache): asked for when the Atlas or dimension changes, or every 8 chunks
// walked; newly explored ones arrive by themselves, and the server sends a fresh set when the player's layer changes
// (AtlasLayers: the surface, or a 32-block underground slice, its top and bottom y shown in the map's corners).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID, value = Dist.CLIENT)
public final class AtlasHud
{
    private AtlasHud() {}

    private static final Identifier LAYER = Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "atlas_map");
    private static final int MAP = 64;
    private static final int BORDER = 0xFF6B4E2A;
    private static final int PAPER = 0xFFD8C7A1;
    private static final int TEXT = 0xFFFFFFFF;
    // Text sizes: the tracker; banner names; the slice's heights in the map's corners
    private static final float TEXT_SCALE = 0.75f;
    private static final float NAME_SCALE = 0.375f;
    private static final float CORNER_SCALE = 0.5f;
    // Arrows for the tracker, one for each eighth of a turn, starting straight ahead (up) and turning clockwise
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    // Where the last tile request was made from (in chunks), to ask again after walking far
    private static int requestedX = Integer.MIN_VALUE, requestedZ = Integer.MIN_VALUE;

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event)
    {
        event.registerBelow(VanillaGuiLayers.CHAT, LAYER, AtlasHud::render);
    }

    // Keeps the tile cache for the carried Atlas and this dimension, asking the server when needed
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack atlas = shown(player);
        UUID id = atlas.isEmpty() ? null : atlas.get(DataComponentRegistry.BOOK_ID.get());
        if (id == null) return;
        if (!id.equals(AtlasClientCache.atlas) || !player.level().dimension().equals(AtlasClientCache.dimension))
        {
            AtlasClientCache.reset(id, player.level().dimension());
            requestedX = Integer.MIN_VALUE;
        }
        int chunkX = player.getBlockX() >> 4, chunkZ = player.getBlockZ() >> 4;
        if (Math.abs(chunkX - requestedX) >= 8 || Math.abs(chunkZ - requestedZ) >= 8)
        {
            requestedX = chunkX;
            requestedZ = chunkZ;
            ClientPacketDistributor.sendToServer(new AtlasPayloads.Request(id, chunkX, chunkZ));
        }
    }

    // Any Atlas unlock item (ours, or another mod's in the bibliomania:atlas_unlock data map) says what it unlocks
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        Identifier unlock = AtlasUnlocks.unlockOf(event.getItemStack());
        if (unlock == null) return;
        event.getToolTip().add(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".atlas_unlock", AtlasUnlocks.name(unlock))
                .withStyle(ChatFormatting.GRAY));
    }

    // The Atlas the map shows: wearing Reading Goggles (the helmet enchantment, or the Reading Glasses), the first one
    // anywhere in the inventory; otherwise only one in hand (main hand first). EMPTY: no map.
    public static ItemStack shown(LocalPlayer player)
    {
        if (ReadingSight.canRead(player)) return AtlasItem.carried(player);
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof AtlasItem) return held;
        }
        return ItemStack.EMPTY;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden()) return;
        ItemStack atlas = shown(player);
        if (atlas.isEmpty()) return;
        AtlasData data = AtlasItem.data(atlas);
        int scale = data.scale();
        int centerX = player.getBlockX(), centerZ = player.getBlockZ();

        // Where and how big (client config [atlas_map]): the map is drawn 64 x 64 at the origin, then the GUI's pose
        // moves it to its place and scales it to `size` (the frame, markers and tracker with it)
        int size = ClientConfig.ATLAS_SIZE.get();
        float zoom = size / (float) MAP;
        // The space it takes: the map with its frame, and the tracker line under it
        int width = size + 4, height = size + 4 + Math.round(10 * zoom);
        int x = ClientConfig.ATLAS_ANCHOR.get().x(graphics.guiWidth(), width) + ClientConfig.ATLAS_X.get();
        int y = ClientConfig.ATLAS_ANCHOR.get().y(graphics.guiHeight(), height) + ClientConfig.ATLAS_Y.get();
        x = HudAnchor.keepOnScreen(x, width, graphics.guiWidth());
        y = HudAnchor.keepOnScreen(y, height, graphics.guiHeight());
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 2, y + 2);
        graphics.pose().scale(zoom, zoom);
        int left = 0, top = 0;
        // The map's inner side (towards the middle of the screen): the slice's heights go there
        boolean labelsLeft = x + width / 2 > graphics.guiWidth() / 2;

        // Frame and paper
        graphics.fill(left - 2, top - 2, left + MAP + 2, top + MAP + 2, BORDER);
        graphics.fill(left, top, left + MAP, top + MAP, PAPER);
        // The ground: one pixel per `scale` blocks, read at the pixel's top-left block. A map the Atlas hasn't unlocked
        // (AtlasUnlocks: underground, the Nether, the End...) stays blank paper.
        boolean unlocked = AtlasUnlocks.unlocked(data, player.level().dimension(), AtlasClientCache.layer);
        for (int px = 0; unlocked && px < MAP; px++)
        {
            for (int pz = 0; pz < MAP; pz++)
            {
                int packed = AtlasClientCache.color(centerX + (px - MAP / 2) * scale, centerZ + (pz - MAP / 2) * scale);
                if (packed <= 3) continue;
                // getColorFromPackedId: vanilla's colour for a packed map colour (ARGB)
                graphics.fill(left + px, top + pz, left + px + 1, top + pz + 1, MapColor.getColorFromPackedId(packed));
            }
        }

        // Markers in this dimension that are on the map
        AtlasData.Marker nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (AtlasData.Marker marker : data.markers())
        {
            if (!marker.dimension().equals(player.level().dimension())) continue;
            if (marker.kind() == AtlasData.Kind.LODESTONE)
            {
                double distance = Math.sqrt(marker.pos().distToCenterSqr(player.position()));
                if (distance < nearestDistance)
                {
                    nearestDistance = distance;
                    nearest = marker;
                }
            }
            int mx = left + MAP / 2 + Math.floorDiv(marker.pos().getX() - centerX, scale);
            int mz = top + MAP / 2 + Math.floorDiv(marker.pos().getZ() - centerZ, scale);
            if (mx < left || mx >= left + MAP || mz < top || mz >= top + MAP) continue;
            drawMarker(graphics, minecraft.font, marker, mx, mz);
        }
        // The player: a white dot with a dark edge
        int cx = left + MAP / 2, cz = top + MAP / 2;
        graphics.fill(cx - 2, cz - 2, cx + 2, cz + 2, 0xFF000000);
        graphics.fill(cx - 1, cz - 1, cx + 1, cz + 1, 0xFFFFFFFF);

        // Underground (or in the Nether): which slice the map shows, its top y in the map's top corner and its bottom y
        // in the bottom corner, on the inner side
        if (AtlasClientCache.layer != AtlasLayers.SURFACE)
        {
            int bottom = AtlasLayers.bottom(AtlasClientCache.layer);
            drawCorner(graphics, minecraft.font, String.valueOf(bottom + AtlasLayers.SLICE - 1), labelsLeft, true, left, top);
            drawCorner(graphics, minecraft.font, String.valueOf(bottom), labelsLeft, false, left, top);
        }
        // The tracker under the map: the nearest lodestone
        if (nearest != null) drawTracker(graphics, minecraft.font, player, nearest, nearestDistance, left, top + MAP + 4);
        graphics.pose().popMatrix();
    }

    private static void drawMarker(GuiGraphicsExtractor graphics, Font font, AtlasData.Marker marker, int x, int z)
    {
        switch (marker.kind())
        {
            case POINT ->
            {
                // The block's item at half size, centred on the spot (pose: the GUI's 2D transform stack)
                graphics.pose().pushMatrix();
                graphics.pose().translate(x - 4, z - 4);
                graphics.pose().scale(0.5f, 0.5f);
                graphics.item(new ItemStack(marker.icon()), 0, 0);
                graphics.pose().popMatrix();
            }
            case BANNER ->
            {
                // A little flag: a dark pole and the banner's colour
                graphics.fill(x - 1, z - 3, x, z + 3, 0xFF3A2A1A);
                graphics.fill(x, z - 3, x + 3, z + 1, 0xFF000000 | marker.color());
                // Its custom name under it, like a vanilla map: half size, on a see-through dark backing
                marker.name().ifPresent(name -> drawName(graphics, font, name, x, z + 4));
            }
            case LODESTONE ->
            {
                // A red X
                for (int step = -2; step <= 2; step++)
                {
                    graphics.fill(x + step, z + step, x + step + 1, z + step + 1, 0xFFD02020);
                    graphics.fill(x + step, z - step, x + step + 1, z - step + 1, 0xFFD02020);
                }
            }
        }
    }

    // Text at NAME_SCALE, centred under (x, top), on a see-through dark backing (as a vanilla map labels banners)
    private static void drawName(GuiGraphicsExtractor graphics, Font font, Component name, int x, int top)
    {
        FormattedCharSequence text = name.getVisualOrderText();
        int width = font.width(text);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, top);
        graphics.pose().scale(NAME_SCALE, NAME_SCALE);
        graphics.fill(-width / 2 - 1, -1, width / 2 + 1, font.lineHeight, 0x80000000);
        graphics.text(font, text, -width / 2, 0, TEXT, false);
        graphics.pose().popMatrix();
    }

    // "↗ 120, 64, -35 (128m)": the lodestone's coordinates; the arrow turns with where the player looks
    private static void drawTracker(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, AtlasData.Marker marker,
                                    double distance, int left, int top)
    {
        double dx = marker.pos().getX() + 0.5 - player.getX(), dz = marker.pos().getZ() + 0.5 - player.getZ();
        // The direction to it, as a yaw like the player's (0 = south, 90 = west), then relative to where they face
        float toward = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relative = Mth.wrapDegrees(toward - player.getYRot());
        int arrow = Math.floorMod(Math.round(relative / 45f), 8);
        Component line = Component.translatable("hud." + BibliomaniaReferences.MODID + ".atlas_tracker", ARROWS[arrow],
                marker.pos().getX(), marker.pos().getY(), marker.pos().getZ(), (int) Math.round(distance));
        int width = font.width(line);
        // At TEXT_SCALE, its right end at the map's right edge
        graphics.pose().pushMatrix();
        graphics.pose().translate(left + MAP, top);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
        graphics.text(font, line.getVisualOrderText(), -width, 0, TEXT, true);
        graphics.pose().popMatrix();
    }

    // Small text in one of the map's corners (inside it), on a see-through dark backing
    private static void drawCorner(GuiGraphicsExtractor graphics, Font font, String text, boolean leftSide, boolean topSide, int left, int top)
    {
        float width = font.width(text) * CORNER_SCALE, height = font.lineHeight * CORNER_SCALE;
        float x = leftSide ? left + 1 : left + MAP - 1 - width;
        float y = topSide ? top + 1 : top + MAP - height;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(CORNER_SCALE, CORNER_SCALE);
        graphics.fill(-1, -1, font.width(text) + 1, font.lineHeight - 1, 0x80000000);
        graphics.text(font, text, 0, 0, TEXT, false);
        graphics.pose().popMatrix();
    }
}
