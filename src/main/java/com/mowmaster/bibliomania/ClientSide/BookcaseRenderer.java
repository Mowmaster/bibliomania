package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlock;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a Bookcase's books standing on its shelves, spine out, each as thick as its item model (storage books get
// thicker as they fill: their item models do that by fullness). The books are their item models, turned upright:
// - an item book model lies flat: spine along its left edge (x 2-3), covers 12 wide (x 2-14), 14 deep (z 1-15),
//   as thick as its pages (y 0 up)
// - turned a quarter round the vertical axis, the spine faces the front (north); then a quarter round the front-to-back
//   axis, it stands up: its thickness now runs along x (from x = -8 px, the cover it lay on), 14 tall, 12 deep
// Each slot's space is a third of the 14 px between the sides; the model is scaled to fit and stood on the shelf.
public class BookcaseRenderer implements BlockEntityRenderer<BookcaseBlockEntity, BookcaseRenderer.State>
{
    // A book model scaled to 0.44: 6.2 px tall (shelves are 6.5), up to 4.4 px thick (spaces are 4.67 wide)
    private static final float BOOK_SCALE = 0.44f;
    private static final float PX = 1f / 16f;

    private final ItemModelResolver itemModelResolver;

    public BookcaseRenderer(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(BookcaseBlockEntity shelf, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(shelf, state, partialTick, cameraPos, crumblingOverlay);
        Level level = shelf.getLevel();
        BlockState blockState = shelf.getBlockState();
        state.facing = blockState.getValue(BookcaseBlock.FACING);
        BookcaseBlock block = (BookcaseBlock) blockState.getBlock();
        state.front = block.shelfFront(blockState);
        state.floorTop = block.shelfFloor(0);
        state.floorBottom = block.shelfFloor(1);
        for (int slot = 0; slot < BookcaseBlockEntity.SLOTS; slot++)
        {
            ItemStack book = shelf.getBook(slot);
            // NONE: the model as built (no hand, ground or GUI transform); the last number varies each book's seed
            itemModelResolver.updateForTopItem(state.books[slot], book, ItemDisplayContext.NONE, level, null, slot);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        // Turn with the block: blockstate y rotation of 90 per step clockwise (north 0, east 90...) is a turn of -90
        // per step about the vertical axis here
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(-modelRotation(state.facing)));
        poseStack.translate(-0.5, 0, -0.5);
        for (int slot = 0; slot < BookcaseBlockEntity.SLOTS; slot++)
        {
            ItemStackRenderState book = state.books[slot];
            if (book.isEmpty()) continue;
            float floor = slot < 3 ? state.floorTop : state.floorBottom;
            poseStack.pushPose();
            // The point where the book's standing-up model (centred at its origin, see the class comment) lands: its
            // lying cover (x = -8 px) at the slot's left edge plus a hair, its bottom (-7 px) on the floor, its spine
            // (z = -6 px) half a pixel in from the front
            poseStack.translate((BookcaseBlock.slotLeft(slot) + 0.15f) * PX + 8 * PX * BOOK_SCALE,
                    floor * PX + 7 * PX * BOOK_SCALE,
                    (state.front + 0.5f) * PX + 6 * PX * BOOK_SCALE);
            poseStack.scale(BOOK_SCALE, BOOK_SCALE, BOOK_SCALE);
            // Stand it up (thickness along x), after turning the spine to the front: the last rotate applies first
            poseStack.rotate(Axis.ZP.rotationDegrees(-90));
            poseStack.rotate(Axis.YP.rotationDegrees(-90));
            book.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

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

    public static class State extends BlockEntityRenderState
    {
        final ItemStackRenderState[] books = new ItemStackRenderState[BookcaseBlockEntity.SLOTS];
        Direction facing = Direction.NORTH;
        float front;
        float floorTop;
        float floorBottom;

        // An instance initializer: runs when a State is made, filling the array
        {
            for (int slot = 0; slot < books.length; slot++) books[slot] = new ItemStackRenderState();
        }
    }
}
