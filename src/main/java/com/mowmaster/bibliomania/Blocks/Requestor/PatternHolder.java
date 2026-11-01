package com.mowmaster.bibliomania.Blocks.Requestor;

import net.minecraft.world.item.ItemStack;

// A block entity with a request pattern drawn on its grid face (RequestorRenderer) and read by the Reading Goggles:
// the Patterned Storage Requestor and the Storage Dispenser.
public interface PatternHolder
{
    RequestPattern pattern();

    // The Storage Dispenser's item in use (its worn shears...), or EMPTY
    default ItemStack inUse()
    {
        return ItemStack.EMPTY;
    }
}
