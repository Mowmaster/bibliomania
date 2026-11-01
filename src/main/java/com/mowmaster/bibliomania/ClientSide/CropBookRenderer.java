package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a placed Farmer's Almanac's soil and crop on its pages, standing still (not spinning): the soil as a thin
// slab (the top quarter of the block, squeezed), the crop at its growth stage on top, both half size. Tall plants
// (sugar cane, cactus, kelp, vines) are drawn as tall as they have grown, a grown cactus with its flower on top;
// weeping vines upside down; under-water plants with water round their bottom block.
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
        state.flipped = profile != null && profile.flipped();
        state.water = profile != null && profile.water();
        state.hasBody = profile != null && profile.body() != null;
        if (profile != null && state.hasSoil)
        {
            resolver.update(state.crop, profile.stage(contents.stage()), DISPLAY);
            // Tall plants with a different block under the tip (kelp plant under kelp)
            if (profile.body() != null) resolver.update(state.body, profile.body(), DISPLAY);
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
        // Under-water plants: water round the bottom block only
        if (state.water) water(poseStack, collector, state.lightCoords);
        for (int block = 0; block < state.cropHeight; block++)
        {
            // The tip at the top; under it the body block, if the plant has one
            boolean tip = block == state.cropHeight - 1;
            BlockModelRenderState model = !tip && state.hasBody ? state.body : state.crop;
            poseStack.pushPose();
            if (state.flipped)
            {
                // Weeping vines grow down from ceilings: each block turned upside down (half a turn about the X axis,
                // round the block's centre), so they grow up from the book instead
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.rotate(Axis.XP.rotationDegrees(180));
                poseStack.translate(-0.5, -0.5, -0.5);
            }
            model.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
            poseStack.translate(0, 1, 0);
        }
        if (state.hasTopper) state.topper.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    // A see-through water cube filling the current block (the crop's bottom block), in water's own texture and its
    // usual blue (fluid textures are greyscale and tinted). Built from quads like the storage books' sphere.
    private static void water(PoseStack poseStack, SubmitNodeCollector collector, int light)
    {
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                .get(Fluids.WATER.defaultFluidState()).stillMaterial().sprite();
        int color = 0xA03F76E4; // 0xAARRGGBB: water blue, partly see-through
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float in = 0.001f, out = 0.999f; // a hair inside the block, so it doesn't flicker against the soil
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(sprite.atlasLocation()), (pose, buffer) -> {
            // Each face: four corners, counter-clockwise seen from outside, and the way it faces
            face(buffer, pose, color, light, u0, v0, u1, v1, 0, 1, 0, new float[][]{{in, out, in}, {in, out, out}, {out, out, out}, {out, out, in}});
            face(buffer, pose, color, light, u0, v0, u1, v1, 0, -1, 0, new float[][]{{in, in, in}, {out, in, in}, {out, in, out}, {in, in, out}});
            face(buffer, pose, color, light, u0, v0, u1, v1, 0, 0, -1, new float[][]{{in, in, in}, {in, out, in}, {out, out, in}, {out, in, in}});
            face(buffer, pose, color, light, u0, v0, u1, v1, 0, 0, 1, new float[][]{{in, in, out}, {out, in, out}, {out, out, out}, {in, out, out}});
            face(buffer, pose, color, light, u0, v0, u1, v1, -1, 0, 0, new float[][]{{in, in, in}, {in, in, out}, {in, out, out}, {in, out, in}});
            face(buffer, pose, color, light, u0, v0, u1, v1, 1, 0, 0, new float[][]{{out, in, in}, {out, out, in}, {out, out, out}, {out, in, out}});
        });
    }

    private static void face(VertexConsumer buffer, PoseStack.Pose pose, int color, int light, float u0, float v0, float u1, float v1,
                             float nx, float ny, float nz, float[][] corners)
    {
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int corner = 0; corner < 4; corner++)
        {
            float[] at = corners[corner];
            buffer.addVertex(pose, at[0], at[1], at[2]).setColor(color).setUv(uv[corner][0], uv[corner][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
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
        final BlockModelRenderState body = new BlockModelRenderState();
        boolean hasBody;
        boolean flipped;
        boolean water;
        boolean hasSoil;
        boolean hasTopper;
        boolean pod;
        int cropHeight;
        float height;
    }
}
