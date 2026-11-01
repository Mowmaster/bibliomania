package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Requestor.PatternHolder;
import com.mowmaster.bibliomania.Blocks.Requestor.RequestPattern;
import com.mowmaster.bibliomania.Blocks.Requestor.RequestorGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.LightCoordsUtil;
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
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a Patterned Storage Requestor's or Storage Dispenser's pattern on its grid face: each set slot's item lying
// flat in its cell, and its count (over 1) small in the cell's corner. The dispenser's one item sits in the middle.
// Everything is placed in the model's own frame (built facing south, the grid on top, see RequestorGrid), then turned
// the way the blockstate turns the model.
// <T extends BlockEntity & PatternHolder>: one renderer class for both block entity types; T is "a BlockEntity that
// is also a PatternHolder" (an intersection type)
public class RequestorRenderer<T extends BlockEntity & PatternHolder> implements BlockEntityRenderer<T, RequestorRenderer.State>
{
    private static final float PX = 1f / 16f;
    // Item size in pixels: the grid's cells, or the dispenser's one item
    private static final float CELL_ITEM = 2.6f;
    private static final float SINGLE_ITEM = 7f;
    // Count text: this many pixels tall (the font's lines are 9 units)
    private static final float TEXT_HEIGHT = 1.6f;
    private static final int MAX_SLOTS = 9;

    private final ItemModelResolver itemModelResolver;

    public RequestorRenderer(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(T holder, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(holder, state, partialTick, cameraPos, crumblingOverlay);
        state.facing = holder.getBlockState().getValue(DispenserBlock.FACING);
        // The light at the block itself is what's inside a solid block: dark. The grid's items sit on its face, so
        // they're lit like the space in front of that face (the same light the face itself gets).
        // LevelRenderer.getLightCoords: block and sky light packed into one int, as renderers want it
        if (holder.getLevel() != null)
        {
            state.lightCoords = LightCoordsUtil.getLightCoords(holder.getLevel(), holder.getBlockPos().relative(RequestorGrid.gridFace(state.facing)));
        }
        RequestPattern pattern = holder.pattern();
        state.slots = pattern.size();
        for (int slot = 0; slot < MAX_SLOTS; slot++)
        {
            state.counts[slot] = pattern.count(slot);
            // FIXED: an item frame's flat look
            itemModelResolver.updateForTopItem(state.items[slot], pattern.item(slot), ItemDisplayContext.FIXED, holder.getLevel(), null, slot);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        int[] rotation = RequestorGrid.rotation(state.facing);
        poseStack.pushPose();
        // Into the model's frame: the blockstate turns by y then x, clockwise (negative), round the block's centre
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(-rotation[1]));
        poseStack.rotate(Axis.XP.rotationDegrees(-rotation[0]));
        poseStack.translate(-0.5, -0.5, -0.5);
        Font font = Minecraft.getInstance().font;
        for (int slot = 0; slot < state.slots; slot++)
        {
            if (state.items[slot].isEmpty()) continue;
            float x, z, size;
            if (state.slots == 1)
            {
                x = RequestorGrid.SINGLE_X;
                z = RequestorGrid.SINGLE_Z;
                size = SINGLE_ITEM;
            }
            else
            {
                x = RequestorGrid.CELL_X[slot % 3];
                z = RequestorGrid.CELL_Z[slot / 3];
                size = CELL_ITEM;
            }
            item(poseStack, collector, state.items[slot], x, z, size, state.lightCoords);
            if (state.counts[slot] > 1) count(poseStack, collector, font, state.counts[slot], x + size / 2, z + size / 2, state.lightCoords);
        }
        poseStack.popPose();
    }

    // One item lying on the top face, centred at (x, z) in pixels, its top towards the back (north) so it reads the
    // right way up from the front. A FIXED item faces north (-z) as drawn: turned a quarter round x it faces up, then
    // half round z so its top points north.
    private static void item(PoseStack poseStack, SubmitNodeCollector collector, ItemStackRenderState item, float x, float z, float size, int light)
    {
        poseStack.pushPose();
        poseStack.translate(x * PX, 1f + 0.3f * PX, z * PX);
        poseStack.rotate(Axis.XP.rotationDegrees(90));
        poseStack.rotate(Axis.ZP.rotationDegrees(180));
        poseStack.scale(size * PX, size * PX, 0.05f * PX * 16);
        item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    // A count, lying on the top face just above the items, its bottom right corner at (x, z) in pixels. Text is laid
    // out across x and down y, facing +z: a quarter turn back round x lays it face up with its top to the north; the
    // y scale is negative because text runs downwards.
    private static void count(PoseStack poseStack, SubmitNodeCollector collector, Font font, int count, float x, float z, int light)
    {
        FormattedCharSequence text = Component.literal(Integer.toString(count)).getVisualOrderText();
        float scale = TEXT_HEIGHT * PX / font.lineHeight;
        poseStack.pushPose();
        poseStack.translate(x * PX, 1f + 0.5f * PX, z * PX);
        poseStack.rotate(Axis.XP.rotationDegrees(-90));
        poseStack.scale(scale, -scale, scale);
        // POLYGON_OFFSET: drawn a touch in front of what's under it (like sign text), so it never flickers
        collector.submitText(poseStack, -font.width(text), -font.lineHeight, text, false, Font.DisplayMode.POLYGON_OFFSET,
                light, 0xFFFFFFFF, 0, 0);
        poseStack.popPose();
    }

    public static class State extends BlockEntityRenderState
    {
        Direction facing = Direction.SOUTH;
        int slots;
        final int[] counts = new int[MAX_SLOTS];
        final ItemStackRenderState[] items = new ItemStackRenderState[MAX_SLOTS];

        {
            for (int slot = 0; slot < MAX_SLOTS; slot++) items[slot] = new ItemStackRenderState();
        }
    }
}
