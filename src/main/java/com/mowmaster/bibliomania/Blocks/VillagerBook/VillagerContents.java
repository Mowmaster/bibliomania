package com.mowmaster.bibliomania.Blocks.VillagerBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

// What a Trader's Ledger holds (the "bibliomania:villager_contents" data component, on the item and the placed book):
// - villager:     the villager's VillagerData (its type, e.g. plains or desert; its profession; its level 1-5),
//                 empty for an empty book
// - offers:       its trades (vanilla's MerchantOffers, uses included)
// - xp:           its trading experience (levels it up like a real villager)
// - workstation:  the job block shown in front of it (the profession's, or the one that was clicked)
// - page:         the trade on show
// - restockAt:    the game tick all trades restock (0 = none used since the last restock)
// - discounts:    discount items used (enchanted golden apples): emerald prices are lower (VillagerBookActions#applyDiscounts)
// - speedUps:     restock items used (nether stars): trades restock sooner
// The two upgrades belong to the book: they stay when the villager changes profession or is reset.
// MerchantOffers is a mutable list, so it's always copied before a change (data components must never change in place).
public record VillagerContents(Optional<VillagerData> villager, MerchantOffers offers, int xp, Optional<Block> workstation,
                               int page, long restockAt, int discounts, int speedUps)
{
    public static final VillagerContents EMPTY = new VillagerContents(Optional.empty(), new MerchantOffers(), 0, Optional.empty(), 0, 0L, 0, 0);

    // VillagerData and MerchantOffers have vanilla codecs (the same ones villagers save with)
    public static final Codec<VillagerContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    VillagerData.CODEC.optionalFieldOf("villager").forGetter(VillagerContents::villager),
                    MerchantOffers.CODEC.optionalFieldOf("offers").forGetter(contents -> Optional.of(contents.offers())),
                    Codec.INT.optionalFieldOf("xp", 0).forGetter(VillagerContents::xp),
                    BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("workstation").forGetter(VillagerContents::workstation),
                    Codec.INT.optionalFieldOf("page", 0).forGetter(VillagerContents::page),
                    Codec.LONG.optionalFieldOf("restock_at", 0L).forGetter(VillagerContents::restockAt),
                    Codec.INT.optionalFieldOf("discounts", 0).forGetter(VillagerContents::discounts),
                    Codec.INT.optionalFieldOf("speed_ups", 0).forGetter(VillagerContents::speedUps))
            .apply(instance, (villager, offers, xp, workstation, page, restockAt, discounts, speedUps) ->
                    new VillagerContents(villager, offers.orElseGet(MerchantOffers::new), xp, workstation, page, restockAt, discounts, speedUps)));

    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerContents> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    // ---- Reading ----

    public boolean hasVillager()
    {
        return villager.isPresent();
    }

    public int level()
    {
        return villager.map(VillagerData::level).orElse(0);
    }

    // The trade on show, or null if there are none
    public @Nullable MerchantOffer currentOffer()
    {
        if (offers.isEmpty()) return null;
        return offers.get(Math.floorMod(page, offers.size()));
    }

    public int pageCount()
    {
        return Math.max(1, offers.size());
    }

    // ---- Changes (each returns a new copy) ----

    public VillagerContents withPage(int newPage)
    {
        return new VillagerContents(villager, offers, xp, workstation, Math.floorMod(newPage, pageCount()), restockAt, discounts, speedUps);
    }

    public VillagerContents withRestockAt(long tick)
    {
        return new VillagerContents(villager, offers, xp, workstation, page, tick, discounts, speedUps);
    }

    // A new villager, offers, experience and workstation (page and timer reset; the upgrades stay)
    public VillagerContents withVillager(VillagerData data, MerchantOffers newOffers, int newXp, Optional<Block> block)
    {
        return new VillagerContents(Optional.of(data), newOffers, newXp, block, 0, 0L, discounts, speedUps);
    }

    // The same, keeping the page and restock timer (after a trade)
    public VillagerContents withTrade(VillagerData data, MerchantOffers newOffers, int newXp)
    {
        return new VillagerContents(Optional.of(data), newOffers, newXp, workstation, Math.floorMod(page, Math.max(1, newOffers.size())), restockAt, discounts, speedUps);
    }

    // New offers only (restocked, or discounted)
    public VillagerContents withOffers(MerchantOffers newOffers, long newRestockAt)
    {
        return new VillagerContents(villager, newOffers, xp, workstation, page, newRestockAt, discounts, speedUps);
    }

    public VillagerContents withDiscount()
    {
        return new VillagerContents(villager, offers, xp, workstation, page, restockAt, discounts + 1, speedUps);
    }

    public VillagerContents withSpeedUp()
    {
        return new VillagerContents(villager, offers, xp, workstation, page, restockAt, discounts, speedUps + 1);
    }
}
