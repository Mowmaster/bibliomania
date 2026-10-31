package com.mowmaster.bibliomania.Blocks.BreakfastBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.Book.BookBlockEntity;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;

// "The Breakfast Club": a storage book for things you can eat or drink. 8 pages ("slots"), 64 each. Placed, it works
// like any book (right-click stores, left-click takes; the open page's item spins above it), except that a slot whose
// cake is half eaten can't be emptied until the cake is finished. In the hand it can be eaten from (BreakfastBookItem).
public class BreakfastBookBlock extends BookBlock
{
    public static final int PAGES = 8;
    public static final int PAGE_CAPACITY = 64;

    // A cake slice, as vanilla's CakeBlock gives it: 2 hunger, 0.1 saturation modifier
    public static final int SLICE_NUTRITION = 2;
    public static final float SLICE_SATURATION = 0.1f;

    public BreakfastBookBlock(Properties properties)
    {
        super(properties, PAGE_CAPACITY, PAGES);
    }

    @Override
    public boolean accepts(ItemResource resource)
    {
        return isEdible(resource.toStack(1));
    }

    // Anything you eat or drink from the hand (the minecraft:consumable component: food, stews, potions, milk, honey
    // bottles...; not splash or lingering potions, which are thrown), or a block that's eaten once placed (cake)
    public static boolean isEdible(ItemStack stack)
    {
        return stack.has(DataComponents.CONSUMABLE) || stack.has(DataComponents.FOOD) || usesOf(stack) > 0;
    }

    // How many times a block item is eaten once placed: a cake (any CakeBlock, so modded cakes built on it too) has
    // MAX_BITES + 1 slices (7). 0 for anything else.
    public static int usesOf(ItemStack stack)
    {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CakeBlock ? CakeBlock.MAX_BITES + 1 : 0;
    }

    // ---- Placed: no taking out a half-eaten cake ----

    // The uses-left list travels with the book: a block entity keeps the item components it doesn't use itself and
    // gives them back when it becomes an item again, so here it's read from the block entity's components()
    private static boolean openPageHalfEaten(Level level, BlockPos pos)
    {
        if (!(level.getBlockEntity(pos) instanceof BookBlockEntity book)) return false;
        List<Integer> usesLeft = book.components().get(DataComponentRegistry.USES_LEFT.get());
        int page = book.getPage();
        return usesLeft != null && page < usesLeft.size() && usesLeft.get(page) > 0;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player)
    {
        if (openPageHalfEaten(level, pos)) refuse(level, pos);
        else super.attack(state, level, pos, player);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack,
                                       boolean willHarvest, FluidState fluid)
    {
        if (player.isCreative() && openPageHalfEaten(level, pos))
        {
            refuse(level, pos);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
    }

    private static void refuse(Level level, BlockPos pos)
    {
        if (!level.isClientSide()) level.playSound(null, pos, SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.BLOCKS, 1f, 1f);
    }
}
