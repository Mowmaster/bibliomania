package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mowmaster.bibliomania.Atlas.AtlasData;
import com.mowmaster.bibliomania.Atlas.AtlasItem;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.List;

// The Atlas's points of interest within RANGE blocks get a white outline of their block's shape in the world, while
// the Atlas's map is shown (AtlasHud#shown: held, or anywhere with Reading Goggles). Done the 26.x way, like
// NeoForge's own debug outlines: what to draw is gathered while the level's render state is extracted (kept in it
// under a ContextKey), then drawn when custom geometry is submitted (submitShapeOutline, offset by the camera).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID, value = Dist.CLIENT)
public final class AtlasOutlines
{
    private AtlasOutlines() {}

    private static final double RANGE = 16;
    private static final int WHITE = 0xFFFFFFFF;

    // One outline: where, and the block's shape there (relative to its corner)
    private record Outline(BlockPos pos, VoxelShape shape) {}

    // ContextKey: a typed key for data a mod adds to the level render state
    private static final ContextKey<List<Outline>> OUTLINES =
            new ContextKey<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "atlas_outlines"));

    @SubscribeEvent
    public static void onExtract(ExtractLevelRenderStateEvent event)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        ClientLevel level = event.getLevel();
        if (player == null || level == null) return;
        ItemStack atlas = AtlasHud.shown(player);
        if (atlas.isEmpty()) return;
        List<Outline> outlines = new ArrayList<>();
        for (AtlasData.Marker marker : AtlasItem.data(atlas).markers())
        {
            if (marker.kind() != AtlasData.Kind.POINT || !marker.dimension().equals(level.dimension())) continue;
            if (marker.pos().distToCenterSqr(player.position()) > RANGE * RANGE) continue;
            VoxelShape shape = level.getBlockState(marker.pos()).getShape(level, marker.pos());
            // A block with no shape (gone, or air now): its whole space
            outlines.add(new Outline(marker.pos(), shape.isEmpty() ? Shapes.block() : shape));
        }
        if (!outlines.isEmpty()) event.getRenderState().setRenderData(OUTLINES, outlines);
    }

    @SubscribeEvent
    public static void onSubmit(SubmitCustomGeometryEvent event)
    {
        List<Outline> outlines = event.getLevelRenderState().getRenderData(OUTLINES);
        if (outlines == null) return;
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        // The line width the game uses for the window's size (as NeoForge's debug outlines do)
        float lineWidth = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        for (Outline outline : outlines)
        {
            Vec3 offset = Vec3.atLowerCornerOf(outline.pos()).subtract(camera);
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            event.getSubmitNodeCollector().submitShapeOutline(poseStack, outline.shape(), RenderTypes.lines(), WHITE, lineWidth, false);
            poseStack.popPose();
        }
    }
}
