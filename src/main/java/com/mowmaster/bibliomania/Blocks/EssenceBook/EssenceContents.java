package com.mowmaster.bibliomania.Blocks.EssenceBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

// What a Book of Essence holds (the "bibliomania:essence_contents" data component, on the item and the placed book):
// - type:        the mob it's set to (right-click a mob or a spawner with an empty book); empty until then
// - appearance:  the look of the mob it was set from (variant...), see MobAppearance; empty when set from a spawner
// - essence:     0 to 1 (0% to 100%): kills of the mob with the book in your inventory, and spawners, fill it
// - stored:      mobs "spawned" into the book once it's at 100%; these are what's killed for drops
// - nextSpawn:   the game tick of the next spawn (0 = not started)
// - speedUps:    speed items used (nether stars): each shortens the spawn time by ServerConfig.essenceSpeedPercent()
// - minBonus, maxBonus: added to the config's fewest/most mobs per spawn by size items (heavy cores)
// Records are immutable, so every change returns a new copy (data components must never change in place).
public record EssenceContents(Optional<EntityType<?>> type, DataComponentMap appearance, float essence, int stored,
                              long nextSpawn, int speedUps, int minBonus, int maxBonus)
{
    public static final EssenceContents EMPTY = new EssenceContents(Optional.empty(), DataComponentMap.EMPTY, 0f, 0, 0L, 0, 0, 0);

    public static final Codec<EssenceContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("type").forGetter(EssenceContents::type),
                    DataComponentMap.CODEC.optionalFieldOf("appearance", DataComponentMap.EMPTY).forGetter(EssenceContents::appearance),
                    Codec.FLOAT.optionalFieldOf("essence", 0f).forGetter(EssenceContents::essence),
                    Codec.INT.optionalFieldOf("stored", 0).forGetter(EssenceContents::stored),
                    Codec.LONG.optionalFieldOf("next_spawn", 0L).forGetter(EssenceContents::nextSpawn),
                    Codec.INT.optionalFieldOf("speed_ups", 0).forGetter(EssenceContents::speedUps),
                    Codec.INT.optionalFieldOf("min_bonus", 0).forGetter(EssenceContents::minBonus),
                    Codec.INT.optionalFieldOf("max_bonus", 0).forGetter(EssenceContents::maxBonus))
            .apply(instance, EssenceContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssenceContents> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    // ---- Reading ----

    public boolean isSet()
    {
        return type.isPresent();
    }

    public boolean complete()
    {
        return essence >= 1f;
    }

    public int percent()
    {
        return (int) Math.floor(essence * 100f);
    }

    // Ticks between spawns: the config's time, shortened by each speed item in turn (2% each by default:
    // 0.98 x 0.98 x ...), so it gets close to, but never reaches, 0. Never under 1 second.
    public int interval()
    {
        double factor = Math.pow(1.0 - ServerConfig.essenceSpeedPercent() / 100.0, speedUps);
        return (int) Math.max(20, Math.round(ServerConfig.essenceSpawnInterval() * factor));
    }

    public int spawnMin()
    {
        return Math.max(0, ServerConfig.essenceSpawnMin() + minBonus);
    }

    public int spawnMax()
    {
        return Math.max(spawnMin(), ServerConfig.essenceSpawnMax() + maxBonus);
    }

    public long spawnLeft(long now)
    {
        return nextSpawn > 0 ? Math.max(0, nextSpawn - now) : 0;
    }

    public float fullness()
    {
        return Math.min(1f, (float) stored / ServerConfig.essenceMaxStored());
    }

    // ---- Changes (each returns a new copy) ----

    // Set to a mob (only an empty book is set; see EssenceBookItem)
    public EssenceContents withType(EntityType<?> mob, DataComponentMap look)
    {
        return new EssenceContents(Optional.of(mob), look, 0f, 0, 0L, 0, 0, 0);
    }

    // `amount` more essence (1 = 100%), up to 100%
    public EssenceContents withEssence(float amount)
    {
        return new EssenceContents(type, appearance, Math.min(1f, essence + amount), stored, nextSpawn, speedUps, minBonus, maxBonus);
    }

    public EssenceContents withKilled(int killed)
    {
        return new EssenceContents(type, appearance, essence, Math.max(0, stored - killed), nextSpawn, speedUps, minBonus, maxBonus);
    }

    public EssenceContents withSpeedUp()
    {
        return new EssenceContents(type, appearance, essence, stored, nextSpawn, speedUps + 1, minBonus, maxBonus);
    }

    public EssenceContents withSizeUp()
    {
        return new EssenceContents(type, appearance, essence, stored, nextSpawn, speedUps,
                minBonus + ServerConfig.essenceMinIncrease(), maxBonus + ServerConfig.essenceMaxIncrease());
    }

    // Called every tick (placed, or in a player's inventory), server only. Returns the new contents, or null if nothing
    // changed (almost every tick). Like the Bestiary's eggs, a book in a chest doesn't catch up on missed spawns.
    @Nullable
    public EssenceContents tick(long now, RandomSource random)
    {
        if (!complete()) return null;
        if (nextSpawn == 0) return withNextSpawn(now + interval()); // just reached 100%: start the timer
        if (now < nextSpawn) return null;
        // random.nextIntBetweenInclusive(a, b): a whole number from a to b, both included
        int spawned = random.nextIntBetweenInclusive(spawnMin(), spawnMax());
        int newStored = Math.min(ServerConfig.essenceMaxStored(), stored + spawned);
        return new EssenceContents(type, appearance, essence, newStored, now + interval(), speedUps, minBonus, maxBonus);
    }

    private EssenceContents withNextSpawn(long tick)
    {
        return new EssenceContents(type, appearance, essence, stored, tick, speedUps, minBonus, maxBonus);
    }
}
