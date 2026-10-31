package com.mowmaster.bibliomania.Blocks.BarterBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// What a Book of Barter holds (the "bibliomania:barter_contents" data component, on the item and the placed book):
// - piglin:    whether a piglin has been put in (right-click one with the book); it only barters (and can only be
//              placed) with one
// - gold:      gold ingots given to the piglin (a gold block counts as 9)
// - nextDrop:  the game tick of the next barter (0 = not started)
// - speedUps:  speed items used (nether stars): barters come sooner
// - yields:    yield items used (heavy cores): each barter rolls the loot table once more, for one more gold
public record BarterContents(boolean piglin, int gold, long nextDrop, int speedUps, int yields)
{
    public static final BarterContents EMPTY = new BarterContents(false, 0, 0L, 0, 0);

    public static final Codec<BarterContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("piglin", false).forGetter(BarterContents::piglin),
                    Codec.INT.optionalFieldOf("gold", 0).forGetter(BarterContents::gold),
                    Codec.LONG.optionalFieldOf("next_drop", 0L).forGetter(BarterContents::nextDrop),
                    Codec.INT.optionalFieldOf("speed_ups", 0).forGetter(BarterContents::speedUps),
                    Codec.INT.optionalFieldOf("yields", 0).forGetter(BarterContents::yields))
            .apply(instance, BarterContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BarterContents> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    // Rolls (and gold) per barter: 1, plus 1 per heavy core
    public int perBarter()
    {
        return 1 + yields;
    }

    public BarterContents withGold(int newGold)
    {
        return new BarterContents(piglin, newGold, nextDrop, speedUps, yields);
    }

    public BarterContents withNextDrop(long tick)
    {
        return new BarterContents(piglin, gold, tick, speedUps, yields);
    }

    public BarterContents withSpeedUp()
    {
        return new BarterContents(piglin, gold, nextDrop, speedUps + 1, yields);
    }

    public BarterContents withYield()
    {
        return new BarterContents(piglin, gold, nextDrop, speedUps, yields + 1);
    }

    public BarterContents withPiglin()
    {
        return new BarterContents(true, gold, nextDrop, speedUps, yields);
    }
}
