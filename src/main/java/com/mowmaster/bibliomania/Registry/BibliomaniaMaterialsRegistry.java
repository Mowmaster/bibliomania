package com.mowmaster.bibliomania.Registry;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

public class BibliomaniaMaterialsRegistry
{
    /*WOOD(0, 59, 2.0F, 0.0F, 15, () -> {
    return Ingredient.of(ItemTags.PLANKS);
}),
    STONE(1, 131, 4.0F, 1.0F, 5, () -> {
    return Ingredient.of(ItemTags.STONE_TOOL_MATERIALS);
}),
    IRON(2, 250, 6.0F, 2.0F, 14, () -> {
    return Ingredient.of(Items.IRON_INGOT);
}),
    DIAMOND(3, 1561, 8.0F, 3.0F, 10, () -> {
    return Ingredient.of(Items.DIAMOND);
}),
    GOLD(0, 32, 12.0F, 0.0F, 22, () -> {
    return Ingredient.of(Items.GOLD_INGOT);
}),
    NETHERITE(4, 2031, 9.0F, 4.0F, 15, () -> {
    return Ingredient.of(Items.NETHERITE_INGOT);
});*/

    //https://github.com/vectorwing/FarmersDelight/blob/1.18.2/src/main/java/vectorwing/farmersdelight/common/registry/ModMaterials.java#L9
    public static final Tier COPPER = new Tier()
    {
        @Override
        public int getUses() {
            return 200;
        }

        @Override
        public float getSpeed() {
            return 3.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 1.5F;
        }

        @Override
        public int getLevel() {
            return 1;
        }

        @Override
        public int getEnchantmentValue() {
            return 10;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.COPPER_INGOT);
        }
    };

    public static final Tier OBSIDIAN = new Tier()
    {
        @Override
        public int getUses() {
            return 1500;
        }

        @Override
        public float getSpeed() {
            return 4.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 8.0F;
        }

        @Override
        public int getLevel() {
            return 3;
        }

        @Override
        public int getEnchantmentValue() {
            return 20;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.OBSIDIAN);
        }
    };
}
