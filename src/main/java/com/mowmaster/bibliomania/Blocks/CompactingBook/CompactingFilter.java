package com.mowmaster.bibliomania.Blocks.CompactingBook;

import com.mowmaster.mowlib.filter.HeldFilters;
import com.mowmaster.mowlib.filter.ITransportFilter;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;

// A Compacting Book's filter (MowLib's HeldFilters keeps it, in mowlib:held_filter): whether an empty book may start a
// chain (a book that holds something only ever takes its own chain).
public final class CompactingFilter
{
    private CompactingFilter() {}

    // A whitelist lets it start if any tier is listed (whitelist iron ingots: iron nuggets start it too); a blacklist
    // stops it if any tier is listed. No filter: anything.
    public static boolean allowsChain(ItemStack filter, List<CompactingContents.Tier> chain)
    {
        if (!(filter.getItem() instanceof ITransportFilter transportFilter)) return true;
        boolean blacklist = transportFilter.isBlacklist(filter);
        for (CompactingContents.Tier tier : chain)
        {
            boolean allowed = HeldFilters.allows(filter, ItemResource.of(tier.item()));
            if (blacklist && !allowed) return false;
            if (!blacklist && allowed) return true;
        }
        return blacklist;
    }
}
