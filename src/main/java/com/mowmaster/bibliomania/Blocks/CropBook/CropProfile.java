package com.mowmaster.bibliomania.Blocks.CropBook;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// How a seed grows in a Farmer's Almanac: the block it plants, what each growth stage looks like, and what a full
// harvest gives. Worked out from the seed item, so it's the same on client (rendering) and server (growing):
// - crops (wheat, carrots, potatoes, beetroots, torchflowers, modded crops): their ages; a replanting harvest
// - pumpkin / melon stems: their ages, then the fruit; harvesting gives the fruit and keeps the stem
// - sweet berry bushes, glow berries (cave vines without and with berries): picked, the plant stays
// - sugar cane, kelp, vines, twisting and weeping vines: 1, 2, then 3 blocks tall; the top two are cut
// - cactus: 1, 2, 3 blocks tall, then a cactus flower on top; the top two and the flower are cut
// - chorus flowers: their ages; harvest gives the fruit of a small chorus plant
// - sea pickles: 1 to 4 pickles, under water; a replanting harvest
// - flowers (and seagrass): don't grow; each bone meal gives one more of them
// - saplings: one stage to a whole tree (drawn from a fixed pattern); harvested with an axe (logs, or stripped logs
//   on a right-click) or shears (leaves and what leaves drop), then a sapling again
// - anything else whose block has an "age" (nether wart, cocoa, pitcher pods, modded plants): its ages, replanting
// The fields:
// - replants:  the harvest takes the plant (like breaking a crop), so one seed from the drops goes back in
// - maxHeight: for plants that grow upwards, how many blocks tall they get; 0 for the rest
// - body:      for tall plants, the block under the tip (kelp plant under kelp); null = the same as the tip
// - topper:    drawn on top at the last stage (a cactus's flower), or null
// - pod:       grows on the side of its soil (cocoa): drawn turned so the side it hangs from faces down onto the book
// - flipped:   grows downwards in the world (weeping vines): drawn upside down, so it grows up from the book
// - water:     grows under water (kelp, sea pickles, seagrass): drawn with water round its bottom block
// - flower:    never grows; bone meal gives one of it each time instead (CropBookActions#boneMeal)
// - dead:      what's drawn while the book has no water (coral: its dead form), or null
// - tree:      a sapling, mushroom or fungus that grows into a tree in one go (its blocks and shape; see Tree), or null
// A plant with a bibliomania:crop_drops entry drops that instead of its harvest states' loot (see CropDrops).
public record CropProfile(Item seed, Block plant, List<BlockState> stages, List<BlockState> harvest, boolean replants,
                          int maxHeight, @Nullable BlockState body, @Nullable BlockState topper, boolean pod,
                          boolean flipped, boolean water, boolean flower, @Nullable Tree tree,
                          @Nullable BlockState dead)
{
    // A plain profile (one block tall, no special drawing); the with... methods below add the rest
    private static CropProfile basic(Item seed, Block plant, List<BlockState> stages, List<BlockState> harvest, boolean replants)
    {
        return new CropProfile(seed, plant, stages, harvest, replants, 0, null, null, false, false, false, false, null, null);
    }

    private CropProfile tall(int height, @Nullable BlockState bodyState, @Nullable BlockState topperState)
    {
        return new CropProfile(seed, plant, stages, harvest, replants, height, bodyState, topperState, pod, flipped, water, flower, tree, dead);
    }

    private CropProfile asPod()
    {
        return new CropProfile(seed, plant, stages, harvest, replants, maxHeight, body, topper, true, flipped, water, flower, tree, dead);
    }

    private CropProfile asFlipped()
    {
        return new CropProfile(seed, plant, stages, harvest, replants, maxHeight, body, topper, pod, true, water, flower, tree, dead);
    }

    // Under water: and every state that can be waterlogged is (sea pickles glow only when they are)
    private CropProfile inWater()
    {
        return new CropProfile(seed, plant, waterlogged(stages), waterlogged(harvest), replants, maxHeight, body, topper, pod, flipped, true, flower, tree, dead);
    }

    private CropProfile asFlower()
    {
        return new CropProfile(seed, plant, stages, harvest, replants, maxHeight, body, topper, pod, flipped, water, true, tree, dead);
    }

    private static List<BlockState> waterlogged(List<BlockState> states)
    {
        List<BlockState> result = new ArrayList<>();
        for (BlockState state : states)
        {
            result.add(state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, true) : state);
        }
        return result;
    }

    private CropProfile withDead(@Nullable BlockState deadState)
    {
        return new CropProfile(seed, plant, stages, harvest, replants, maxHeight, body, topper, pod, flipped, water, flower, tree, deadState);
    }

    private CropProfile asTree(Tree treeBlocks)
    {
        return new CropProfile(seed, plant, stages, harvest, replants, maxHeight, body, topper, pod, flipped, water, flower, treeBlocks, dead);
    }

    // ---- Trees ----

    // A tree's blocks and shape: its log (trunk), leaves (the canopy; a mushroom's cap, a fungus's wart blocks), the
    // stripped log an axe right-click gives (null: it can't be stripped, like mushroom stems), and its pattern.
    // Saplings are found by name: "oak_sapling" -> oak_log, oak_leaves, stripped_oak_log (in the sapling's own
    // namespace, so modded trees named the usual way work too; "_propagule" for mangroves). Huge mushrooms and nether
    // fungi are listed in Tree.of.
    // List<String> rather than String[]: a record compares its fields with equals, which a List does by content.
    // leafOptions: the leaves it can grow with, one picked at random each time it grows (poplars: yellow, orange or
    // red; most trees have just one). The pick is saved in the book (CropContents#variant).
    public record Tree(Block log, List<Block> leafOptions, @Nullable Block strippedLog, List<String> pattern)
    {
        // One kind of leaves
        public Tree(Block log, Block leaves, @Nullable Block strippedLog, List<String> pattern)
        {
            this(log, List.of(leaves), strippedLog, pattern);
        }

        // The leaves for a grown tree's variant (floorMod: always 0 to size - 1, even for an old or odd number)
        public Block leaves(int variant)
        {
            return leafOptions.get(Math.floorMod(variant, leafOptions.size()));
        }

        // Patterns, top row first. L or S: log/stem; X or B: leaves/cap blocks; space: nothing.
        // Drawn as square layers (see CropBookRenderer#tree); a harvest gives one block per letter.
        public static final List<String> SAPLING = List.of(
                "  X  ",
                " XXX ",
                "XXLXX",
                "  L  ",
                "  L  ",
                "  L  ");
        public static final List<String> MUSHROOM = List.of(
                "BBBBBBB",
                "   S   ",
                "   S   ",
                "   S   ",
                "   S   ");
        public static final List<String> FUNGUS = List.of(
                "BBBBB",
                "B S B",
                "B S  ",
                "  S  ",
                "  S  ");

        public static boolean isLog(char c)
        {
            return c == 'L' || c == 'S';
        }

        public static boolean isLeaves(char c)
        {
            return c == 'X' || c == 'B';
        }

        // How many logs and leaves a harvest gives: the letters in the pattern
        public int logCount()
        {
            return count(true);
        }

        public int leavesCount()
        {
            return count(false);
        }

        private int count(boolean logs)
        {
            int total = 0;
            for (String row : pattern)
            {
                for (char c : row.toCharArray()) if (logs ? isLog(c) : isLeaves(c)) total++;
            }
            return total;
        }

        public boolean strippable()
        {
            return strippedLog != null;
        }

        public static @Nullable Tree of(Block plant)
        {
            // Huge mushrooms (one stem block for both) and nether fungi
            if (plant == Blocks.BROWN_MUSHROOM) return new Tree(Blocks.MUSHROOM_STEM, Blocks.BROWN_MUSHROOM_BLOCK, null, MUSHROOM);
            if (plant == Blocks.RED_MUSHROOM) return new Tree(Blocks.MUSHROOM_STEM, Blocks.RED_MUSHROOM_BLOCK, null, MUSHROOM);
            if (plant == Blocks.WARPED_FUNGUS) return new Tree(Blocks.WARPED_STEM, Blocks.WARPED_WART_BLOCK, Blocks.STRIPPED_WARPED_STEM, FUNGUS);
            if (plant == Blocks.CRIMSON_FUNGUS) return new Tree(Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK, Blocks.STRIPPED_CRIMSON_STEM, FUNGUS);

            // A SaplingBlock, or anything in the #minecraft:saplings tag (newer or modded saplings with their own class)
            if (!(plant instanceof SaplingBlock) && !plant.defaultBlockState().is(BlockTags.SAPLINGS)) return null;
            Identifier id = BuiltInRegistries.BLOCK.getKey(plant);
            String namespace = id.getNamespace();
            String name = id.getPath().replace("_sapling", "").replace("_propagule", "");
            // The wood: the name, or the name without its first words until there's a log of that wood
            // ("yellow_poplar" -> "poplar" if there's no yellow_poplar_log)
            String wood = name;
            while (block(namespace, wood + "_log") == null && wood.contains("_")) wood = wood.substring(wood.indexOf('_') + 1);
            Block log = block(namespace, wood + "_log");
            if (log == null) return null;
            List<Block> leaves = leaves(namespace, name, wood);
            if (leaves.isEmpty()) return null;
            // A sapling's tree with no stripped log of its own still "strips" (into its plain logs)
            Block stripped = block(namespace, "stripped_" + wood + "_log");
            return new Tree(log, leaves, stripped != null ? stripped : log, SAPLING);
        }

        // "<sapling name>_leaves", then "<wood>_leaves", or else every "<colour>_<wood>_leaves" (poplars have no plain
        // poplar_leaves, only yellow_, orange_ and red_: each is equally likely). Empty if there are none.
        private static List<Block> leaves(String namespace, String name, String wood)
        {
            Block leaves = block(namespace, name + "_leaves");
            if (leaves == null) leaves = block(namespace, wood + "_leaves");
            if (leaves != null) return List.of(leaves);
            String suffix = "_" + wood + "_leaves";
            List<Block> colours = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK)
            {
                Identifier other = BuiltInRegistries.BLOCK.getKey(block);
                if (other.getNamespace().equals(namespace) && other.getPath().endsWith(suffix)) colours.add(block);
            }
            // List.copyOf: an unchangeable copy, so the record can't be changed through it
            return List.copyOf(colours);
        }

        private static @Nullable Block block(String namespace, String path)
        {
            // getOptional: the registered block, or empty if there's none with that id
            return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(namespace, path)).orElse(null);
        }
    }

    // Tools that harvest a grown tree: an axe (logs) or shears (leaves). Other plants take anything (the hand too).
    public boolean canHarvestWith(ItemStack tool)
    {
        return tree == null || tool.is(ItemTags.AXES) || tool.is(Tags.Items.TOOLS_SHEAR);
    }

    // A tree's harvest: shears give its leaves (the blocks, and a roll of the leaves' own drops for each: saplings,
    // sticks, apples, mushrooms); anything else its logs, stripped ones if `stripped` (and it has them)
    // `variant`: which of its leaves the tree grew with (CropContents#variant)
    public List<ItemStack> treeDrops(ServerLevel level, BlockPos pos, ItemStack tool, @Nullable Entity harvester, boolean stripped, int variant)
    {
        List<ItemStack> drops = new ArrayList<>();
        if (tree == null) return drops;
        if (tool.is(Tags.Items.TOOLS_SHEAR))
        {
            int leaves = tree.leavesCount();
            Block leafBlock = tree.leaves(variant);
            drops.add(new ItemStack(leafBlock, leaves));
            for (int leaf = 0; leaf < leaves; leaf++)
            {
                // Rolled without the shears (with them, leaves drop themselves). Blocks that always drop themselves
                // (wart blocks) would come twice, so the leaves block itself is left out of the roll.
                for (ItemStack drop : Block.getDrops(leafBlock.defaultBlockState(), level, pos, null, harvester, ItemStack.EMPTY))
                {
                    if (!drop.is(leafBlock.asItem())) drops.add(drop);
                }
            }
        }
        else
        {
            Block log = stripped && tree.strippable() ? tree.strippedLog() : tree.log();
            drops.add(new ItemStack(log, tree.logCount()));
        }
        return drops;
    }

    // ---- Reading ----

    public int maxStage()
    {
        return stages.size() - 1;
    }

    public BlockState stage(int stage)
    {
        return stages.get(Math.clamp(stage, 0, maxStage()));
    }

    // How many blocks tall the plant is drawn at this stage (tall plants grow upwards, the rest are one)
    public int height(int stage)
    {
        return maxHeight > 0 ? Math.min(stage + 1, maxHeight) : 1;
    }

    // What's drawn on top at this stage (the cactus flower when grown), or null
    public @Nullable BlockState topper(int stage)
    {
        return stage >= maxStage() ? topper : null;
    }

    // What a full harvest drops: each harvested state's own loot (with the tool, so Fortune works), and one seed
    // less for plants that are replanted (if a seed dropped)
    // A bibliomania:crop_drops entry replaces all of it, or (with "replace": false) adds to it. `stripped`: a tree
    // right-clicked with an axe gives stripped logs.
    public List<ItemStack> drops(ServerLevel level, BlockPos pos, ItemStack tool, @Nullable Entity harvester, boolean stripped, int variant)
    {
        CropDrops.Override override = CropDrops.overrideFor(plant);
        if (override != null && override.replace()) return override.roll(level.getRandom(), tool);
        List<ItemStack> drops = tree != null ? treeDrops(level, pos, tool, harvester, stripped, variant) : ownDrops(level, pos, tool, harvester);
        if (override != null) drops.addAll(override.roll(level.getRandom(), tool));
        return drops;
    }

    public List<ItemStack> drops(ServerLevel level, BlockPos pos, ItemStack tool, @Nullable Entity harvester)
    {
        return drops(level, pos, tool, harvester, false, 0);
    }

    // The harvested states' own loot, less the replanted seed
    private List<ItemStack> ownDrops(ServerLevel level, BlockPos pos, ItemStack tool, @Nullable Entity harvester)
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
            return basic(seed, plant, stages, List.of(fruit.defaultBlockState()), false);
        }
        // Glow berries: a vine without, then with berries; picking leaves the vine
        if (plant == Blocks.CAVE_VINES)
        {
            BlockState ripe = base.setValue(CaveVines.BERRIES, true);
            return basic(seed, plant, List.of(base.setValue(CaveVines.BERRIES, false), ripe), List.of(ripe), false);
        }
        // Sugar cane: 1 to 3 blocks tall; the top two are cut, the bottom one stays
        if (plant == Blocks.SUGAR_CANE)
        {
            return basic(seed, plant, List.of(base, base, base), List.of(base, base), false).tall(3, null, null);
        }
        // Kelp: the same, under water; kelp plant blocks under a kelp tip
        if (plant == Blocks.KELP)
        {
            BlockState body = Blocks.KELP_PLANT.defaultBlockState();
            return basic(seed, plant, List.of(base, base, base), List.of(body, body), false).tall(3, body, null).inWater();
        }
        // Twisting vines grow up; weeping vines grow down from ceilings, so they're drawn upside down
        if (plant == Blocks.TWISTING_VINES || plant == Blocks.WEEPING_VINES)
        {
            boolean weeping = plant == Blocks.WEEPING_VINES;
            BlockState body = (weeping ? Blocks.WEEPING_VINES_PLANT : Blocks.TWISTING_VINES_PLANT).defaultBlockState();
            CropProfile profile = basic(seed, plant, List.of(base, base, base), List.of(body, body), false).tall(3, body, null);
            return weeping ? profile.asFlipped() : profile;
        }
        // Vines: hanging on all four sides of a column; they only drop with shears, so their drops are in crop_drops
        if (plant == Blocks.VINE)
        {
            BlockState sides = base.setValue(VineBlock.NORTH, true).setValue(VineBlock.SOUTH, true)
                    .setValue(VineBlock.EAST, true).setValue(VineBlock.WEST, true);
            return basic(seed, plant, List.of(sides, sides, sides), List.of(sides, sides), false).tall(3, null, null);
        }
        // Cactus: 1 to 3 blocks tall, then a flower on top (one stage more than sugar cane); the top two and the
        // flower are cut, the bottom one stays
        if (plant == Blocks.CACTUS)
        {
            BlockState flower = Blocks.CACTUS_FLOWER.defaultBlockState();
            return basic(seed, plant, List.of(base, base, base, base), List.of(base, base, flower), false).tall(3, null, flower);
        }
        // Chorus: the flower's ages; a harvest gives what a small chorus plant would (4 stems' worth)
        if (plant == Blocks.CHORUS_FLOWER)
        {
            BlockState stem = Blocks.CHORUS_PLANT.defaultBlockState();
            return basic(seed, plant, ages(base), Collections.nCopies(4, stem), false);
        }
        // Sweet berries: picked, the bush stays
        if (plant == Blocks.SWEET_BERRY_BUSH)
        {
            List<BlockState> stages = ages(base);
            return basic(seed, plant, stages, List.of(stages.getLast()), false);
        }
        // Torchflowers and pitcher pods grow into their flower, which is what's harvested
        if (plant == Blocks.TORCHFLOWER_CROP || plant == Blocks.PITCHER_CROP)
        {
            Block flower = plant == Blocks.TORCHFLOWER_CROP ? Blocks.TORCHFLOWER : Blocks.PITCHER_PLANT;
            List<BlockState> stages = new ArrayList<>(ages(base));
            stages.add(flower.defaultBlockState());
            return basic(seed, plant, stages, List.of(flower.defaultBlockState()), true);
        }
        // Coral, coral fans and coral blocks: like flowers (bone meal gives one more), but under water; with no water
        // in the book they're drawn as their dead form ("dead_" + the name, in the same namespace)
        if (base.is(BlockTags.CORALS) || base.is(BlockTags.CORAL_BLOCKS))
        {
            Identifier id = BuiltInRegistries.BLOCK.getKey(plant);
            BlockState dead = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), "dead_" + id.getPath()))
                    .map(Block::defaultBlockState).orElse(null);
            // Dead coral out of water isn't waterlogged
            if (dead != null && dead.hasProperty(BlockStateProperties.WATERLOGGED)) dead = dead.setValue(BlockStateProperties.WATERLOGGED, false);
            return basic(seed, plant, List.of(base), List.of(), false).asFlower().inWater().withDead(dead);
        }
        // Saplings, mushrooms and fungi: the plant, then (in one go) the whole tree
        Tree tree = Tree.of(plant);
        if (tree != null) return basic(seed, plant, List.of(base, base), List.of(), false).asTree(tree);
        // Flowers (and seagrass, under water): they don't grow; bone meal gives one at a time
        if (base.is(BlockTags.FLOWERS) || plant == Blocks.SEAGRASS)
        {
            CropProfile flower = basic(seed, plant, List.of(base), List.of(), false).asFlower();
            return plant == Blocks.SEAGRASS ? flower.inWater() : flower;
        }
        // Crops, and anything else with an age (or, for sea pickles, a pickle count)
        List<BlockState> stages = plant instanceof CropBlock crop ? cropAges(crop) : ages(base);
        if (stages.size() < 2) return null;
        CropProfile profile = basic(seed, plant, stages, List.of(stages.getLast()), true);
        // Pods (cocoa, or anything built on CocoaBlock) hang from the side of their soil
        if (plant instanceof CocoaBlock) profile = profile.asPod();
        // Sea pickles, and anything that is water by default (a plant that only lives under water)
        if (plant instanceof SeaPickleBlock || base.getFluidState().is(Fluids.WATER)) profile = profile.inWater();
        return profile;
    }

    // A crop's own states for each age (CropBlock knows its age property and maximum)
    private static List<BlockState> cropAges(CropBlock crop)
    {
        List<BlockState> stages = new ArrayList<>();
        for (int age = 0; age <= crop.getMaxAge(); age++) stages.add(crop.getStateForAge(age));
        return stages;
    }

    // The block's state for each value of its "age" property (or "pickles", for sea pickles), lowest first
    // (empty if it has neither)
    private static List<BlockState> ages(BlockState base)
    {
        for (Property<?> property : base.getProperties())
        {
            if (property instanceof IntegerProperty age && (property.getName().equals("age") || property.getName().equals("pickles")))
            {
                List<BlockState> stages = new ArrayList<>();
                for (int value : age.getPossibleValues().stream().sorted().toList()) stages.add(base.setValue(age, value));
                return stages;
            }
        }
        return List.of();
    }
}
