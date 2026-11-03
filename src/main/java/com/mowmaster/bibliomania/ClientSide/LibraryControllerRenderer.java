package com.mowmaster.bibliomania.ClientSide;

import java.util.List;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.paging.PagedContents;
import com.mowmaster.mowlib.paging.IPagedStorageItem;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.mowlib.tint.CBTintBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

// The Library Controller as an end crystal: above its base (the block model), two frames turn on a slant round each
// other, bobbing up and down, with the chosen book spinning in the middle. The frames are a texture of our own
// (controller_frame.png, greyscale), tinted with the block's dye (white when undyed); everything is drawn at full
// brightness, since the block is a light source.
public class LibraryControllerRenderer implements BlockEntityRenderer<LibraryControllerBlockEntity, LibraryControllerRenderer.State>
{
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "textures/block/library/controller_frame.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    // An end crystal's tilt: its frames turn about the (1, 0, 1) axis, set at 60 degrees
    private static final Quaternionf TILT = new Quaternionf().setAngleAxis((float) Math.toRadians(60), 0.7071f, 0f, 0.7071f);

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
        state.time = controller.getLevel() == null ? 0f : (controller.getLevel().getGameTime() % 24000L) + partialTick;
        // The crystal's own colour (region CRYSTAL; the base is the block model's, region BASE)
        int dyed = controller.getColor(LibraryControllerBlock.CRYSTAL);
        state.color = dyed == CBTintBlockEntity.UNDYED ? 0xFFFFFF : dyed;
        // NONE: the item model as built, which for the mod's books is the book standing upright (as on a Bookcase,
        // whose thick shelf models it also picks); shown full, so it's at its thickest
        itemModelResolver.updateForTopItem(state.book, fullBook(controller.getDisplayBook()), ItemDisplayContext.NONE, controller.getLevel(), null, 0);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        float spin = state.time * 3f;
        // Bobbing like an end crystal: a sine wave over time
        float bob = Mth.sin(state.time * 0.2f) * 0.06f;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.62 + bob, 0.5);

        // Outer frame
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(spin));
        poseStack.rotate(TILT);
        cube(poseStack, collector, 0.62f, state.color);
        // Inner frame, turning the other way inside it
        poseStack.rotate(Axis.YP.rotationDegrees(spin));
        poseStack.rotate(TILT);
        cube(poseStack, collector, 0.5f, state.color);
        poseStack.popPose();

        // The book, upright, turning slowly in the middle (its model is centred on its origin)
        if (!state.book.isEmpty())
        {
            poseStack.pushPose();
            poseStack.rotate(Axis.YP.rotationDegrees(-spin * 0.5f));
            poseStack.scale(0.55f, 0.55f, 0.55f);
            state.book.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
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

    // A cube `size` blocks across, centred on the pose's origin, every face showing the whole frame texture, tinted
    private static void cube(PoseStack poseStack, SubmitNodeCollector collector, float size, int color)
    {
        float h = size / 2;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(FRAME), (pose, buffer) -> {
            // Each face: four corners counter-clockwise seen from outside, and its outward normal
            face(buffer, pose, color, new float[]{-h, -h, h, h, -h, h, h, h, h, -h, h, h}, 0, 0, 1);       // south (+z)
            face(buffer, pose, color, new float[]{h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h}, 0, 0, -1);  // north (-z)
            face(buffer, pose, color, new float[]{h, -h, h, h, -h, -h, h, h, -h, h, h, h}, 1, 0, 0);       // east (+x)
            face(buffer, pose, color, new float[]{-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h}, -1, 0, 0);  // west (-x)
            face(buffer, pose, color, new float[]{-h, h, h, h, h, h, h, h, -h, -h, h, -h}, 0, 1, 0);       // up (+y)
            face(buffer, pose, color, new float[]{-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h}, 0, -1, 0);  // down (-y)
        });
    }

    // Corners in order bottom-left, bottom-right, top-right, top-left (as seen from outside) -> the texture's corners
    private static void face(VertexConsumer buffer, PoseStack.Pose pose, int color, float[] c, float nx, float ny, float nz)
    {
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int corner = 0; corner < 4; corner++)
        {
            buffer.addVertex(pose, c[corner * 3], c[corner * 3 + 1], c[corner * 3 + 2]).setColor(0xFF000000 | color)
                    .setUv(uv[corner][0], uv[corner][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    public static class State extends BlockEntityRenderState
    {
        float time;
        int color = 0xFFFFFF;
        final ItemStackRenderState book = new ItemStackRenderState();
    }
}
