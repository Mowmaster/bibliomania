package com.mowmaster.bibliomania.Blocks.CropBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.HolderSetCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

// Which soils a Farmer's Almanac takes, and how fast crops grow on them. Two NeoForge data maps (JSON, so datapacks
// and other mods can add soils and rules):
// - bibliomania:crop_soils, on soil blocks (data/<ns>/data_maps/block/crop_soils.json):
//     {"speed": 1.25}                      growth speed (1 = normal, 0.5 = half as fast, 1.25 = a quarter faster)
//     {"speed": 1.0, "any_plant": false}   only for plants whose rule names it (end stone: only chorus)
// - bibliomania:crop_soil_rules, on plant blocks (data/<ns>/data_maps/block/crop_soil_rules.json): a plant with a
//   rule only grows on the soils it lists (ids, or a tag like "#minecraft:logs"), at the speeds it lists (or the
//   soil's own speed):
//     {"soils": ["minecraft:sand", "minecraft:dirt"], "speeds": {"minecraft:dirt": 0.5}}
//     {"soils": "#minecraft:logs", "speeds": {"minecraft:jungle_log": 1.25}}
// Plants without a rule grow on every soil that has "any_plant" (the default).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class CropSoils
{
    private CropSoils() {}

    public record Soil(float speed, boolean anyPlant)
    {
        public static final Codec<Soil> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("speed", 1f).forGetter(Soil::speed),
                        Codec.BOOL.optionalFieldOf("any_plant", true).forGetter(Soil::anyPlant))
                .apply(instance, Soil::new));
    }

    // HolderSet: a set of registry entries written as a tag ("#minecraft:logs"), one id, or a list of ids (the same
    // codec vanilla and NeoForge use for block and fluid lists). Codec.unboundedMap: a JSON object whose keys are
    // block ids and values are numbers.
    public record Rule(HolderSet<Block> soils, Map<Block, Float> speeds)
    {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        HolderSetCodec.create(Registries.BLOCK, BuiltInRegistries.BLOCK.holderByNameCodec(), false).fieldOf("soils").forGetter(Rule::soils),
                        Codec.unboundedMap(BuiltInRegistries.BLOCK.byNameCodec(), Codec.FLOAT).optionalFieldOf("speeds", Map.of()).forGetter(Rule::speeds))
                .apply(instance, Rule::new));
    }

    // Both are sent to clients (the inventory clicks and tooltips use them there too)
    public static final DataMapType<Block, Soil> SOILS = DataMapType.builder(
                    Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "crop_soils"), Registries.BLOCK, Soil.CODEC)
            .synced(Soil.CODEC, false).build();

    public static final DataMapType<Block, Rule> RULES = DataMapType.builder(
                    Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "crop_soil_rules"), Registries.BLOCK, Rule.CODEC)
            .synced(Rule.CODEC, false).build();

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event)
    {
        event.register(SOILS);
        event.register(RULES);
    }

    // The soil's entry, or null if it isn't a soil. builtInRegistryHolder: the block's registry entry, which NeoForge
    // lets us read data maps from.
    public static @Nullable Soil soil(Block block)
    {
        return block.builtInRegistryHolder().getData(SOILS);
    }

    public static boolean isSoil(Block block)
    {
        return soil(block) != null;
    }

    // How fast `plant` grows on `soil` (1 = normal), or 0 if it can't grow there
    public static float speed(Block plant, Block soil)
    {
        Soil entry = soil(soil);
        Rule rule = plant.builtInRegistryHolder().getData(RULES);
        if (rule != null)
        {
            if (!rule.soils().contains(soil.builtInRegistryHolder())) return 0f;
            Float override = rule.speeds().get(soil);
            return override != null ? override : entry != null ? entry.speed() : 1f;
        }
        return entry != null && entry.anyPlant() ? entry.speed() : 0f;
    }
}
