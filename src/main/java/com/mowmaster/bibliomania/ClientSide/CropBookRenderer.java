package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.CropBook.CropContents;
import com.mowmaster.bibliomania.Blocks.CropBook.CropProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a placed Farmer's Almanac's soil and crop on its pages, standing still (not spinning): the soil as a thin
// slab (the top quarter of the block, squeezed), the crop at its growth stage on top, both half size. Sugar cane and
// cactus are drawn as tall as they have grown, a grown cactus with its flower on top.
// 26.1+ block models in renderers: a BlockModelResolver fills a BlockModelRenderState with a block state's model in
// extractRenderState, and the render state submits it in submit (see the 26.1 porting primer).
public class CropBookRenderer implements BlockEntityRenderer<CropBookBlockEntity, CropBookRenderer.State>
{
    // How the models are drawn ("as a block in the world", rather than e.g. held)
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    // Half a block wide; the soil a quarter of that tall
    private static final float SCALE = 0.5f;
    private static final float SOIL_HEIGHT = 0.25f;

    public CropBookRenderer(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(CropBookBlockEntity book, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(book, state, partialTick, cameraPos, crumblingOverlay);
        BlockModelResolver resolver = Minecraft.getInstance().getBlockModelResolver();
        CropContents contents = book.getContents();
        // Sitting on the pages (the page stack is about thickness/16 high)
        state.height = book.getBlockState().getValue(BookBlock.THICKNESS) / 16f;

        state.hasSoil = contents.soil().isPresent();
        if (state.hasSoil) resolver.update(state.soil, contents.soil().get().defaultBlockState(), DISPLAY);

        CropProfile profile = contents.profile();
        state.cropHeight = 0;
        state.hasTopper = false;
        state.pod = profile != null && profile.pod();
        if (profile != null && state.hasSoil)
        {
            resolver.update(state.crop, profile.stage(contents.stage()), DISPLAY);
            state.cropHeight = profile.height(contents.stage());
            // A cactus's flower on top once grown
            BlockState topper = profile.topper(contents.stage());
            state.hasTopper = topper != null;
            if (topper != null) resolver.update(state.topper, topper, DISPLAY);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (!state.hasSoil) return;
        poseStack.pushPose();
        // Centred on the pages: a half-size block starts a quarter in from each side
        poseStack.translate(0.5 - SCALE / 2, state.height, 0.5 - SCALE / 2);
        poseStack.scale(SCALE, SCALE, SCALE);

        // The soil: a whole block squeezed to a quarter of its height
        poseStack.pushPose();
        poseStack.scale(1f, SOIL_HEIGHT, 1f);
        state.soil.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();

        // The crop on top, one block per block of height (sugar cane)
        poseStack.translate(0, SOIL_HEIGHT, 0);
        if (state.pod)
        {
            // A pod's model hangs from the north side of its block (cocoa faces north by default). Turned a quarter
            // around the X axis (east-west), about the block's centre, that side ends up underneath: on the book.
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.rotate(Axis.XP.rotationDegrees(-90));
            poseStack.translate(-0.5, -0.5, -0.5);
        }
        for (int block = 0; block < state.cropHeight; block++)
        {
            state.crop.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.translate(0, 1, 0);
        }
        if (state.hasTopper) state.topper.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    // Tall crops reach above the block: keep drawing them when only they are on screen
    @Override
    public AABB getRenderBoundingBox(CropBookBlockEntity book)
    {
        BlockPos pos = book.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 2.5, pos.getZ() + 1.0);
    }

    public static class State extends BlockEntityRenderState
    {
        final BlockModelRenderState soil = new BlockModelRenderState();
        final BlockModelRenderState crop = new BlockModelRenderState();
        final BlockModelRenderState topper = new BlockModelRenderState();
        boolean hasSoil;
        boolean hasTopper;
        boolean pod;
        int cropHeight;
        float height;
    }
}
