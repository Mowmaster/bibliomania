package com.mowmaster.bibliomania.Blocks.CropBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

// What a Farmer's Almanac holds (the "bibliomania:crop_contents" data component, on the item and the placed book):
// - soil:      the soil block (always put in first)
// - seed:      the planted item (wheat seeds, sugar cane, glow berries...); its CropProfile says how it grows
// - stage:     the growth stage, 0 to the profile's last
// - nextTick:  in an inventory, the game tick of the next growth chance (0 = not started); placed books use random ticks
// - boosts:    boost items put in (nether stars): each raises the growth chance and shortens the inventory timer by
//              ServerConfig.cropBoostPercent(); kept when the soil or seed is changed
// Records are immutable, so every change returns a new copy.
public record CropContents(Optional<Block> soil, Optional<Item> seed, int stage, long nextTick, int boosts)
{
    public static final CropContents EMPTY = new CropContents(Optional.empty(), Optional.empty(), 0, 0L, 0);

    public static final Codec<CropContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("soil").forGetter(CropContents::soil),
                    BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("seed").forGetter(CropContents::seed),
                    Codec.INT.optionalFieldOf("stage", 0).forGetter(CropContents::stage),
                    Codec.LONG.optionalFieldOf("next_tick", 0L).forGetter(CropContents::nextTick),
                    Codec.INT.optionalFieldOf("boosts", 0).forGetter(CropContents::boosts))
            .apply(instance, CropContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CropContents> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    // ---- Reading ----

    public @Nullable CropProfile profile()
    {
        return seed.map(CropProfile::of).orElse(null);
    }

    public boolean planted()
    {
        return profile() != null && soil.isPresent();
    }

    public boolean grown()
    {
        CropProfile profile = profile();
        return profile != null && stage >= profile.maxStage();
    }

    // How fast it grows on its soil (1 = normal); 0 if nothing is planted
    public float speed()
    {
        CropProfile profile = profile();
        if (profile == null || soil.isEmpty()) return 0f;
        return CropSoils.speed(profile.plant(), soil.get());
    }

    // Whether `seed` could be planted now (soil in, nothing planted, and the seed's plant grows on this soil)
    public boolean canPlant(Item item)
    {
        if (soil.isEmpty() || seed.isPresent()) return false;
        CropProfile profile = CropProfile.of(item);
        return profile != null && CropSoils.speed(profile.plant(), soil.get()) > 0f;
    }

    // ---- Changes ----

    public CropContents withSoil(Block block)
    {
        return new CropContents(Optional.of(block), seed, 0, 0L, boosts);
    }

    public CropContents withSeed(Item item)
    {
        return new CropContents(soil, Optional.of(item), 0, 0L, boosts);
    }

    public CropContents withStage(int newStage)
    {
        return new CropContents(soil, seed, newStage, nextTick, boosts);
    }

    public CropContents withNextTick(long tick)
    {
        return new CropContents(soil, seed, stage, tick, boosts);
    }

    // The seed taken out (growth lost), or else the soil
    public CropContents withoutTop()
    {
        if (seed.isPresent()) return new CropContents(soil, Optional.empty(), 0, 0L, boosts);
        return new CropContents(Optional.empty(), Optional.empty(), 0, 0L, boosts);
    }

    public CropContents withBoost()
    {
        return new CropContents(soil, seed, stage, nextTick, boosts + 1);
    }
}
