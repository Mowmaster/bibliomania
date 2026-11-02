package com.mowmaster.bibliomania.Blocks.CompactingBook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;

// What a Compacting Book holds: its chain of tiers (gold nugget -> ingot -> block), and ONE pool counted in the
// lowest tier (nuggets). Every tier is a view of that pool: putting in a block adds 81, taking out a nugget takes 1,
// and each tier shows how many of it the pool makes (total / its value). Nothing is ever crafted, so nothing is lost.
// - The room is CAPACITY of the top tier: 1024 gold blocks = 82,944 nuggets; 1024 amethyst blocks = 4,096 shards.
// - An empty book has no chain; the first item in sets it (CompactingChains), and emptying it clears it again.
// - page: the tier the placed book has open (its arrows and the page keys turn it)
// Saved on the item as bibliomania:compacting_contents (a record: immutable, with equals/hashCode, as components must be).
public record CompactingContents(List<Tier> tiers, long total, int page)
{
    // How many of the top tier the book holds when full
    public static final int CAPACITY = 1024;

    // One tier: its item and how many of the lowest tier it's worth (the lowest is 1)
    public record Tier(Item item, int value)
    {
        public static final Codec<Tier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Tier::item),
                Codec.INT.fieldOf("value").forGetter(Tier::value)
        ).apply(instance, Tier::new));
    }

    public static final CompactingContents EMPTY = new CompactingContents(List.of(), 0, 0);

    public static final Codec<CompactingContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Tier.CODEC.listOf().fieldOf("tiers").forGetter(CompactingContents::tiers),
            Codec.LONG.fieldOf("total").forGetter(CompactingContents::total),
            Codec.INT.optionalFieldOf("page", 0).forGetter(CompactingContents::page)
    ).apply(instance, CompactingContents::new));
    // fromCodecWithRegistries: sends it to clients the same way it's saved
    public static final StreamCodec<RegistryFriendlyByteBuf, CompactingContents> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public CompactingContents
    {
        tiers = List.copyOf(tiers); // compact constructor: keeps the list immutable
    }

    // A fresh book set to a chain (no items yet), open at `page`
    public static CompactingContents of(List<Tier> tiers, int page)
    {
        return new CompactingContents(tiers, 0, Math.max(0, Math.min(page, tiers.size() - 1)));
    }

    public boolean isSet()
    {
        return !tiers.isEmpty();
    }

    public int size()
    {
        return tiers.size();
    }

    // The most the pool can hold, in the lowest tier
    public long maxTotal()
    {
        return isSet() ? (long) tiers.getLast().value() * CAPACITY : 0;
    }

    public float fullness()
    {
        return isSet() ? (float) ((double) total / maxTotal()) : 0f;
    }

    public ItemResource resource(int tier)
    {
        return tier >= 0 && tier < tiers.size() ? ItemResource.of(tiers.get(tier).item()) : ItemResource.EMPTY;
    }

    public ItemStack stack(int tier, int count)
    {
        return tier >= 0 && tier < tiers.size() ? new ItemStack(tiers.get(tier).item(), count) : ItemStack.EMPTY;
    }

    // How many of a tier the pool makes
    public int amount(int tier)
    {
        if (tier < 0 || tier >= tiers.size()) return 0;
        return (int) Math.min(Integer.MAX_VALUE, total / tiers.get(tier).value());
    }

    // How many more of a tier fit
    public int room(int tier)
    {
        if (tier < 0 || tier >= tiers.size()) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (maxTotal() - total) / tiers.get(tier).value());
    }

    // Which tier an item is (exactly: no extra components), or -1
    public int tierOf(ItemResource resource)
    {
        for (int tier = 0; tier < tiers.size(); tier++)
        {
            if (resource.equals(resource(tier))) return tier;
        }
        return -1;
    }

    // count of a tier added (negative: taken). Emptied: no chain any more, so the book can take something else.
    public CompactingContents plus(int tier, long count)
    {
        long changed = Math.max(0, Math.min(maxTotal(), total + count * tiers.get(tier).value()));
        return changed == 0 ? EMPTY : new CompactingContents(tiers, changed, page);
    }

    public CompactingContents withPage(int newPage)
    {
        return isSet() ? new CompactingContents(tiers, total, Math.floorMod(newPage, tiers.size())) : this;
    }
}
