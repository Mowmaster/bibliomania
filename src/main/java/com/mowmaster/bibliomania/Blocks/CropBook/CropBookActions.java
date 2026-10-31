package com.mowmaster.bibliomania.Blocks.CropBook;

import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// What a Farmer's Almanac does, shared by the placed book and the item.
public final class CropBookActions
{
    private CropBookActions() {}

    // ---- Putting things in ----

    // The book with `stack` added (one of it), or null if it doesn't fit now. Soil first (any block with a
    // bibliomania:crop_soils entry), then a seed that grows on that soil.
    public static @Nullable CropContents insert(CropContents contents, ItemStack stack)
    {
        if (stack.isEmpty()) return null;
        // A boost item (nether star) goes in at any time
        if (ServerConfig.isCropBoostItem(stack)) return contents.withBoost();
        if (contents.soil().isEmpty())
        {
            return stack.getItem() instanceof BlockItem blockItem && CropSoils.isSoil(blockItem.getBlock())
                    ? contents.withSoil(blockItem.getBlock()) : null;
        }
        return contents.canPlant(stack.getItem()) ? contents.withSeed(stack.getItem()) : null;
    }

    // What taking out gives back: the seed (losing the growth so far), or with no seed the soil
    public static ItemStack topItem(CropContents contents)
    {
        if (contents.seed().isPresent()) return new ItemStack(contents.seed().get());
        return contents.soil().map(block -> new ItemStack(block)).orElse(ItemStack.EMPTY);
    }

    // ---- Growing ----

    public static boolean isBoneMeal(ItemStack stack)
    {
        return stack.is(Items.BONE_MEAL);
    }

    // The chance a growth chance works: ServerConfig.cropGrowChance() (a third by default) times the soil's speed,
    // raised by each boost (+10% each by default); at most certain
    public static float growChance(CropContents contents)
    {
        float boost = 1f + contents.boosts() * (float) ServerConfig.cropBoostPercent() / 100f;
        return Math.min(1f, ServerConfig.cropGrowChance() * contents.speed() * boost);
    }

    // Ticks between growth chances in an inventory: the config's 1 minute, shortened by each boost in turn (10% each
    // by default: 0.9 x 0.9 x ...); never under a second
    public static int inventoryInterval(CropContents contents)
    {
        double factor = Math.pow(1.0 - ServerConfig.cropBoostPercent() / 100.0, contents.boosts());
        return (int) Math.max(20, Math.round(ServerConfig.cropInventoryInterval() * factor));
    }

    // One growth chance (a random tick, or an inventory interval). Returns the book one stage further, or null if it
    // didn't grow.
    public static @Nullable CropContents tryGrow(CropContents contents, RandomSource random)
    {
        if (!contents.planted() || contents.grown()) return null;
        if (random.nextFloat() >= growChance(contents)) return null;
        return contents.withStage(contents.stage() + 1);
    }

    // Bone meal: straight to the next stage (null if nothing's growing)
    public static @Nullable CropContents boneMeal(CropContents contents)
    {
        if (!contents.planted() || contents.grown()) return null;
        return contents.withStage(contents.stage() + 1);
    }

    // ---- Harvesting ----

    // A full harvest's drops (see CropProfile#drops); the caller resets the book with contents.withStage(0)
    public static List<ItemStack> harvest(ServerLevel level, CropContents contents, BlockPos pos, ItemStack tool, @Nullable Entity harvester)
    {
        CropProfile profile = contents.profile();
        if (profile == null || !contents.grown()) return List.of();
        return profile.drops(level, pos, tool, harvester);
    }
}
