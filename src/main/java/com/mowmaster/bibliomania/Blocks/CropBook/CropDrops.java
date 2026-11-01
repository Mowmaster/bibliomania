package com.mowmaster.bibliomania.Blocks.CropBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.HolderSetCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Harvest overrides for the Farmer's Almanac. A NeoForge data map on plant blocks,
// data/<ns>/data_maps/block/crop_drops.json:
//   { "values": {
//       "minecraft:chorus_flower": { "drops": [
//         { "item": "minecraft:chorus_fruit", "min": 1, "max": 4 },
//         { "item": "minecraft:chorus_flower", "chance": 0.1 } ] },
//       "minecraft:mangrove_propagule": { "replace": false, "drops": [
//         { "item": "minecraft:mangrove_propagule", "chance": 0.05, "rolls": 8, "tool": "#c:tools/shear" } ] } } }
// - replace: true (the default) drops only these, instead of the plant's own loot (and no seed is taken off);
//            false adds them on top of the normal harvest (a tree's logs or leaves, a crop's loot)
// Each entry:
// - item:    what drops
// - min/max: how many (both 1 if left out)
// - chance:  the chance it drops at all, per roll (1 if left out)
// - rolls:   how many times it's tried (1 if left out): 8 rolls at 0.05 is like eight leaves each with a 5% chance
// - tool:    only when harvested with one of these items: an item id, a "#tag", or a list of them (any tool, or the
//            hand, if left out). A tree's axe harvest and its axe right-click (stripped logs) both count as the axe.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class CropDrops
{
    private CropDrops() {}

    // HolderSet: a set of registry entries written as one id, a "#tag", or a list of ids. HolderSetCodec reads all
    // three (the last argument false: a single id doesn't have to be wrapped in a list).
    private static final Codec<HolderSet<Item>> ITEMS = HolderSetCodec.create(Registries.ITEM, BuiltInRegistries.ITEM.holderByNameCodec(), false);

    // Optional<...> fields: optionalFieldOf without a default gives Optional.empty() when the field is left out
    public record Entry(Holder<Item> item, int min, int max, float chance, int rolls, Optional<HolderSet<Item>> tool)
    {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(Entry::item),
                        Codec.INT.optionalFieldOf("min", 1).forGetter(Entry::min),
                        Codec.INT.optionalFieldOf("max", 1).forGetter(Entry::max),
                        Codec.FLOAT.optionalFieldOf("chance", 1f).forGetter(Entry::chance),
                        Codec.INT.optionalFieldOf("rolls", 1).forGetter(Entry::rolls),
                        ITEMS.optionalFieldOf("tool").forGetter(Entry::tool))
                .apply(instance, Entry::new));

        // No tool listed: anything; otherwise the harvesting stack must be one of them
        public boolean allows(ItemStack harvestTool)
        {
            return tool.isEmpty() || harvestTool.is(tool.get());
        }
    }

    public record Override(boolean replace, List<Entry> drops)
    {
        public static final Codec<Override> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.BOOL.optionalFieldOf("replace", true).forGetter(Override::replace),
                        Entry.CODEC.listOf().fieldOf("drops").forGetter(Override::drops))
                .apply(instance, Override::new));

        // One harvest's worth with this tool
        public List<ItemStack> roll(RandomSource random, ItemStack harvestTool)
        {
            List<ItemStack> result = new ArrayList<>();
            for (Entry entry : drops)
            {
                if (!entry.allows(harvestTool)) continue;
                int total = 0;
                for (int roll = 0; roll < entry.rolls(); roll++)
                {
                    if (random.nextFloat() >= entry.chance()) continue;
                    total += random.nextIntBetweenInclusive(entry.min(), Math.max(entry.min(), entry.max()));
                }
                if (total > 0) result.add(new ItemStack(entry.item(), total));
            }
            return result;
        }
    }

    public static final DataMapType<Block, Override> DROPS = DataMapType.builder(
                    Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "crop_drops"), Registries.BLOCK, Override.CODEC)
            .build();

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event)
    {
        event.register(DROPS);
    }

    public static @Nullable Override overrideFor(Block plant)
    {
        return plant.builtInRegistryHolder().getData(DROPS);
    }
}
