package com.mowmaster.bibliomania.Blocks.Book;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mowmaster.mowlib.MowLibUtils.MowLibRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

public class BaseBookBlockEntityRender implements BlockEntityRenderer<BaseBookBlockEntity> {

    public BaseBookBlockEntityRender(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BaseBookBlockEntity p_112307_, float p_112308_, PoseStack p_112309_, MultiBufferSource p_112310_, int p_112311_, int p_112312_) {
        if (!p_112307_.isRemoved())
        {
            List<ItemStack> listed = p_112307_.getItemStacks();
            ItemStack stack = p_112307_.getItemInPedestal(p_112307_.getCurrentSlot());
            BlockPos pos = p_112307_.getPos();
            Level level = p_112307_.getLevel();


            renderItemRotating(level,p_112309_,p_112310_,stack,p_112311_,p_112312_);
        }
    }

    public static void renderItemRotating(Level worldIn, PoseStack p_112309_, MultiBufferSource p_112310_, ItemStack itemStack, int p_112311_, int p_112312_) {
        if (!itemStack.isEmpty()) {
            p_112309_.pushPose();
            p_112309_.translate(0.5D, 0.25D, 0.5D);
            p_112309_.scale(1.5F, 1.5F, 1.5F);
            long time = System.currentTimeMillis();
            float angle = (float)(time / 25L % 360L);
            p_112309_.mulPose(Axis.YP.rotationDegrees(angle));
            ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
            BakedModel baked = renderer.getModel(itemStack, worldIn, (LivingEntity)null, 0);
            renderer.render(itemStack, ItemDisplayContext.GROUND, true, p_112309_, p_112310_, p_112311_, p_112312_, baked);
            p_112309_.popPose();
        }

    }
}
