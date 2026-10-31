package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
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

// Mobs that lay eggs or shed something on their own (chickens, armadillos...) keep doing it in a Bestiary:
// every DROP_INTERVAL ticks, each stored adult rolls its loot table once. A placed book drops the items on itself;
// a book in a player's inventory puts them in the inventory (see MobBookBlockEntity#serverTick, MobBookItem#inventoryTick).
//
// Which mobs, and which loot table, comes from a NeoForge "data map": a JSON file that attaches a value to registry
// entries, here entity types. Ours is data/<any namespace>/data_maps/entity_type/interval_drops.json:
//   { "values": { "minecraft:chicken": { "loot_table": "minecraft:gameplay/chicken_lay" } } }
// Datapacks and other mods can add their own mobs to it without code. The tables are vanilla's own egg/scute tables,
// so a chicken's egg follows the same rules as a real chicken's.
//
// @EventBusSubscriber: NeoForge sees that RegisterDataMapTypesEvent is a mod bus event and puts it there.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class MobBookDrops
{
    private MobBookDrops() {}

    // 7.5 minutes (20 ticks a second)
    public static final int DROP_INTERVAL = 9000;

    // The data map's value: the loot table to roll
    public record IntervalDrop(ResourceKey<LootTable> lootTable)
    {
        // {"loot_table": "minecraft:gameplay/chicken_lay"}, or just the id as a string
        // (Codec.withAlternative tries the first form, then the second; NeoForge's RaidHeroGift does the same)
        public static final Codec<IntervalDrop> ID_CODEC = ResourceKey.codec(Registries.LOOT_TABLE)
                .xmap(IntervalDrop::new, IntervalDrop::lootTable);
        public static final Codec<IntervalDrop> CODEC = Codec.withAlternative(
                RecordCodecBuilder.create(instance -> instance.group(
                                ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(IntervalDrop::lootTable))
                        .apply(instance, IntervalDrop::new)),
                ID_CODEC);
    }

    public static final DataMapType<EntityType<?>, IntervalDrop> INTERVAL_DROPS = DataMapType.builder(
                    Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "interval_drops"), Registries.ENTITY_TYPE,
                    IntervalDrop.CODEC)
            // Sent to clients too (for the tooltip timer); not required, so clients without it can still join
            .synced(IntervalDrop.CODEC, false)
            .build();

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event)
    {
        event.register(INTERVAL_DROPS);
    }

    // The mob's entry, or null if it doesn't lay/shed anything. wrapAsHolder gives the registry entry for the type,
    // and NeoForge adds getData to holders.
    @Nullable
    public static IntervalDrop dropFor(EntityType<?> type)
    {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).getData(INTERVAL_DROPS);
    }

    public static boolean hasDrops(MobBookContents contents)
    {
        return contents.type().isPresent() && dropFor(contents.type().get()) != null;
    }

    // The result of a tick: the contents to keep (null = unchanged) and the items made (often none)
    public record Result(@Nullable MobBookContents contents, List<ItemStack> items) {}
    private static final Result NOTHING = new Result(null, List.of());

    // Called every tick for a placed book and for a book in a player's inventory (server only).
    // - A mob with drops and no timer yet: start the timer
    // - Timer done: each adult rolls the loot table once, and the timer restarts
    // - A mob without drops but with a timer (e.g. the chickens were all killed and cows went in): stop the timer
    public static Result tick(ServerLevel level, MobBookContents contents, Vec3 at)
    {
        long now = level.getGameTime();
        IntervalDrop drop = contents.type().isPresent() && contents.adults() > 0 ? dropFor(contents.type().get()) : null;
        if (drop == null)
        {
            return contents.nextDrop() != 0 ? new Result(contents.withNextDrop(0), List.of()) : NOTHING;
        }
        if (contents.nextDrop() == 0) return new Result(contents.withNextDrop(now + DROP_INTERVAL), List.of());
        if (now < contents.nextDrop()) return NOTHING;

        List<ItemStack> items = roll(level, contents.type().get(), drop, contents.adults(), at);
        return new Result(contents.withNextDrop(now + DROP_INTERVAL), items);
    }

    // Rolls the table `times` times for a temporary mob of `type` standing at `at` (like a real chicken laying),
    // and merges the results into as few stacks as possible.
    private static List<ItemStack> roll(ServerLevel level, EntityType<?> type, IntervalDrop drop, int times, Vec3 at)
    {
        List<ItemStack> merged = new ArrayList<>();
        Entity mob = MobBookActions.createTemporary(type, level, EntitySpawnReason.TRIGGERED);
        if (mob == null) return merged;
        mob.setPos(at.x, at.y, at.z);

        // The same calls vanilla's fishing hook makes. "Gift" is the loot context vanilla uses for chicken eggs and
        // armadillo scutes: the place (ORIGIN) and the mob (THIS_ENTITY, so e.g. a cold chicken could lay blue eggs).
        LootTable table = level.getServer().reloadableRegistries().getLootTable(drop.lootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, at)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .create(LootContextParamSets.GIFT);
        for (int i = 0; i < times; i++)
        {
            for (ItemStack stack : table.getRandomItems(params)) merge(merged, stack);
        }
        return merged;
    }

    // Adds `stack` to the list, topping up matching stacks first
    private static void merge(List<ItemStack> into, ItemStack stack)
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
