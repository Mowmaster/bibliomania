package com.mowmaster.bibliomania.Blocks.CompactingBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Works out an item's compacting chain (gold nugget -> ingot -> block), on the server, once per item: the answer is
// kept until datapacks reload (recipes or the data map may change). A book stores its chain on itself
// (CompactingContents), so clients never need recipes (the server doesn't send them) and this runs only when an empty
// book takes its first item.
// - Custom chains: the bibliomania:compacting item data map, which comes first. For items whose recipes don't go both
//   ways (amethyst shards -> block has no way back), or to change a chain:
//     data/<namespace>/data_maps/item/compacting.json
//     {"values": {"minecraft:amethyst_shard": {"into": "minecraft:amethyst_block", "count": 4}}}
//   meaning 4 shards are one block (and the block is 4 shards).
// - Otherwise crafting recipes: X compacts into Y if 3x3 (or 2x2) of X crafts one Y, and one Y crafts 9 (or 4) X back.
//   Only chains that go both ways are used, so every tier can come back out.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class CompactingChains
{
    private CompactingChains() {}

    private static final int MAX_TIERS = 6;

    // One step up: `count` of this item make one `into`
    public record Step(Holder<Item> into, int count)
    {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("into").forGetter(Step::into),
                Codec.INT.fieldOf("count").forGetter(Step::count)
        ).apply(instance, Step::new));
    }

    public static final DataMapType<Item, Step> COMPACTING = DataMapType.builder(
                    Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "compacting"), Registries.ITEM, Step.CODEC)
            .build();

    @SubscribeEvent
    public static void registerDataMaps(RegisterDataMapTypesEvent event)
    {
        event.register(COMPACTING);
    }

    // Item -> its chain (Optional.empty(): it has none). ConcurrentHashMap: safe if ever read from two threads.
    private static final Map<Item, Optional<List<CompactingContents.Tier>>> CACHE = new ConcurrentHashMap<>();

    // Datapacks (re)loaded: recipes and data maps may have changed. With no player it's a reload for everyone.
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event)
    {
        if (event.getPlayer() == null) CACHE.clear();
    }

    // The chain an item belongs to, lowest tier first, or null if it has none (or there's no server: clients)
    public static @Nullable List<CompactingContents.Tier> find(Item item)
    {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        ServerLevel level = server.overworld();
        // computeIfAbsent: works it out only the first time this item is asked about
        return CACHE.computeIfAbsent(item, each -> Optional.ofNullable(build(level, each))).orElse(null);
    }

    private static @Nullable List<CompactingContents.Tier> build(ServerLevel level, Item item)
    {
        // Down to the lowest tier...
        Item base = item;
        Set<Item> seen = new HashSet<>();
        seen.add(base);
        for (int steps = 0; steps < MAX_TIERS; steps++)
        {
            Item lower = down(level, base);
            if (lower == null || !seen.add(lower)) break;
            base = lower;
        }
        // ...then up, adding up each tier's worth
        List<CompactingContents.Tier> tiers = new ArrayList<>();
        int value = 1;
        tiers.add(new CompactingContents.Tier(base, value));
        Set<Item> used = new HashSet<>(Collections.singleton(base));
        Item current = base;
        while (tiers.size() < MAX_TIERS)
        {
            Step step = up(level, current);
            if (step == null || !used.add(step.into().value())) break;
            value *= step.count();
            current = step.into().value();
            tiers.add(new CompactingContents.Tier(current, value));
        }
        return tiers.size() >= 2 ? List.copyOf(tiers) : null;
    }

    // What `count` of this item compact into, or null
    private static @Nullable Step up(ServerLevel level, Item item)
    {
        // wrapAsHolder: the registry entry for the item; NeoForge adds getData (its data map value) to holders
        Step custom = BuiltInRegistries.ITEM.wrapAsHolder(item).getData(COMPACTING);
        if (custom != null) return custom;
        for (int side : new int[]{3, 2})
        {
            ItemStack made = craft(level, side, item);
            if (made.isEmpty() || made.getCount() != 1) continue;
            ItemStack back = craft(level, 1, made.getItem());
            if (back.is(item) && back.getCount() == side * side) return new Step(BuiltInRegistries.ITEM.wrapAsHolder(made.getItem()), side * side);
        }
        return null;
    }

    // The item this one is made of (the tier below), or null
    private static @Nullable Item down(ServerLevel level, Item item)
    {
        // getDataMap: every entry of the data map, item key -> value
        for (Map.Entry<ResourceKey<Item>, Step> entry : BuiltInRegistries.ITEM.getDataMap(COMPACTING).entrySet())
        {
            if (entry.getValue().into().value() == item) return BuiltInRegistries.ITEM.getValue(entry.getKey());
        }
        ItemStack apart = craft(level, 1, item);
        if (apart.isEmpty() || (apart.getCount() != 9 && apart.getCount() != 4)) return null;
        Step back = up(level, apart.getItem());
        return back != null && back.into().value() == item ? apart.getItem() : null;
    }

    // What a side x side square of the item crafts into (EMPTY if nothing): the same lookup a crafting table does
    private static ItemStack craft(ServerLevel level, int side, Item item)
    {
        List<ItemStack> grid = new ArrayList<>();
        for (int slot = 0; slot < side * side; slot++) grid.add(new ItemStack(item));
        CraftingInput input = CraftingInput.of(side, side, grid);
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input))
                .orElse(ItemStack.EMPTY);
    }
}
