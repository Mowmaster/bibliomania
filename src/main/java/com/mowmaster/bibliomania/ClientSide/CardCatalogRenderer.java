package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlock;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlockEntity;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogLayout;
import com.mowmaster.bibliomania.Blocks.CardCatalog.DrawerEntry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws over a Card Catalog's drawer fronts (the drawers themselves are the block model):
// - each set drawer's icon, flat on the upper part of its front, above the handle
// - a trade drawer's emerald face, and a missing item or book's cross (drawer_canceled), over the whole front
// Faces are quads in the textures' own files (not the block atlas), drawn a hair in front of the drawer; an open
// drawer's front is further out (CardCatalogLayout.OPEN_Z).
public class CardCatalogRenderer implements BlockEntityRenderer<CardCatalogBlockEntity, CardCatalogRenderer.State>
{
    private static final Identifier CANCELED = Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "textures/block/card_catalog/drawer_canceled.png");
    private static final Identifier EMERALD = Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "textures/block/card_catalog/drawer_emerald.png");
    private static final float PX = 1f / 16f;
    // How far in front of a drawer face the overlays and icons sit, in pixels
    private static final float FACE_GAP = 0.02f;
    private static final float ICON_GAP = 0.6f;

    private final ItemModelResolver itemModelResolver;

    public CardCatalogRenderer(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(CardCatalogBlockEntity catalog, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(catalog, state, partialTick, cameraPos, crumblingOverlay);
        BlockState blockState = catalog.getBlockState();
        state.facing = blockState.getValue(CardCatalogBlock.FACING);
        state.open = blockState.getValue(CardCatalogBlock.OPEN);
        state.size = catalog.size();
        for (int drawer = 0; drawer < CardCatalogBlockEntity.MAX_DRAWERS; drawer++)
        {
            DrawerEntry entry = drawer < state.size ? catalog.drawer(drawer) : null;
            state.set[drawer] = entry != null;
            state.trade[drawer] = entry != null && entry.kind() == DrawerEntry.Kind.TRADE;
            state.missing[drawer] = entry != null && catalog.isMissing(drawer);
            // FIXED: an item frame's flat look
            itemModelResolver.updateForTopItem(state.icons[drawer], entry == null ? net.minecraft.world.item.ItemStack.EMPTY : entry.icon(),
                    ItemDisplayContext.FIXED, catalog.getLevel(), null, drawer);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        // Into the model's frame (facing north): blockstate y rotation of 90 per step is a turn of -90 here
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(-rotation(state.facing)));
        poseStack.translate(-0.5, 0, -0.5);
        float[][] layout = CardCatalogLayout.drawers(state.size);
        for (int drawer = 0; drawer < state.size; drawer++)
        {
            if (!state.set[drawer]) continue;
            float[] r = layout[drawer];
            float z = (state.open == drawer + 1 ? CardCatalogLayout.OPEN_Z : CardCatalogLayout.CLOSED_Z);
            if (state.trade[drawer]) face(poseStack, collector, EMERALD, r, z - FACE_GAP, state.lightCoords);
            if (state.missing[drawer]) face(poseStack, collector, CANCELED, r, z - FACE_GAP * 2, state.lightCoords);
            icon(poseStack, collector, state.icons[drawer], r, z, state.lightCoords);
        }
        poseStack.popPose();
    }

    // The icon: centred across the drawer, in its upper part (the handle is lower down), facing out (north)
    private static void icon(PoseStack poseStack, SubmitNodeCollector collector, ItemStackRenderState icon, float[] r, float z, int light)
    {
        if (icon.isEmpty()) return;
        float width = r[2] - r[0], height = r[3] - r[1];
        float size = Math.min(width, height) * 0.45f;
        poseStack.pushPose();
        poseStack.translate((r[0] + r[2]) / 2 * PX, (r[3] - height * 0.3f) * PX, (z - ICON_GAP) * PX);
        // A FIXED item faces south as built; half a turn makes it face out of the drawer, a flat squash keeps it on
        poseStack.rotate(Axis.YP.rotationDegrees(180));
        poseStack.scale(size * PX, size * PX, 0.05f * PX * 16);
        icon.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    // A quad over a drawer's front, facing north, in `texture` (the whole file stretched over it)
    private static void face(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, float[] r, float z, int light)
    {
        float x0 = r[0] * PX, y0 = r[1] * PX, x1 = r[2] * PX, y1 = r[3] * PX, zz = z * PX;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, buffer) -> {
            // Counter-clockwise seen from the north; seen from there the texture's left (u 0) is at the high x
            vertex(buffer, pose, x0, y0, zz, 1, 1, light);
            vertex(buffer, pose, x0, y1, zz, 1, 0, light);
            vertex(buffer, pose, x1, y1, zz, 0, 0, light);
            vertex(buffer, pose, x1, y0, zz, 0, 1, light);
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light)
    {
        buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, -1);
    }

    private static float rotation(Direction facing)
    {
        return switch (facing)
        {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    public static class State extends BlockEntityRenderState
    {
        Direction facing = Direction.NORTH;
        int open;
        int size = 1;
        final boolean[] set = new boolean[CardCatalogBlockEntity.MAX_DRAWERS];
        final boolean[] trade = new boolean[CardCatalogBlockEntity.MAX_DRAWERS];
        final boolean[] missing = new boolean[CardCatalogBlockEntity.MAX_DRAWERS];
        final ItemStackRenderState[] icons = new ItemStackRenderState[CardCatalogBlockEntity.MAX_DRAWERS];

        {
            for (int drawer = 0; drawer < icons.length; drawer++) icons[drawer] = new ItemStackRenderState();
        }
    }
}
