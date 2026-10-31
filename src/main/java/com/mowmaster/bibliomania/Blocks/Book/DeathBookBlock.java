package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;

// The gravestone book. Carried in your inventory when you die, it's placed where you died holding everything you
// dropped (see DeathBookHandler). Items can only be taken out, page by page, or all at once by picking it up:
// that spills whatever is left and gives back the empty book, ready for next time.
public class DeathBookBlock extends BookBlock
{
    // A full inventory plus armour, offhand and the player's head fits with room to spare
    public static final int PAGES = 64;
    public static final int PAGE_CAPACITY = 64;

    public DeathBookBlock(Properties properties)
    {
        super(properties, PAGE_CAPACITY, PAGES);
    }

    // Everything a player can drop, other books included
    @Override
    public boolean accepts(ItemResource resource)
    {
        return true;
    }

    @Override
    protected boolean allowsManualInsert()
    {
        return false;
    }

    @Override
    protected void pickUp(ServerLevel level, BlockPos pos, BlockState state, BookBlockEntity book, Player player)
    {
        ItemStack stack;
        // MowLib's StorageInteractions.extractStack: up to a stack at a time from the last page holding something
        while (!(stack = StorageInteractions.extractStack(book.getItems(), Integer.MAX_VALUE)).isEmpty())
        {
            Containers.dropItemStack(level, pos.getX(), pos.getY() + 0.5, pos.getZ(), stack);
        }
        book.setPage(0);
        super.pickUp(level, pos, state, book, player);
    }
}
