package com.mowmaster.bibliomania.Items;

import com.google.common.collect.Sets;
import com.mowmaster.bibliomania.Registry.DeferredRegisterItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

public class BaseKnifeItem extends DiggerItem {
    public BaseKnifeItem(float p_204108_, float p_204109_, Tier p_204110_, Properties p_204112_) {
        super(p_204108_, p_204109_, p_204110_, MINEABLE_WITH_KNIFE,p_204112_);
    }

    @Override
    public boolean canAttackBlock(BlockState p_41441_, Level p_41442_, BlockPos p_41443_, Player p_41444_) {
        return !p_41444_.isCreative();
    }

    @Override
    public boolean hurtEnemy(ItemStack p_40994_, LivingEntity p_40995_, LivingEntity p_40996_) {
        p_40994_.hurtAndBreak(1, p_40996_, (user) -> user.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        Set<Enchantment> ALLOWED_ENCHANTMENTS = Sets.newHashSet(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.MOB_LOOTING);
        if (ALLOWED_ENCHANTMENTS.contains(enchantment)) {
            return true;
        }
        Set<Enchantment> DENIED_ENCHANTMENTS = Sets.newHashSet(Enchantments.BLOCK_FORTUNE);
        if (DENIED_ENCHANTMENTS.contains(enchantment)) {
            return false;
        }
        return enchantment.category.canEnchant(stack.getItem());
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        if(itemStack.getItem() instanceof BaseKnifeItem knife)
        {
            return itemStack.getItem().getDefaultInstance();
        }

        return DeferredRegisterItems.KNIFE_STONE.get().getDefaultInstance();
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }


    public static final TagKey<Block> MINEABLE_WITH_KNIFE = TagKey.create(ForgeRegistries.BLOCKS.getRegistryKey(),new ResourceLocation("forge", "mineable/knife"));
}
