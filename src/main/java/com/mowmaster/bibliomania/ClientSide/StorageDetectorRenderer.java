package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mowmaster.bibliomania.Blocks.Detector.StorageDetectorBlockEntity;
import com.mowmaster.mowlib.client.CBItemRenderer;
import com.mowmaster.mowlib.client.FaceItemRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws each Storage Detector face's item flat in the window in the middle of that face (the texture's centre square),
// with MowLib's FaceItemRenderer (lit by the light in front of each face).
public class StorageDetectorRenderer extends CBItemRenderer<StorageDetectorBlockEntity, StorageDetectorRenderer.State>
{
    // The item's size in pixels (the window is 6 across), and how far out from the face it sits
    private static final float ITEM_SIZE = 5f;
    private static final float LIFT = 0.15f;

    public StorageDetectorRenderer(BlockEntityRendererProvider.Context context)
    {
        super(context);
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
        super.extractRenderState(detector, state, partialTick, cameraPos, crumblingOverlay);
        for (Direction face : Direction.values())
        {
            int index = face.get3DDataValue();
            // FIXED: an item frame's flat look
            resolve(state.items[index], detector.item(face), ItemDisplayContext.FIXED, detector.getLevel(), index);
            // The light of the space in front of the face (inside the solid block it's dark)
            if (detector.getLevel() != null) state.light[index] = LightCoordsUtil.getLightCoords(detector.getLevel(), detector.getBlockPos().relative(face));        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        for (Direction face : Direction.values())
        {
            int index = face.get3DDataValue();
            // In the middle of the face (0 right, 0 up)
            FaceItemRenderer.submit(poseStack, collector, state.items[index], face, 0, 0, ITEM_SIZE, LIFT, state.light[index]);
        }
    }

    public static class State extends BlockEntityRenderState
    {
        final ItemStackRenderState[] items = newStates(6);
        final int[] light = new int[6];
    }
}
