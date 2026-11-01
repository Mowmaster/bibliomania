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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Draws a placed Trader's Ledger, everything turned to face the player who placed it (fixed once placed), and drawn at
// DISPLAY_SCALE:
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
    // All of it together, made bigger (1.5 = 150%), so the parts keep their places relative to each other
    private static final float DISPLAY_SCALE = 1.5f;
    // A wandering trader's "workstation": a blue cushion (looked up by id; empty if it isn't there)
    private static final ItemStack CUSHION = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("minecraft", "blue_cushion"))
            .map(ItemStack::new).orElse(ItemStack.EMPTY);
    private static final float CUSHION_SCALE = 0.6f;

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
        state.cushion = false;
        Level level = book.getLevel();
        VillagerContents contents = book.getContents();
        if (level == null || !contents.hasVillager()) return;

        // Face the player who placed it: the book's FACING is the way they were looking, so everything turns to face
        // the opposite way, and stays like that. atan2(x, z) is 0 for due south (+Z), the way models face unturned.
        Direction towardsPlacer = book.getBlockState().getValue(BookBlock.FACING).getOpposite();
        state.angle = (float) Math.atan2(towardsPlacer.getStepX(), towardsPlacer.getStepZ());
        state.height = (book.getBlockState().getValue(BookBlock.THICKNESS) + 2) / 16f;

        LivingEntity villager = book.getDisplayVillager();
        if (villager != null)
        {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            state.villager = dispatcher.extractEntity(villager, partialTick);
        }
        // A wandering trader has no workstation block: it sits behind a (blue) cushion, an item since 26.3
        if (contents.wandering())
        {
            itemModelResolver.updateForTopItem(state.cushionItem, CUSHION, ItemDisplayContext.GROUND, level, null, 3);
            state.cushion = !state.cushionItem.isEmpty();
        }
        else if (contents.workstation().isPresent())
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
        if (state.villager == null && !state.hasWorkstation && !state.cushion) return;
        poseStack.pushPose();
        // From here on +Z points at the player who placed the book
        poseStack.translate(0.5, state.height, 0.5);
        poseStack.rotate(Axis.YP.rotation(state.angle));
        poseStack.scale(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE);

        // The villager at the back
        if (state.villager != null)
        {
            poseStack.pushPose();
            poseStack.translate(0, 0, -0.17);
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

        // A wandering trader's cushion where the workstation would be
        if (state.cushion) item(state.cushionItem, poseStack, collector, state.lightCoords, 0, 0, workstationZ, CUSHION_SCALE);

        // The item on offer, above the workstation
        item(state.result, poseStack, collector, state.lightCoords, 0, WORKSTATION_SIZE + 0.04f, workstationZ);
        // The price in front: one item in the middle, or two side by side
        // (x DISPLAY_SCALE = about 0.48 from the centre: still on the book)
        float priceZ = 0.32f;
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
        item(item, poseStack, collector, light, x, y, z, ITEM_SCALE);
    }

    private static void item(ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector, int light, float x, float y, float z, float scale)
    {
        if (item.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scale, scale, scale);
        item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(VillagerBookBlockEntity book)
    {
        BlockPos pos = book.getBlockPos();
        return new AABB(pos.getX() - 0.25, pos.getY(), pos.getZ() - 0.25, pos.getX() + 1.25, pos.getY() + 2.0, pos.getZ() + 1.25);
    }

    public static class State extends BlockEntityRenderState
    {
        @Nullable EntityRenderState villager;
        final BlockModelRenderState workstation = new BlockModelRenderState();
        boolean hasWorkstation;
        final ItemStackRenderState cushionItem = new ItemStackRenderState();
        boolean cushion;
        final ItemStackRenderState costA = new ItemStackRenderState();
        final ItemStackRenderState costB = new ItemStackRenderState();
        final ItemStackRenderState result = new ItemStackRenderState();
        float angle;
        float height;
    }
}
