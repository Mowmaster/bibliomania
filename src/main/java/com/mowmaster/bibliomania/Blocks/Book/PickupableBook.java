package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.mowlib.keys.IPickupableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

// A placed book's block entity that can become its item again, contents and all (every Bibliomania book's). It's
// MowLib's IPickupableBlock (toItem, markPickedUp, pickUp), so MowLib's pick-up key (default H) works on every book;
// the storage books' BookBlockEntity overrides pickUp (the Book of Death spills its items first).
public interface PickupableBook extends IPickupableBlock
{
    // Picks the book at `pos` up for `player` (the books' own sneak + right-click). true if there was a book.
    static boolean pickUp(ServerLevel level, BlockPos pos, Player player)
    {
        if (!(level.getBlockState(pos).getBlock() instanceof BookBlock)) return false;
        return IPickupableBlock.pickUpAt(level, pos, player);
    }
}
