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

import java.util.ArrayList;
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
        // A yield item (heavy core) too
        if (ServerConfig.isCropYieldItem(stack)) return contents.withYield();
        if (contents.soil().isEmpty())
        {
            return stack.getItem() instanceof BlockItem blockItem && CropSoils.isSoil(blockItem.getBlock())
                    ? contents.withSoil(blockItem.getBlock()) : null;
        }
        return contents.canPlant(stack.getItem()) ? contents.withSeed(stack.getItem()) : null;
    }

    // What taking out gives back: the seed (losing the growth so far), or with no seed the soil. Coral comes out dead
    // if the book has no water.
    public static ItemStack topItem(CropContents contents)
    {
        CropProfile profile = contents.profile();
        if (profile != null && profile.dead() != null && !contents.water()) return new ItemStack(profile.dead().getBlock());
        if (contents.seed().isPresent()) return new ItemStack(contents.seed().get());
        return contents.soil().map(block -> new ItemStack(block)).orElse(ItemStack.EMPTY);
    }

    // ---- Water ----
    // A water bucket pours water into the book (the bucket comes back empty); an empty bucket takes it back out.

    public static boolean isWaterBucket(ItemStack stack)
    {
        return stack.is(Items.WATER_BUCKET);
    }

    public static boolean isEmptyBucket(ItemStack stack)
    {
        return stack.is(Items.BUCKET);
    }

    // The book after `bucket` is used on it, or null if it does nothing (water into a watered book, or an empty
    // bucket on a dry one)
    public static @Nullable CropContents useBucket(CropContents contents, ItemStack bucket)
    {
        if (isWaterBucket(bucket) && !contents.water()) return contents.withWater(true);
        if (isEmptyBucket(bucket) && contents.water()) return contents.withWater(false);
        return null;
    }

    // What the bucket becomes: a water bucket empties, an empty bucket fills
    public static ItemStack bucketAfter(ItemStack bucket)
    {
        return new ItemStack(isWaterBucket(bucket) ? Items.BUCKET : Items.WATER_BUCKET);
    }

    // Under-water plants without water in the book don't grow (and bone meal does nothing)
    public static boolean needsWater(CropContents contents)
    {
        CropProfile profile = contents.profile();
        return profile != null && profile.water() && !contents.water();
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
        // Saplings have their own (lower) chance: a whole tree in one go
        CropProfile profile = contents.profile();
        float base = profile != null && profile.tree() != null ? ServerConfig.treeGrowChance() : ServerConfig.cropGrowChance();
        return Math.min(1f, base * contents.speed() * boost);
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
        CropProfile profile = contents.profile();
        if (!contents.planted() || contents.grown() || profile == null || profile.flower() || needsWater(contents)) return null;
        if (random.nextFloat() >= growChance(contents)) return null;
        return nextStage(contents, profile, random);
    }

    // One stage further. A tree picks its leaves as it grows (a poplar: yellow, orange or red, a third each).
    private static CropContents nextStage(CropContents contents, CropProfile profile, RandomSource random)
    {
        CropContents next = contents.withStage(contents.stage() + 1);
        if (profile.tree() != null) next = next.withVariant(random.nextInt(profile.tree().leafOptions().size()));
        return next;
    }

    // Bone meal: straight to the next stage (null if nothing's growing)
    // What bone meal does: the book one stage further, or for a flower the same book and one of the flower.
    // Null if it does nothing (nothing planted, or already ripe).
    public record BoneMeal(CropContents contents, List<ItemStack> drops) {}

    // Saplings only grow from bone meal sometimes (ServerConfig.treeBoneMealChance(), 30% by default); the bone meal
    // is used either way, like on a real sapling
    public static @Nullable BoneMeal boneMeal(CropContents contents, RandomSource random)
    {
        CropProfile profile = contents.profile();
        if (!contents.planted() || profile == null) return null;
        // Coral in a dry book: one of its dead form (the book stays as it is)
        if (profile.dead() != null && !contents.water()) return new BoneMeal(contents, List.of(new ItemStack(profile.dead().getBlock())));
        if (needsWater(contents)) return null;
        if (profile.flower()) return new BoneMeal(contents, List.of(new ItemStack(profile.seed())));
        if (contents.grown()) return null;
        if (profile.tree() != null && random.nextFloat() >= ServerConfig.treeBoneMealChance()) return new BoneMeal(contents, List.of());
        return new BoneMeal(nextStage(contents, profile, random), List.of());
    }

    // ---- Harvesting ----

    // A full harvest's drops (see CropProfile#drops), made bigger by yield items; the caller resets the book with
    // contents.withStage(0)
    public static List<ItemStack> harvest(ServerLevel level, CropContents contents, BlockPos pos, ItemStack tool, @Nullable Entity harvester)
    {
        return harvest(level, contents, pos, tool, harvester, false);
    }

    // `stripped`: a tree right-clicked with an axe gives stripped logs
    public static List<ItemStack> harvest(ServerLevel level, CropContents contents, BlockPos pos, ItemStack tool, @Nullable Entity harvester, boolean stripped)
    {
        CropProfile profile = contents.profile();
        if (profile == null || !contents.grown()) return List.of();
        List<ItemStack> drops = profile.drops(level, pos, tool, harvester, stripped && profile.tree() != null, contents.variant());
        double multiplier = yieldMultiplier(contents);
        if (multiplier == 1.0) return drops;
        List<ItemStack> bigger = new ArrayList<>();
        for (ItemStack drop : drops)
        {
            // e.g. 3 x 1.25 = 3.75: 3 for sure, and a 75% chance of a 4th, so on average exactly +25%
            double exact = drop.getCount() * multiplier;
            int count = (int) Math.floor(exact);
            if (level.getRandom().nextDouble() < exact - count) count++;
            // split into normal stack sizes (a big harvest could pass 64)
            while (count > 0)
            {
                int size = Math.min(count, drop.getMaxStackSize());
                bigger.add(drop.copyWithCount(size));
                count -= size;
            }
        }
        return bigger;
    }

    // 1 + yieldPercent% per yield item (1.25, 1.5, 1.75... by default)
    public static double yieldMultiplier(CropContents contents)
    {
        return 1.0 + contents.yields() * ServerConfig.cropYieldPercent() / 100.0;
    }
}
