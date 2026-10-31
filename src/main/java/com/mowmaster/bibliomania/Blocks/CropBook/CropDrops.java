package com.mowmaster.bibliomania.Blocks.CropBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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

// Harvest overrides for the Farmer's Almanac: a plant with an entry drops exactly this instead of its own loot
// (and no seed is taken off). A NeoForge data map on plant blocks, data/<ns>/data_maps/block/crop_drops.json:
//   { "values": { "minecraft:chorus_flower": { "drops": [
//       { "item": "minecraft:chorus_fruit", "min": 1, "max": 4 },
//       { "item": "minecraft:chorus_flower", "chance": 0.1 } ] } } }
// Each entry: the item, how many (min to max, both 1 if left out), and the chance it drops at all (1 if left out).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class CropDrops
{
    private CropDrops() {}

    public record Entry(Holder<Item> item, int min, int max, float chance)
    {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(Entry::item),
                        Codec.INT.optionalFieldOf("min", 1).forGetter(Entry::min),
                        Codec.INT.optionalFieldOf("max", 1).forGetter(Entry::max),
                        Codec.FLOAT.optionalFieldOf("chance", 1f).forGetter(Entry::chance))
                .apply(instance, Entry::new));
    }

    public record Override(List<Entry> drops)
    {
        public static final Codec<Override> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Entry.CODEC.listOf().fieldOf("drops").forGetter(Override::drops))
                .apply(instance, Override::new));

        // One harvest's worth
        public List<ItemStack> roll(RandomSource random)
        {
            List<ItemStack> result = new ArrayList<>();
            for (Entry entry : drops)
            {
                if (random.nextFloat() >= entry.chance()) continue;
                int count = random.nextIntBetweenInclusive(entry.min(), Math.max(entry.min(), entry.max()));
                if (count > 0) result.add(new ItemStack(entry.item(), count));
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
