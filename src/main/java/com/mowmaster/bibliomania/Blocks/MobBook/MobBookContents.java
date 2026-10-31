package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

// What a Bestiary holds (the "bibliomania:mob_contents" data component, on the item and saved by the placed book):
// - type:          the one kind of mob it holds (empty until the first capture, and again once it's emptied)
// - adults:        how many adults; only these can be killed or fed
// - growing:       babies from the current breeding cycle; they become adults when the cooldown ends
// - fed:           breeding items eaten this cycle (each adult eats at most one); babies = fed / 2
// - breedingEnds:  the game tick the cooldown ends at (0 = no cooldown)
// - nextDrop:      the game tick the adults next lay eggs/shed (0 = not started; only for mobs in
//                  the bibliomania:interval_drops data map, see MobBookDrops)
// Times are absolute game ticks (Level#getGameTime), so the cooldown runs out wherever the book is: placed, in an
// inventory or in a chest. Nothing has to tick: settle(now) moves the grown babies into the adults when read.
// Records are immutable, so every change returns a new copy (data components must never change in place).
public record MobBookContents(Optional<EntityType<?>> type, int adults, int growing, int fed, long breedingEnds, long nextDrop)
{
    // Most mobs a book holds (adults + growing)
    public static final int MAX_MOBS = 256;
    // Breeding cooldown: 5 minutes (20 ticks a second)
    public static final int BREEDING_COOLDOWN = 5 * 60 * 20;

    public static final MobBookContents EMPTY = new MobBookContents(Optional.empty(), 0, 0, 0, 0L, 0L);

    // Saved as {"type": "minecraft:cow", "adults": 12, ...}; missing fields mean an empty book
    public static final Codec<MobBookContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("type").forGetter(MobBookContents::type),
                    Codec.INT.optionalFieldOf("adults", 0).forGetter(MobBookContents::adults),
                    Codec.INT.optionalFieldOf("growing", 0).forGetter(MobBookContents::growing),
                    Codec.INT.optionalFieldOf("fed", 0).forGetter(MobBookContents::fed),
                    Codec.LONG.optionalFieldOf("breeding_ends", 0L).forGetter(MobBookContents::breedingEnds),
                    Codec.LONG.optionalFieldOf("next_drop", 0L).forGetter(MobBookContents::nextDrop))
            .apply(instance, MobBookContents::new));

    // Sent to clients through the codec ("WithRegistries" because the type is a registry entry)
    public static final StreamCodec<RegistryFriendlyByteBuf, MobBookContents> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    // ---- Reading ----

    public int total()
    {
        return adults + growing;
    }

    public boolean isEmpty()
    {
        return type.isEmpty() || total() <= 0;
    }

    public boolean onCooldown(long now)
    {
        return breedingEnds > 0 && now < breedingEnds;
    }

    // Ticks left on the breeding cooldown (0 when none)
    public long cooldownLeft(long now)
    {
        return onCooldown(now) ? breedingEnds - now : 0;
    }

    // How far the growing babies are, 0 (just born) to 1 (grown)
    public float growProgress(long now)
    {
        if (!onCooldown(now)) return 1f;
        return 1f - (float) cooldownLeft(now) / BREEDING_COOLDOWN;
    }

    // Adults that haven't eaten this cycle
    public int breedable()
    {
        return Math.max(0, adults - fed);
    }

    public float fullness()
    {
        return (float) total() / MAX_MOBS;
    }

    // ---- Changes (each returns a new copy) ----

    // Once the cooldown has run out: babies are adults, and everyone can eat again
    public MobBookContents settle(long now)
    {
        if (breedingEnds > 0 && now >= breedingEnds) return new MobBookContents(type, adults + growing, 0, 0, 0L, nextDrop);
        return this;
    }

    public boolean canCapture(EntityType<?> mob)
    {
        return (type.isEmpty() || type.get() == mob || total() == 0) && total() < MAX_MOBS;
    }

    public MobBookContents withCaptured(EntityType<?> mob)
    {
        // An empty book takes on the new type (a book emptied of cows can hold sheep)
        if (total() == 0) return new MobBookContents(Optional.of(mob), 1, 0, 0, 0L, 0L);
        return new MobBookContents(type, adults + 1, growing, fed, breedingEnds, nextDrop);
    }

    // How many breeding items can be eaten now: one per adult that hasn't eaten, and no more than would fit
    // (fed / 2 babies must stay within MAX_MOBS)
    public int maxFeed()
    {
        int roomForBabies = MAX_MOBS - adults;
        int maxFedForRoom = 2 * roomForBabies + 1;
        return Math.max(0, Math.min(breedable(), maxFedForRoom - fed));
    }

    // `eaten` breeding items eaten at `now`: babies become fed / 2, and the cooldown (re)starts
    public MobBookContents withFed(int eaten, long now)
    {
        int newFed = fed + eaten;
        return new MobBookContents(type, adults, newFed / 2, newFed, now + BREEDING_COOLDOWN, nextDrop);
    }

    // `killed` adults removed. An emptied book forgets its type, so it can hold another kind.
    public MobBookContents withKilled(int killed)
    {
        int left = Math.max(0, adults - killed);
        if (left == 0 && growing == 0) return EMPTY;
        return new MobBookContents(type, left, growing, Math.min(fed, left), breedingEnds, nextDrop);
    }

    // The egg/shed timer set to `tick` (0 stops it)
    public MobBookContents withNextDrop(long tick)
    {
        return new MobBookContents(type, adults, growing, fed, breedingEnds, tick);
    }

    // Ticks until the next eggs/shed (0 when not started)
    public long dropLeft(long now)
    {
        return nextDrop > 0 ? Math.max(0, nextDrop - now) : 0;
    }
}
