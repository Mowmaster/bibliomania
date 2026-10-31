package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerContents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a placed Trader's Ledger: everything turned to face the camera (the player looking at it):
// - the villager standing on the pages, at the back
// - its workstation in front of it, small, with the item on offer floating above it
// - the price (one or two items) in front of the workstation; a barrier when the trade is sold out
// The villager is a client-side copy (VillagerBookBlockEntity#getDisplayVillager), drawn like the Bestiary's mobs;
// the block like the Farmer's Almanac's soil; the items like MowLib's spinning item.
public class VillagerBookRenderer implements BlockEntityRenderer<VillagerBookBlockEntity, VillagerBookRenderer.State>
{
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    // Sizes, in blocks: the villager about 0.6 tall, the workstation a quarter block, items half their dropped size
    private static final float VILLAGER_SCALE = 0.3f;
    private static final float WORKSTATION_SIZE = 0.25f;
    private static final float ITEM_SCALE = 0.5f;

    private final ItemModelResolver itemModelResolver;

    public VillagerBookRenderer(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(VillagerBookBlockEntity book, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(book, state, partialTick, cameraPos, crumblingOverlay);
        state.villager = null;
        state.hasWorkstation = false;
        Level level = book.getLevel();
        VillagerContents contents = book.getContents();
        if (level == null || !contents.hasVillager()) return;

        // Face the camera: the angle from the book's centre to the camera, around the vertical axis.
        // atan2(x, z) is 0 when the camera is due south (+Z), which is the way models face unturned.
        BlockPos pos = book.getBlockPos();
        state.angle = (float) Math.atan2(cameraPos.x - (pos.getX() + 0.5), cameraPos.z - (pos.getZ() + 0.5));
        state.height = (book.getBlockState().getValue(BookBlock.THICKNESS) + 2) / 16f;

        Villager villager = book.getDisplayVillager();
        if (villager != null)
        {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            state.villager = dispatcher.extractEntity(villager, partialTick);
        }
        if (contents.workstation().isPresent())
        {
            Minecraft.getInstance().getBlockModelResolver().update(state.workstation, contents.workstation().get().defaultBlockState(), DISPLAY);
            state.hasWorkstation = true;
        }

        // The trade on show: what it costs and what it gives; sold out shows a barrier as the price
        MerchantOffer offer = contents.currentOffer();
        ItemStack costA = ItemStack.EMPTY, costB = ItemStack.EMPTY, result = ItemStack.EMPTY;
        if (offer != null)
        {
            result = offer.getResult();
            if (offer.isOutOfStock())
            {
                costA = new ItemStack(Items.BARRIER);
            }
            else
            {
                costA = offer.getCostA();
                costB = offer.getCostB();
            }
        }
        itemModelResolver.updateForTopItem(state.costA, costA, ItemDisplayContext.GROUND, level, null, 0);
        itemModelResolver.updateForTopItem(state.costB, costB, ItemDisplayContext.GROUND, level, null, 1);
        itemModelResolver.updateForTopItem(state.result, result, ItemDisplayContext.GROUND, level, null, 2);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.villager == null && !state.hasWorkstation) return;
        poseStack.pushPose();
        // From here on +Z points at the camera
        poseStack.translate(0.5, state.height, 0.5);
        poseStack.rotate(Axis.YP.rotation(state.angle));

        // The villager at the back
        if (state.villager != null)
        {
            poseStack.pushPose();
            poseStack.translate(0, 0, -0.18);
            poseStack.scale(VILLAGER_SCALE, VILLAGER_SCALE, VILLAGER_SCALE);
            Minecraft.getInstance().getEntityRenderDispatcher().submit(state.villager, camera, 0.0, 0.0, 0.0, poseStack, collector);
            poseStack.popPose();
        }

        // The workstation in front of it (a block model's origin is its corner, so it's moved back half its size)
        float workstationZ = 0.12f;
        if (state.hasWorkstation)
        {
            poseStack.pushPose();
            poseStack.translate(-WORKSTATION_SIZE / 2, 0, workstationZ - WORKSTATION_SIZE / 2);
            poseStack.scale(WORKSTATION_SIZE, WORKSTATION_SIZE, WORKSTATION_SIZE);
            state.workstation.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // The item on offer, above the workstation
        item(state.result, poseStack, collector, state.lightCoords, 0, WORKSTATION_SIZE + 0.04f, workstationZ);
        // The price in front: one item in the middle, or two side by side
        float priceZ = 0.38f;
        if (state.costB.isEmpty())
        {
            item(state.costA, poseStack, collector, state.lightCoords, 0, 0.04f, priceZ);
        }
        else
        {
            item(state.costA, poseStack, collector, state.lightCoords, -0.1f, 0.04f, priceZ);
            item(state.costB, poseStack, collector, state.lightCoords, 0.1f, 0.04f, priceZ);
        }
        poseStack.popPose();
    }

    private static void item(ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector, int light, float x, float y, float z)
    {
        if (item.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(VillagerBookBlockEntity book)
    {
        BlockPos pos = book.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0);
    }

    public static class State extends BlockEntityRenderState
    {
        @Nullable EntityRenderState villager;
        final BlockModelRenderState workstation = new BlockModelRenderState();
        boolean hasWorkstation;
        final ItemStackRenderState costA = new ItemStackRenderState();
        final ItemStackRenderState costB = new ItemStackRenderState();
        final ItemStackRenderState result = new ItemStackRenderState();
        float angle;
        float height;
    }
}
