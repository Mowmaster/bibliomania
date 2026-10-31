package com.mowmaster.bibliomania.Blocks.BreakfastBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;

// "The Breakfast Club": a storage book for food and drinkable potions only. 8 pages ("slots"), 64 each. Placed, it
// works like any book (right-click stores, left-click takes; the open page's item spins above it). In the hand it
// can be eaten from (BreakfastBookItem).
public class BreakfastBookBlock extends BookBlock
{
    public static final int PAGES = 8;
    public static final int PAGE_CAPACITY = 64;

    public BreakfastBookBlock(Properties properties)
    {
        super(properties, PAGE_CAPACITY, PAGES);
    }

    @Override
    public boolean accepts(ItemResource resource)
    {
        return isEdible(resource.toStack(1));
    }

    // Food (anything with the minecraft:food component: bread, stews, honey bottles...) or a drinkable potion
    // (not splash or lingering ones)
    public static boolean isEdible(ItemStack stack)
    {
        return stack.has(DataComponents.FOOD) || stack.is(Items.POTION);
    }
}
