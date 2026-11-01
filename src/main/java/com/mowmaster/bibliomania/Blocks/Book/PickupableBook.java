package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// A placed book's block entity that can become its item again, contents and all (every Bibliomania book's).
// The "pick up book" key (Network/PickupBookPayload) uses pickUp below for any of them.
public interface PickupableBook
{
    // The book as an item, carrying its contents
    ItemStack toItem();

    // Called just before the block is removed, so removing it doesn't also drop the book
    void markPickedUp();

    // Picks the book at `pos` up for `player`: the storage books through BookBlock#pickUp (the Book of Death spills its
    // items first), every other book the same way here. true if there was a book.
    static boolean pickUp(ServerLevel level, BlockPos pos, Player player)
    {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (state.getBlock() instanceof BookBlock book && blockEntity instanceof BookBlockEntity storage)
        {
            book.pickUp(level, pos, state, storage, player);
            return true;
        }
        if (!(state.getBlock() instanceof BookBlock) || !(blockEntity instanceof PickupableBook pickupable)) return false;
        ItemStack item = pickupable.toItem();
        pickupable.markPickedUp();
        level.removeBlock(pos, false);
        give(player, item);
        SoundType sound = state.getSoundType();
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        return true;
    }

    // Into an empty main hand, otherwise the inventory (or at the player's feet if that's full): never replaces
    // what the player is holding
    static void give(Player player, ItemStack item)
    {
        if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, item);
        else StorageInteractions.giveToPlayer(player, item);
    }
}
