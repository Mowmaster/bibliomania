package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.Book.BookBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Shows the open page's item spinning above the book (the book itself is a normal block model).
// 26.x renderers work in two steps: extractRenderState copies what's needed out of the block entity into a
// render state object, then submit draws from that state only. This lets the game prepare the next frame's
// states while the current one is being drawn.
public class BookRenderer implements BlockEntityRenderer<BookBlockEntity, BookRenderer.State>
{
    // Degrees per tick (2 per tick = 40 per second, the same speed as 1.20)
    private static final float SPIN_SPEED = 2f;

    private final ItemModelResolver itemModelResolver;

    public BookRenderer(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(BookBlockEntity book, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(book, state, partialTick, cameraPos, crumblingOverlay);
        // GROUND: the same model and size as a dropped item. An empty page gives an empty render state.
        ItemStack stack = book.getOpenPageStack();
        itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.GROUND, book.getLevel(), null, 0);
        state.thickness = book.getBlockState().getValue(BookBlock.THICKNESS);
        long gameTime = book.getLevel() != null ? book.getLevel().getGameTime() : 0;
        state.angle = ((gameTime % 360) + partialTick) * SPIN_SPEED % 360f;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera)
    {
        if (state.item.isEmpty()) return;
        poseStack.pushPose();
        // Just above the pages, which rise with the thickness
        poseStack.translate(0.5, (state.thickness + 4) / 16.0, 0.5);
        poseStack.scale(1.5f, 1.5f, 1.5f);
        poseStack.rotate(Axis.YP.rotationDegrees(state.angle));
        state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    public static class State extends BlockEntityRenderState
    {
        // Reused every frame rather than making a new one
        final ItemStackRenderState item = new ItemStackRenderState();
        int thickness;
        float angle;
    }
}
