package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookContents;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a placed Bestiary's mob spinning above it, with the mob's own model (like a mob spawner does):
// - normally the adult
// - during a breeding cooldown, a baby that grows to adult size as the cooldown runs out
// - a brief red flash after a kill (the same red as a hurt mob: it sets hurtTime on the display mob)
// The mob drawn is a fake copy kept by the block entity; the entity renderer turns it into a render state each frame.
// (A candidate for MowLib later, as a SpinningEntityRenderer next to its SpinningItemRenderer.)
public class MobBookRenderer implements BlockEntityRenderer<MobBookBlockEntity, MobBookRenderer.State>
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
    public void extractRenderState(MobBookBlockEntity book, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(book, state, partialTick, cameraPos, crumblingOverlay);
        state.mob = null;
        Level level = book.getLevel();
        if (level == null) return;
        long now = level.getGameTime();
        MobBookContents contents = book.getContents();
        if (contents.isEmpty() || contents.type().isEmpty()) return;

        // Babies growing: show one, growing; otherwise the adult
        boolean baby = contents.growing() > 0 && contents.onCooldown(now);
        Entity mob = book.getDisplayMob(baby);
        if (mob == null) return;

        // The hurt flash: a living mob with hurtTime above 0 is drawn tinted red
        if (mob instanceof LivingEntity living)
        {
            long sinceKill = now - book.getLastKill();
            living.hurtTime = sinceKill >= 0 && sinceKill < FLASH_TICKS ? (int) (FLASH_TICKS - sinceKill) : 0;
        }

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        state.mob = dispatcher.extractEntity(mob, partialTick);

        // Fit by the adult's size, so the baby and the adult line up
        EntityDimensions size = contents.type().get().getDimensions();
        float scale = DISPLAY_SIZE / Math.max(1f, Math.max(size.width(), size.height()));
        // The baby model is about half size: scale it up from x1 to x2 as it grows
        if (baby) scale *= 1f + contents.growProgress(now);
        state.scale = scale;

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
    public AABB getRenderBoundingBox(MobBookBlockEntity book)
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
