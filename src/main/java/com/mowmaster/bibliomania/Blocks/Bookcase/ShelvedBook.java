package com.mowmaster.bibliomania.Blocks.Bookcase;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.mowmaster.mowlib.reading.ReadingPaper;
import com.mowmaster.mowlib.reading.IReadableItem;
import com.mowmaster.mowlib.transport.TransportType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jetbrains.annotations.Nullable;

// What a book item does while it sits in a Bookcase (implemented on the book's Item class). Every method has a
// default, so a book only overrides what it has:
// - shelfHandler: its storage of one transport type, for hoppers and pipes (through the bookcase's capabilities).
//   `access` is the book's place in the bookcase: the handler changes the book only through it, so a transaction that's
//   cancelled (a hopper only simulating) leaves the book as it was.
// - shelfTick: what its inventoryTick does (growing, restocking, laying eggs...), with the bookcase instead of a player.
// - shelfFullness: how full it is (0-1), for the Reading Goggles; -1 for books that don't store anything.
// - read: its reading HUD paper (MowLib IReadableItem), when it has more to say than MowLib's default
public interface ShelvedBook extends IReadableItem
{
    // The reading HUD (MowLib IReadableItem): every Bibliomania book shows its paper while held. null: MowLib's
    // usual paper (a paged storage book's pages, or else the tooltip); books with more to say override it.
    @Override
    default @Nullable ReadingPaper read(ItemStack stack, Level level, Player player)
    {
        return null;
    }

    // <R extends Resource>: a generic method, so the handler's resource type matches the type asked for
    default <R extends Resource> @Nullable ResourceHandler<R> shelfHandler(TransportType<R> type, ItemAccess access)
    {
        return null;
    }

    // Server, every tick. `book` is the stack in the bookcase itself: change it in place, and return true when
    // anything changed (so the bookcase saves it and tells clients). Items the book makes go to shelf.store(...).
    default boolean shelfTick(ItemStack book, ServerLevel level, BookcaseBlockEntity shelf, int slot)
    {
        return false;
    }

    default float shelfFullness(ItemStack book)
    {
        return -1f;
    }
}
