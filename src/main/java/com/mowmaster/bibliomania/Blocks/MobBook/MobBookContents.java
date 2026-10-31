package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

// What a Bestiary holds (the "bibliomania:mob_contents" data component, on the item and saved by the placed book):
// - type:          the one kind of mob it holds (empty until the first capture, and again once it's emptied)
// - appearance:    the first captured mob's variant and colour data (cow/chicken/frog variant, sheep wool colour...),
//                  given to every mob the book shows or uses. Mobs added later take this look. See MobAppearance.
// - adults:        how many adults; only these can be killed, fed or sheared
// - growing:       babies from the current breeding cycle; they become adults when the cooldown ends
// - fed:           breeding items eaten this cycle (each adult eats at most one); babies = fed / 2
// - breedingEnds:  the game tick the cooldown ends at (0 = no cooldown)
// - nextDrop:      the game tick the adults next lay eggs/shed (0 = not started; only for mobs in
//                  the bibliomania:interval_drops data map, see MobBookDrops)
// - grown:         babies that have grown up but whose grow-up drops (turtle scutes...) haven't been given yet.
//                  settle() counts them; MobBookDrops pays them out on the next tick and sets it back to 0.
// - regrowEnds:    the game tick the adults' wool (or other shearable part) has grown back (0 = not sheared)
// - milkReadyAt:   the game tick the adults can be milked again (0 = now; with the default config there's no wait)
// Times are absolute game ticks (Level#getGameTime), so they run out wherever the book is: placed, in an
// inventory or in a chest. settle(now) moves the grown babies into the adults when read.
// Lengths of time come from the server config (ServerConfig).
// Records are immutable, so every change returns a new copy (data components must never change in place).
public record MobBookContents(Optional<EntityType<?>> type, DataComponentMap appearance, int adults, int growing, int fed,
                              long breedingEnds, long nextDrop, int grown, long regrowEnds, long milkReadyAt)
{
    // Most mobs a book holds (adults + growing)
    public static final int MAX_MOBS = 256;

    public static final MobBookContents EMPTY = new MobBookContents(Optional.empty(), DataComponentMap.EMPTY, 0, 0, 0, 0L, 0L, 0, 0L, 0L);

    // Saved as {"type": "minecraft:sheep", "appearance": {"minecraft:sheep/color": "red"}, "adults": 12, ...};
    // missing fields mean an empty book. DataComponentMap.CODEC saves components the same way item stacks do.
    public static final Codec<MobBookContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("type").forGetter(MobBookContents::type),
                    DataComponentMap.CODEC.optionalFieldOf("appearance", DataComponentMap.EMPTY).forGetter(MobBookContents::appearance),
                    Codec.INT.optionalFieldOf("adults", 0).forGetter(MobBookContents::adults),
                    Codec.INT.optionalFieldOf("growing", 0).forGetter(MobBookContents::growing),
                    Codec.INT.optionalFieldOf("fed", 0).forGetter(MobBookContents::fed),
                    Codec.LONG.optionalFieldOf("breeding_ends", 0L).forGetter(MobBookContents::breedingEnds),
                    Codec.LONG.optionalFieldOf("next_drop", 0L).forGetter(MobBookContents::nextDrop),
                    Codec.INT.optionalFieldOf("grown", 0).forGetter(MobBookContents::grown),
                    Codec.LONG.optionalFieldOf("regrow_ends", 0L).forGetter(MobBookContents::regrowEnds),
                    Codec.LONG.optionalFieldOf("milk_ready_at", 0L).forGetter(MobBookContents::milkReadyAt))
            .apply(instance, MobBookContents::new));

    // Sent to clients through the codec ("WithRegistries" because the type and variants are registry entries)
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
        float progress = 1f - (float) cooldownLeft(now) / ServerConfig.breedingCooldown();
        return Math.clamp(progress, 0f, 1f); // in case the config changed mid-cooldown
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

    // Sheared and still growing back
    public boolean regrowing(long now)
    {
        return regrowEnds > 0 && now < regrowEnds;
    }

    public long regrowLeft(long now)
    {
        return regrowing(now) ? regrowEnds - now : 0;
    }

    // The mobs' dye colour (a sheep's wool), or null if they have none.
    // Any appearance component whose value is a DyeColor counts, so modded dyeable mobs work too.
    @Nullable
    public DyeColor color()
    {
        for (TypedDataComponent<?> component : appearance)
        {
            if (component.value() instanceof DyeColor color) return color;
        }
        return null;
    }

    // ---- Changes (each returns a new copy) ----

    // Once the cooldown has run out: babies are adults, and everyone can eat again
    public MobBookContents settle(long now)
    {
        if (breedingEnds > 0 && now >= breedingEnds)
        {
            return new MobBookContents(type, appearance, adults + growing, 0, 0, 0L, nextDrop, grown + growing, regrowEnds, milkReadyAt);
        }
        return this;
    }

    public boolean canCapture(EntityType<?> mob)
    {
        return (type.isEmpty() || type.get() == mob || total() == 0) && total() < MAX_MOBS;
    }

    // One more mob. An empty book takes on the new mob's type and look (a book emptied of cows can hold sheep);
    // otherwise the look stays the first mob's.
    public MobBookContents withCaptured(EntityType<?> mob, DataComponentMap look)
    {
        if (total() == 0) return new MobBookContents(Optional.of(mob), look, 1, 0, 0, 0L, 0L, 0, 0L, 0L);
        return new MobBookContents(type, appearance, adults + 1, growing, fed, breedingEnds, nextDrop, grown, regrowEnds, milkReadyAt);
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
        return new MobBookContents(type, appearance, adults, newFed / 2, newFed, now + ServerConfig.breedingCooldown(),
                nextDrop, grown, regrowEnds, milkReadyAt);
    }

    // `killed` adults removed. An emptied book forgets its type, so it can hold another kind.
    public MobBookContents withKilled(int killed)
    {
        int left = Math.max(0, adults - killed);
        if (left == 0 && growing == 0) return EMPTY;
        return new MobBookContents(type, appearance, left, growing, Math.min(fed, left), breedingEnds, nextDrop, grown, regrowEnds, milkReadyAt);
    }

    // The egg/shed timer set to `tick` (0 stops it)
    public MobBookContents withNextDrop(long tick)
    {
        return new MobBookContents(type, appearance, adults, growing, fed, breedingEnds, tick, grown, regrowEnds, milkReadyAt);
    }

    // The grown-up babies' drops have been given (or the mob has none)
    public MobBookContents withGrownPaid()
    {
        return new MobBookContents(type, appearance, adults, growing, fed, breedingEnds, nextDrop, 0, regrowEnds, milkReadyAt);
    }

    // Sheared at `now`: grows back after the configured time
    public MobBookContents withSheared(long now)
    {
        return new MobBookContents(type, appearance, adults, growing, fed, breedingEnds, nextDrop, grown,
                now + ServerConfig.shearRegrowth(), milkReadyAt);
    }

    // Milked at `now`: can be milked again after the configured time (0 by default = straight away)
    public MobBookContents withMilked(long now)
    {
        int cooldown = ServerConfig.milkCooldown();
        return new MobBookContents(type, appearance, adults, growing, fed, breedingEnds, nextDrop, grown, regrowEnds,
                cooldown > 0 ? now + cooldown : 0L);
    }

    public boolean milkWaiting(long now)
    {
        return milkReadyAt > 0 && now < milkReadyAt;
    }

    public long milkLeft(long now)
    {
        return milkWaiting(now) ? milkReadyAt - now : 0;
    }

    // A new look (dyed)
    public MobBookContents withAppearance(DataComponentMap look)
    {
        return new MobBookContents(type, look, adults, growing, fed, breedingEnds, nextDrop, grown, regrowEnds, milkReadyAt);
    }

    // Ticks until the next eggs/shed (0 when not started)
    public long dropLeft(long now)
    {
        return nextDrop > 0 ? Math.max(0, nextDrop - now) : 0;
    }
}
