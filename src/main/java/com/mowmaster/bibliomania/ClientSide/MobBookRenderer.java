package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.MobBook.IMobBookDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a placed mob book's mob spinning above it, with the mob's own model (like a mob spawner does). The block
// entity (IMobBookDisplay) picks the mob and its scale: a Bestiary shows a growing baby during a breeding cooldown,
// a Book of Essence grows its mob as the essence fills. After a kill there's a brief red flash (the same red as a hurt
// mob: it sets hurtTime on the display mob).
// The mob drawn is a fake copy kept by the block entity; the entity renderer turns it into a render state each frame.
// (A candidate for MowLib later, as a SpinningEntityRenderer next to its SpinningItemRenderer.)
// Generic: <T extends BlockEntity & IMobBookDisplay> means "any block entity that also implements IMobBookDisplay",
// so the same renderer draws the Bestiary and the Book of Essence (registered once for each in BibliomaniaClient).
public class MobBookRenderer<T extends BlockEntity & IMobBookDisplay> implements BlockEntityRenderer<T, MobBookRenderer.State>
{
    // Degrees per tick, the same as the item books
    private static final float SPIN_SPEED = 2f;
    // About how tall/wide the mob is drawn, in blocks (big mobs are shrunk to fit, like in a spawner)
    private static final float DISPLAY_SIZE = 0.6f;
    // How long the red flash lasts, in ticks (vanilla's hurt flash is 10)
    private static final int FLASH_TICKS = 10;

    public MobBookRenderer(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(T book, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(book, state, partialTick, cameraPos, crumblingOverlay);
        state.mob = null;
        Level level = book.getLevel();
        if (level == null) return;
        long now = level.getGameTime();
        Entity mob = book.getDisplayMob();
        EntityType<?> type = book.getDisplayType();
        if (mob == null || type == null) return;

        // The hurt flash: a living mob with hurtTime above 0 is drawn tinted red
        if (mob instanceof LivingEntity living)
        {
            long sinceKill = now - book.getLastKill();
            living.hurtTime = sinceKill >= 0 && sinceKill < FLASH_TICKS ? (int) (FLASH_TICKS - sinceKill) : 0;
        }

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        state.mob = dispatcher.extractEntity(mob, partialTick);

        // Fit by the full-grown size, so a baby and the adult line up; then the book's own scale on top
        EntityDimensions size = type.getDimensions();
        state.scale = DISPLAY_SIZE / Math.max(1f, Math.max(size.width(), size.height())) * book.getDisplayScale();

        state.height = (book.getBlockState().getValue(BookBlock.THICKNESS) + 2) / 16.0;
        state.angle = ((now % 360) + partialTick) * SPIN_SPEED % 360f;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera)
    {
        if (state.mob == null) return;
        poseStack.pushPose();
        // Standing on the pages, in the middle of the block, turning around its own centre
        poseStack.translate(0.5, state.height, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(state.angle));
        poseStack.scale(state.scale, state.scale, state.scale);
        // The same call a mob spawner uses: draws the render state at the pose's origin
        Minecraft.getInstance().getEntityRenderDispatcher().submit(state.mob, camera, 0.0, 0.0, 0.0, poseStack, submitNodeCollector);
        poseStack.popPose();
    }

    // The mob reaches above the block, so it must still be drawn when only it is on screen (NeoForge culls by this box)
    @Override
    public AABB getRenderBoundingBox(T book)
    {
        BlockPos pos = book.getBlockPos();
        return new AABB(pos.getX() - 1.0, pos.getY(), pos.getZ() - 1.0, pos.getX() + 2.0, pos.getY() + 2.0, pos.getZ() + 2.0);
    }

    public static class State extends BlockEntityRenderState
    {
        @Nullable EntityRenderState mob;
        float scale;
        double height;
        float angle;
    }
}
