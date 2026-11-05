package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Detector.StorageDetectorBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws each Storage Detector face's item flat in the window in the middle of that face (the texture's centre square).
public class StorageDetectorRenderer implements BlockEntityRenderer<StorageDetectorBlockEntity, StorageDetectorRenderer.State>
{
    private static final float PX = 1f / 16f;
    // The item's size in pixels (the window is 6 across), and how far out from the face it sits
    private static final float ITEM_SIZE = 5f;
    private static final float LIFT = 0.15f;

    private final ItemModelResolver itemModelResolver;

    public StorageDetectorRenderer(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(StorageDetectorBlockEntity detector, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(detector, state, partialTick, cameraPos, crumblingOverlay);
        for (Direction face : Direction.values())
        {
            int index = face.get3DDataValue();
            // FIXED: an item frame's flat look
            itemModelResolver.updateForTopItem(state.items[index], detector.item(face), ItemDisplayContext.FIXED, detector.getLevel(), null, index);
            // Lit like the space in front of the face (the block itself is solid, so dark inside)
            if (detector.getLevel() != null)
            {
                state.light[index] = LightCoordsUtil.getLightCoords(detector.getLevel(), detector.getBlockPos().relative(face));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        for (Direction face : Direction.values())
        {
            int index = face.get3DDataValue();
            if (state.items[index].isEmpty()) continue;
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            // A FIXED item faces north as drawn: turned to face this side (the last rotate applies first)
            switch (face)
            {
                case SOUTH -> poseStack.rotate(Axis.YP.rotationDegrees(180));
                case EAST -> poseStack.rotate(Axis.YP.rotationDegrees(-90));
                case WEST -> poseStack.rotate(Axis.YP.rotationDegrees(90));
                // Up and down: tipped to face the sky or the ground, then turned so its top points north
                case UP ->
                {
                    poseStack.rotate(Axis.XP.rotationDegrees(90));
                    poseStack.rotate(Axis.ZP.rotationDegrees(180));
                }
                case DOWN -> poseStack.rotate(Axis.XP.rotationDegrees(-90));
                default -> {}
            }
            // Out to the north face, just in front of it
            poseStack.translate(0, 0, -0.5 - LIFT * PX);
            poseStack.scale(ITEM_SIZE * PX, ITEM_SIZE * PX, 0.05f);
            state.items[index].submit(poseStack, collector, state.light[index], OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    public static class State extends BlockEntityRenderState
    {
        final ItemStackRenderState[] items = new ItemStackRenderState[6];
        final int[] light = new int[6];

        {
            for (int index = 0; index < 6; index++) items[index] = new ItemStackRenderState();
        }
    }
}
