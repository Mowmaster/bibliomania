package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Mobs that make items on their own keep doing it in a Bestiary. Two kinds, each a NeoForge "data map" (a JSON file
// that attaches a value to registry entries, here entity types), so datapacks and other mods can add mobs without code:
// - interval_drops: every ServerConfig.dropInterval() ticks (default 7.5 minutes), each stored adult drops once (chickens lay eggs, armadillos shed scutes).
//     data/<any namespace>/data_maps/entity_type/interval_drops.json
// - grow_up_drops:  each baby that grows into an adult drops once (turtles drop a turtle scute).
//     data/<any namespace>/data_maps/entity_type/grow_up_drops.json
// A placed book drops the items on itself; a book in a player's inventory puts them in the inventory
// (MobBookBlockEntity#serverTick, MobBookItem#inventoryTick both call tick() below).
//
// @EventBusSubscriber: NeoForge sees that RegisterDataMapTypesEvent is a mod bus event and puts it there.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class MobBookDrops
{
    private MobBookDrops() {}

    // What one mob drops: a loot table, an item, or both. In JSON:
    //   {"loot_table": "minecraft:gameplay/chicken_lay"}
    //   {"item": "minecraft:turtle_scute", "count": 1}       (count is optional, default 1)
    //   "minecraft:gameplay/chicken_lay"                      (just a loot table id)
    // Optional<...> fields: optionalFieldOf leaves them empty when the key isn't in the JSON.
    public record MobDrop(Optional<ResourceKey<LootTable>> lootTable, Optional<Holder<Item>> item, int count)
    {
        private static final Codec<MobDrop> FULL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("loot_table").forGetter(MobDrop::lootTable),
                        BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("item").forGetter(MobDrop::item),
                        Codec.INT.optionalFieldOf("count", 1).forGetter(MobDrop::count))
                .apply(instance, MobDrop::new));
        private static final Codec<MobDrop> TABLE_ID_CODEC = ResourceKey.codec(Registries.LOOT_TABLE)
                .xmap(table -> new MobDrop(Optional.of(table), Optional.empty(), 1),
                        drop -> drop.lootTable().orElseThrow());
        // Codec.withAlternative reads the first form, or if that fails the second (NeoForge's RaidHeroGift does the same)
        public static final Codec<MobDrop> CODEC = Codec.withAlternative(FULL_CODEC, TABLE_ID_CODEC);
    }

    public static final DataMapType<EntityType<?>, MobDrop> INTERVAL_DROPS = dataMap("interval_drops");
    public static final DataMapType<EntityType<?>, MobDrop> GROW_UP_DROPS = dataMap("grow_up_drops");
    // Mobs that shearing in a book kills instead of the usual shear and regrow: shearing a mooshroom turns it into a
    // cow, so the book gives its mushrooms (this drop, rolled as a shearing with the shears as the tool) and kills it
    // (its death drops and XP). data/<any namespace>/data_maps/entity_type/shear_kills.json
    public static final DataMapType<EntityType<?>, MobDrop> SHEAR_KILLS = dataMap("shear_kills");

    private static DataMapType<EntityType<?>, MobDrop> dataMap(String name)
    {
        return DataMapType.builder(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, name),
                        Registries.ENTITY_TYPE, MobDrop.CODEC)
                // Sent to clients too (for the tooltip timer); not required, so clients without it can still join
                .synced(MobDrop.CODEC, false)
                .build();
    }

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event)
    {
        event.register(INTERVAL_DROPS);
        event.register(GROW_UP_DROPS);
        event.register(SHEAR_KILLS);
    }

    // The mob's entry in a data map, or null if it has none. wrapAsHolder gives the registry entry for the type,
    // and NeoForge adds getData to holders.
    @Nullable
    private static MobDrop dropFor(DataMapType<EntityType<?>, MobDrop> map, EntityType<?> type)
    {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).getData(map);
    }

    // Whether the book's mob has interval drops (for the tooltip)
    public static boolean hasDrops(MobBookContents contents)
    {
        return contents.type().isPresent() && dropFor(INTERVAL_DROPS, contents.type().get()) != null;
    }

    // The book's mob's shear-kill drop, or null if shearing doesn't kill it
    @Nullable
    public static MobDrop shearKillFor(MobBookContents contents)
    {
        return contents.type().isPresent() ? dropFor(SHEAR_KILLS, contents.type().get()) : null;
    }

    // `times` of the book's mobs sheared with `shears`: their shearing drops (a loot table rolled with the shears)
    public static List<ItemStack> rollShear(ServerLevel level, MobBookContents contents, MobDrop drop, int times, Vec3 at, ItemStack shears)
    {
        List<ItemStack> items = new ArrayList<>();
        roll(level, contents, drop, times, at, shears, items);
        return items;
    }

    // The result of a tick: the contents to keep (null = unchanged) and the items made (often none)
    public record Result(@Nullable MobBookContents contents, List<ItemStack> items) {}
    private static final Result NOTHING = new Result(null, List.of());

    // Called every tick for a placed book and for a book in a player's inventory (server only).
    // `contents` must be settled (so babies that grew up are counted in grown()).
    public static Result tick(ServerLevel level, MobBookContents contents, Vec3 at)
    {
        if (contents.type().isEmpty()) return NOTHING;
        EntityType<?> type = contents.type().get();
        long now = level.getGameTime();
        MobBookContents updated = contents;
        List<ItemStack> items = new ArrayList<>();

        // Grown-up babies: pay out their drops once, then clear the count (also for mobs without any)
        if (updated.grown() > 0)
        {
            MobDrop drop = dropFor(GROW_UP_DROPS, type);
            if (drop != null) roll(level, updated, drop, updated.grown(), at, null, items);
            updated = updated.withGrownPaid();
        }

        // Interval drops:
        // - a mob with drops and no timer yet: start the timer
        // - timer done: each adult drops once, and the timer restarts
        // - a mob without drops but with a timer (e.g. the chickens were all killed and cows went in): stop the timer
        MobDrop drop = updated.adults() > 0 ? dropFor(INTERVAL_DROPS, type) : null;
        if (drop == null)
        {
            if (updated.nextDrop() != 0) updated = updated.withNextDrop(0);
        }
        else if (updated.nextDrop() == 0)
        {
            updated = updated.withNextDrop(now + ServerConfig.dropInterval());
        }
        else if (now >= updated.nextDrop())
        {
            roll(level, updated, drop, updated.adults(), at, null, items);
            updated = updated.withNextDrop(now + ServerConfig.dropInterval());
        }

        // Records are compared by reference here: every with...() makes a new one, so "same object" = no change
        return updated == contents ? NOTHING : new Result(updated, items);
    }

    // `times` of the book's mobs at `at` each drop once; the items are merged into `into` (as few stacks as possible).
    // With a `tool`, the loot table is rolled as a shearing (vanilla's shearing tables can check the tool), otherwise
    // as a gift (vanilla's context for eggs and scutes).
    private static void roll(ServerLevel level, MobBookContents contents, MobDrop drop, int times, Vec3 at,
                             @Nullable ItemStack tool, List<ItemStack> into)
    {
        // A fixed item: count per mob
        drop.item().ifPresent(item -> {
            for (int i = 0; i < times; i++) merge(into, new ItemStack(item, drop.count()));
        });
        if (drop.lootTable().isEmpty()) return;

        // A loot table, rolled for a temporary mob standing at `at` (like a real chicken laying)
        Entity mob = MobBookActions.createFor(contents, level); // with the book's look: a cold chicken lays blue eggs
        if (mob == null) return;
        mob.setPos(at.x, at.y, at.z);
        // The same calls vanilla's fishing hook makes: the place (ORIGIN), the mob (THIS_ENTITY, so e.g. a brown
        // mooshroom gives brown mushrooms), and for shearing the shears (TOOL)
        LootTable table = level.getServer().reloadableRegistries().getLootTable(drop.lootTable().get());
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, at)
                .withParameter(LootContextParams.THIS_ENTITY, mob);
        LootParams params = tool != null
                ? builder.withParameter(LootContextParams.TOOL, tool).create(LootContextParamSets.SHEARING)
                : builder.create(LootContextParamSets.GIFT);
        for (int i = 0; i < times; i++)
        {
            for (ItemStack stack : table.getRandomItems(params)) merge(into, stack);
        }
    }

    // Adds `stack` to the list, topping up matching stacks first
    static void merge(List<ItemStack> into, ItemStack stack)
    {
        for (ItemStack existing : into)
        {
            if (stack.isEmpty()) return;
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize())
            {
                int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                stack.shrink(moved);
            }
        }
        if (!stack.isEmpty()) into.add(stack);
    }
}
