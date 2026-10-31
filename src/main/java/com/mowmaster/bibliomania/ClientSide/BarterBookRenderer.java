package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.BarterBook.BarterBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a placed Book of Barter's piglin standing on the pages, facing the player who placed it (fixed once placed,
// like the Trader's Ledger), holding gold while the book has gold (BarterBookBlockEntity#getDisplayPiglin).
public class BarterBookRenderer implements BlockEntityRenderer<BarterBookBlockEntity, BarterBookRenderer.State>
{
    // About 0.9 blocks tall (a piglin is 1.95)
    private static final float SCALE = 0.45f;

    public BarterBookRenderer(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(BarterBookBlockEntity book, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(book, state, partialTick, cameraPos, crumblingOverlay);
        state.piglin = null;
        Entity piglin = book.getDisplayPiglin();
        if (piglin == null) return;
        state.piglin = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(piglin, partialTick);
        // Facing back the way the placer was looking (the book's FACING); atan2(x, z) is 0 for due south
        Direction towardsPlacer = book.getBlockState().getValue(BookBlock.FACING).getOpposite();
        state.angle = (float) Math.atan2(towardsPlacer.getStepX(), towardsPlacer.getStepZ());
        state.height = (book.getBlockState().getValue(BookBlock.THICKNESS) + 2) / 16f;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.piglin == null) return;
        poseStack.pushPose();
        poseStack.translate(0.5, state.height, 0.5);
        poseStack.rotate(Axis.YP.rotation(state.angle));
        poseStack.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getEntityRenderDispatcher().submit(state.piglin, camera, 0.0, 0.0, 0.0, poseStack, collector);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(BarterBookBlockEntity book)
    {
        BlockPos pos = book.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0);
    }

    public static class State extends BlockEntityRenderState
    {
        @Nullable EntityRenderState piglin;
        float angle;
        float height;
    }
}
