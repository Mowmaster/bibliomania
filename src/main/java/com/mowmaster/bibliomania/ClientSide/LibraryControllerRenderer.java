package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlock;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.mowlib.paging.IPagedStorageItem;
import com.mowmaster.mowlib.paging.PagedContents;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// The Library Controller's book: standing upright on the half-slab base (the block model), the way books stand on a
// Bookcase (display context NONE: the book's model as built, upright and centred on its origin; the mod's books pick
// their thickest shelf model there), turning slowly. Its "up" is the block's FACING, so on a wall it sticks out of
// the wall.
public class LibraryControllerRenderer implements BlockEntityRenderer<LibraryControllerBlockEntity, LibraryControllerRenderer.State>
{
    private static final float PX = 1f / 16f;
    // Half size: a book (14 px tall) stands 7 px tall in the 8 px above the slab
    private static final float BOOK_SCALE = 0.5f;
    // Degrees per tick it turns (0: it stands still)
    private static final float SPIN_SPEED = 1f;

    private final ItemModelResolver itemModelResolver;

    public LibraryControllerRenderer(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(LibraryControllerBlockEntity controller, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(controller, state, partialTick, cameraPos, crumblingOverlay);
        state.facing = controller.getBlockState().getValue(LibraryControllerBlock.FACING);
        state.spin = controller.getLevel() == null ? 0f : ((controller.getLevel().getGameTime() % 360L) + partialTick) * SPIN_SPEED;
        itemModelResolver.updateForTopItem(state.book, fullBook(controller.getDisplayBook()), ItemDisplayContext.NONE, controller.getLevel(), null, 0);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.book.isEmpty()) return;
        poseStack.pushPose();
        // Round the block's centre: turn so "up" is FACING (getRotation: the turn that takes UP to that direction)
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.rotate(state.facing.getRotation());
        // The book's middle: 3.5 px above the slab's top (the block's middle), so its bottom stands on the slab
        poseStack.translate(0, (7 * BOOK_SCALE +2) * PX, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(state.spin));
        poseStack.scale(BOOK_SCALE, BOOK_SCALE, BOOK_SCALE);
        state.book.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    // A copy of the book that reads as completely full, so paged books (Grass, Wool, Leather...) pick their thickest
    // model (their item model goes by MowLib's paged_fullness): one page holding a full page of stone. Only drawn.
    private static ItemStack fullBook(ItemStack book)
    {
        if (!(book.getItem() instanceof IPagedStorageItem paged)) return book;
        ItemStack full = book.copy();
        int capacity = Math.max(1, paged.pageCapacity(full));
        full.set(MowLibDataComponents.PAGED_CONTENTS.get(),
                new PagedContents(1, 0, List.of(new ResourceStack<>(ItemResource.of(Items.STONE), capacity))));
        return full;
    }

    public static class State extends BlockEntityRenderState
    {
        Direction facing = Direction.UP;
        float spin;
        final ItemStackRenderState book = new ItemStackRenderState();
    }
}
