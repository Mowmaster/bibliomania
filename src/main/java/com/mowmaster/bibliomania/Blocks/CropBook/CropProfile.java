package com.mowmaster.bibliomania.Blocks.CropBook;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// How a seed grows in a Farmer's Almanac: the block it plants, what each growth stage looks like, and what a full
// harvest gives. Worked out from the seed item, so it's the same on client (rendering) and server (growing):
// - crops (wheat, carrots, potatoes, beetroots, torchflowers, modded crops): their ages; a replanting harvest
// - pumpkin / melon stems: their ages, then the fruit; harvesting gives the fruit and keeps the stem
// - sweet berry bushes, glow berries (cave vines without and with berries): picked, the plant stays
// - sugar cane: 1, 2, then 3 blocks tall; the top two are cut
// - chorus flowers: their ages; harvest gives the fruit of a small chorus plant
// - anything else whose block has an "age" (nether wart, cocoa, pitcher pods, modded plants): its ages, replanting
// `replants`: the harvest takes the plant (like breaking a crop), so one seed from the drops goes back in.
public record CropProfile(Item seed, Block plant, List<BlockState> stages, List<BlockState> harvest, boolean replants, boolean tall)
{
    public int maxStage()
    {
        return stages.size() - 1;
    }

    public BlockState stage(int stage)
    {
        return stages.get(Math.clamp(stage, 0, maxStage()));
    }

    // How many blocks tall the plant is drawn at this stage (sugar cane grows upwards, everything else is one)
    public int height(int stage)
    {
        return tall ? stage + 1 : 1;
    }

    // What a full harvest drops: each harvested state's own loot (with the tool, so Fortune works), and one seed
    // less for plants that are replanted (if a seed dropped)
    public List<ItemStack> drops(ServerLevel level, BlockPos pos, ItemStack tool, @Nullable Entity harvester)
    {
        List<ItemStack> drops = new ArrayList<>();
        for (BlockState state : harvest)
        {
            // Block.getDrops rolls the block's loot table for that state, as if it were broken
            drops.addAll(Block.getDrops(state, level, pos, null, harvester, tool));
        }
        if (replants)
        {
            for (ItemStack drop : drops)
            {
                if (drop.is(seed))
                {
                    drop.shrink(1);
                    break;
                }
            }
            drops.removeIf(ItemStack::isEmpty);
        }
        return drops;
    }

    // ---- Working out a seed's profile ----

    // null if the item can't be planted in the book
    public static @Nullable CropProfile of(Item seed)
    {
        if (!(seed instanceof BlockItem blockItem)) return null;
        Block plant = blockItem.getBlock();
        BlockState base = plant.defaultBlockState();

        // Stems: grow, then the fruit appears; harvesting takes only the fruit
        if (plant == Blocks.PUMPKIN_STEM || plant == Blocks.MELON_STEM)
        {
            Block fruit = plant == Blocks.PUMPKIN_STEM ? Blocks.PUMPKIN : Blocks.MELON;
            List<BlockState> stages = new ArrayList<>(ages(base));
            stages.add(fruit.defaultBlockState());
            return new CropProfile(seed, plant, stages, List.of(fruit.defaultBlockState()), false, false);
        }
        // Glow berries: a vine without, then with berries; picking leaves the vine
        if (plant == Blocks.CAVE_VINES)
        {
            BlockState ripe = base.setValue(CaveVines.BERRIES, true);
            return new CropProfile(seed, plant, List.of(base.setValue(CaveVines.BERRIES, false), ripe), List.of(ripe), false, false);
        }
        // Sugar cane: 1 to 3 blocks tall; the top two are cut, the bottom one stays
        if (plant == Blocks.SUGAR_CANE)
        {
            return new CropProfile(seed, plant, List.of(base, base, base), List.of(base, base), false, true);
        }
        // Chorus: the flower's ages; a harvest gives what a small chorus plant would (4 stems' worth)
        if (plant == Blocks.CHORUS_FLOWER)
        {
            BlockState stem = Blocks.CHORUS_PLANT.defaultBlockState();
            return new CropProfile(seed, plant, ages(base), Collections.nCopies(4, stem), false, false);
        }
        // Sweet berries: picked, the bush stays
        if (plant == Blocks.SWEET_BERRY_BUSH)
        {
            List<BlockState> stages = ages(base);
            return new CropProfile(seed, plant, stages, List.of(stages.getLast()), false, false);
        }
        // Torchflowers and pitcher pods grow into their flower, which is what's harvested
        if (plant == Blocks.TORCHFLOWER_CROP || plant == Blocks.PITCHER_CROP)
        {
            Block flower = plant == Blocks.TORCHFLOWER_CROP ? Blocks.TORCHFLOWER : Blocks.PITCHER_PLANT;
            List<BlockState> stages = new ArrayList<>(ages(base));
            stages.add(flower.defaultBlockState());
            return new CropProfile(seed, plant, stages, List.of(flower.defaultBlockState()), true, false);
        }
        // Crops, and anything else with an age
        List<BlockState> stages = plant instanceof CropBlock crop ? cropAges(crop) : ages(base);
        if (stages.size() < 2) return null;
        return new CropProfile(seed, plant, stages, List.of(stages.getLast()), true, false);
    }

    // A crop's own states for each age (CropBlock knows its age property and maximum)
    private static List<BlockState> cropAges(CropBlock crop)
    {
        List<BlockState> stages = new ArrayList<>();
        for (int age = 0; age <= crop.getMaxAge(); age++) stages.add(crop.getStateForAge(age));
        return stages;
    }

    // The block's state for each value of its "age" property, lowest first (empty if it has none)
    private static List<BlockState> ages(BlockState base)
    {
        for (Property<?> property : base.getProperties())
        {
            if (property instanceof IntegerProperty age && property.getName().equals("age"))
            {
                List<BlockState> stages = new ArrayList<>();
                for (int value : age.getPossibleValues().stream().sorted().toList()) stages.add(base.setValue(age, value));
                return stages;
            }
        }
        return List.of();
    }
}
