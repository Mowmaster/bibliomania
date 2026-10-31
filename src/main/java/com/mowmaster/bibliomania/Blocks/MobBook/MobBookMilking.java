package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Milking the mobs in a Bestiary: an empty container (a bucket, a bowl...) used on the book is filled by the adults,
// one container per adult, e.g. 12 cows fill up to 12 buckets in one go. Afterwards they wait ServerConfig.milkCooldown()
// ticks (0 by default: no wait, like vanilla).
//
// Which mobs, and what they fill, is a NeoForge data map (like MobBookDrops), so datapacks and other mods can add
// their own milkable mobs: data/<any namespace>/data_maps/entity_type/milkings.json
//   { "values": { "minecraft:mooshroom": [
//       { "input": "minecraft:bucket", "result": "minecraft:milk_bucket", "sound": "minecraft:entity.cow.milk" },
//       { "input": "minecraft:bowl",   "result": "minecraft:mushroom_stew", "sound": "minecraft:entity.mooshroom.milk" } ] } }
// (A mooshroom's suspicious stew needs it to have eaten a flower first, which the book doesn't track: it gives plain stew.)
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class MobBookMilking
{
    private MobBookMilking() {}

    // The sound when none is given in the JSON
    private static final Identifier DEFAULT_SOUND = Identifier.withDefaultNamespace("entity.cow.milk");

    // One way to milk a mob: the empty container, what it becomes, and the sound (optional)
    public record Milking(Holder<Item> input, Holder<Item> result, Optional<Holder<SoundEvent>> sound)
    {
        public static final Codec<Milking> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("input").forGetter(Milking::input),
                        BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("result").forGetter(Milking::result),
                        BuiltInRegistries.SOUND_EVENT.holderByNameCodec().optionalFieldOf("sound").forGetter(Milking::sound))
                .apply(instance, Milking::new));
    }

    // The value for each mob is a list (listOf), since a mob can fill more than one kind of container
    public static final DataMapType<EntityType<?>, List<Milking>> MILKINGS = DataMapType.builder(
                    Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "milkings"), Registries.ENTITY_TYPE,
                    Milking.CODEC.listOf())
            // Sent to clients too: the client checks it to know a click will do something
            .synced(Milking.CODEC.listOf(), false)
            .build();

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event)
    {
        event.register(MILKINGS);
    }

    // The way `stack` milks the book's mobs, or null if it doesn't (wrong item, not a milkable mob)
    @Nullable
    public static Milking milkingFor(MobBookContents contents, ItemStack stack)
    {
        if (stack.isEmpty() || contents.type().isEmpty()) return null;
        List<Milking> milkings = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(contents.type().get()).getData(MILKINGS);
        if (milkings == null) return null;
        for (Milking milking : milkings)
        {
            if (stack.is(milking.input())) return milking;
        }
        return null;
    }

    // Whether `stack` can be used to milk the book now (a milkable mob, adults, and not waiting)
    public static boolean canMilk(Level level, MobBookContents contents, ItemStack stack)
    {
        return contents.adults() > 0 && !contents.milkWaiting(level.getGameTime()) && milkingFor(contents, stack) != null;
    }

    // How many containers from `stack` get filled: one per adult (creative players keep their containers)
    public static int amount(MobBookContents contents, ItemStack stack)
    {
        return Math.min(stack.getCount(), contents.adults());
    }

    // Uses up `count` containers from `stack` and returns the filled ones (the caller gives them to the player),
    // split into stacks of the right size (milk buckets and stew only stack to 1). `pos` is where the sound plays.
    // Server only.
    public static List<ItemStack> milk(ServerLevel level, Milking milking, ItemStack stack, int count, Player player, BlockPos pos)
    {
        stack.consume(count, player);
        List<ItemStack> results = new ArrayList<>();
        int left = count;
        while (left > 0)
        {
            ItemStack result = new ItemStack(milking.result());
            int size = Math.min(left, result.getMaxStackSize());
            result.setCount(size);
            results.add(result);
            left -= size;
        }
        // Holder#value gives the SoundEvent inside; getValue looks one up by id (null if it doesn't exist)
        SoundEvent sound = milking.sound().map(Holder::value).orElse(BuiltInRegistries.SOUND_EVENT.getValue(DEFAULT_SOUND));
        if (sound != null) level.playSound(null, pos, sound, SoundSource.PLAYERS, 1f, 1f);
        return results;
    }
}
