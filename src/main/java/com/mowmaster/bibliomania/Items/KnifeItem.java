package com.mowmaster.bibliomania.Items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

// A knife: a small tool (set up through Item.Properties#tool in ItemRegistry) that stays in the crafting grid when
// used in a recipe, losing 1 durability each time, and breaks when it runs out.
// In 1.20 this was a DiggerItem; since 1.21.5 tools are plain Items with TOOL/WEAPON data components instead.
public class KnifeItem extends Item
{
    public KnifeItem(Properties properties)
    {
        super(properties);
    }

    // What's left in the grid after crafting. NeoForge's version of this takes the actual stack (an ItemInstance),
    // so we can copy its damage. Since 26.1 the result is an ItemStackTemplate (an immutable item + count + data).
    @Override
    public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance)
    {
        if (!(instance instanceof ItemStack stack)) return new ItemStackTemplate(this, 1);

        ItemStack used = stack.copyWithCount(1);
        used.setDamageValue(used.getDamageValue() + 1);
        if (used.getDamageValue() >= used.getMaxDamage()) return null; // worn out: nothing comes back
        return ItemStackTemplate.fromNonEmptyStack(used);
    }
}
