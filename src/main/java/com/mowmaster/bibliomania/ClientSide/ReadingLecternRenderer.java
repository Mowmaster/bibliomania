package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Lectern.ReadingLecternBlock;
import com.mowmaster.bibliomania.Blocks.Lectern.ReadingLecternBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a Reading Lectern's open page just above its top:
// - a page of items: the item, spinning slowly
// - a whole book: that book as it looks placed, smaller: its block model (open pages, thickness) and whatever its own
//   renderer draws on it (the Almanac's crop, the Ledger's villager, a storage book's sphere...). The book's
//   block entity is a client-only copy made from the book item (ReadingLecternBlockEntity#getDisplayBook); its
//   renderer is found the normal way (the dispatcher) and run here, inside this lectern's pose.
public class ReadingLecternRenderer implements BlockEntityRenderer<ReadingLecternBlockEntity, ReadingLecternRenderer.State>
{
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    // Just above the lectern model's highest point (its slanted top reaches about 14.1 px)
    private static final float TOP = 15f / 16f;
    private static final float ITEM_SCALE = 0.75f;
    private static final float BOOK_SCALE = 0.6f;
    // The model's top is tipped 22.5 degrees
    private static final float TILT = 22.5f;
    private static final float ITEM_FORWARD = 0.3f * (0.5f - 1f / 16f);
    // Degrees a tick
    private static final float SPIN_SPEED = 2f;

    private final ItemModelResolver itemModelResolver;

    public ReadingLecternRenderer(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(ReadingLecternBlockEntity lectern, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(lectern, state, partialTick, cameraPos, crumblingOverlay);
        Level level = lectern.getLevel();
        state.facing = lectern.getBlockState().getValue(ReadingLecternBlock.FACING);
        state.spin = level == null ? 0f : ((level.getGameTime() % 360) + partialTick) * SPIN_SPEED;
        state.book = null;
        state.hasBookModel = false;
        BlockEntity book = lectern.getDisplayBook();
        if (book != null)
        {
            Minecraft.getInstance().getBlockModelResolver().update(state.bookModel, book.getBlockState(), DISPLAY);
            state.hasBookModel = true;
            state.book = BookRender.extract(book, partialTick, cameraPos);
            itemModelResolver.updateForTopItem(state.item, ItemStack.EMPTY, ItemDisplayContext.GROUND, level, null, 0);
        }
        else
        {
            itemModelResolver.updateForTopItem(state.item, lectern.getShown(), ItemDisplayContext.GROUND, level, null, 0);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.hasBookModel)
        {
            poseStack.pushPose();
            // Centred on the top, turned like the lectern (the book's own model and renderer already face its FACING,
            // which is the lectern's), shrunk round the middle of its block space
            poseStack.translate(0.5, TOP, 0.5);
            // Tilted to lie on the slanted top, like a vanilla lectern's book: turned into the lectern's own frame
            // (facing north: the reader at the north, the top rising to the south), tipped so the far side rises,
            // then turned back (the book's model and renderer turn themselves by their FACING, the lectern's)
            float facingTurn = modelRotation(state.facing);
            poseStack.rotate(Axis.YP.rotationDegrees(-facingTurn));
            poseStack.rotate(Axis.XP.rotationDegrees(-TILT));
            poseStack.rotate(Axis.YP.rotationDegrees(facingTurn));
            // Half a turn: a placed book faces the way its placer looked, so as is it would face away from the reader
            poseStack.rotate(Axis.YP.rotationDegrees(180));
            poseStack.scale(BOOK_SCALE, BOOK_SCALE, BOOK_SCALE);
            poseStack.translate(-0.5, 0, -0.5);
            state.bookModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            if (state.book != null) state.book.submit(poseStack, collector, camera);
            poseStack.popPose();
            return;
        }
        if (state.item.isEmpty()) return;
        poseStack.pushPose();
        // A little below the top: a ground item's model already sits up off its origin
        // Moved towards the reader: 30% of the way from the middle to the model's front edge (1 px in from the
        // block's edge), along the way it faces
        poseStack.translate(0.5 + state.facing.getStepX() * ITEM_FORWARD, TOP - 0.15, 0.5 + state.facing.getStepZ() * ITEM_FORWARD);
        poseStack.rotate(Axis.YP.rotationDegrees(state.spin));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    // Blockstate y rotation for a facing (north as modelled, then clockwise)
    private static float modelRotation(Direction facing)
    {
        return switch (facing)
        {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    // Another renderer and its render state, kept together. The type parameter S ties them: the state is the one
    // that renderer made, so it can be handed back to it (the dispatcher works the same way).
    record BookRender<S extends BlockEntityRenderState>(BlockEntityRenderer<?, S> renderer, S renderState)
    {
        // The book's renderer, asked for its render state; null if the book has no renderer
        static <E extends BlockEntity, S extends BlockEntityRenderState> @Nullable BookRender<S> extract(E book, float partialTick, Vec3 cameraPos)
        {
            BlockEntityRenderer<E, S> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(book);
            if (renderer == null) return null;
            S renderState = renderer.createRenderState();
            renderer.extractRenderState(book, renderState, partialTick, cameraPos, null);
            return new BookRender<>(renderer, renderState);
        }

        void submit(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
        {
            renderer.submit(renderState, poseStack, collector, camera);
        }
    }

    public static class State extends BlockEntityRenderState
    {
        final ItemStackRenderState item = new ItemStackRenderState();
        final BlockModelRenderState bookModel = new BlockModelRenderState();
        boolean hasBookModel;
        @Nullable BookRender<?> book;
        Direction facing = Direction.NORTH;
        float spin;
    }
}
