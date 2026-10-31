package com.mowmaster.bibliomania.EventHandlers;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.Book.BookBlockEntity;
import com.mowmaster.bibliomania.Blocks.Book.DeathBookBlock;
import com.mowmaster.bibliomania.Blocks.BlockRegistry;
import com.mowmaster.mowlib.storage.CBBulkItemHandler;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

// When a player dies carrying a death book, the book is placed where they died and everything they dropped goes into
// it, along with their head (its lore is the death message). Whatever doesn't fit is dropped as normal.
// @EventBusSubscriber registers every static @SubscribeEvent method here; NeoForge picks the game event bus because
// LivingDropsEvent is a game event, not a mod lifecycle event.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public class DeathBookHandler
{
    // How far up from where the player died to look for room for the book
    private static final int SEARCH_UP = 8;

    // LOWEST so other mods (e.g. ones that keep certain items) have changed the drops first
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDrops(LivingDropsEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();
        Collection<ItemEntity> drops = event.getDrops();

        ItemEntity bookDrop = findDeathBook(drops);
        if (bookDrop == null) return;

        BlockPos pos = findSpot(level, player.blockPosition());
        if (pos == null) return; // nowhere to put it: everything drops as normal

        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;
        BlockState state = BlockRegistry.DEATH_BOOK.get().defaultBlockState()
                .setValue(BookBlock.FACING, player.getDirection())
                .setValue(BookBlock.WATERLOGGED, water);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof BookBlockEntity book)) return;

        // The book used is taken out of the drops (it's the block now)
        bookDrop.getItem().shrink(1);
        if (bookDrop.getItem().isEmpty()) drops.remove(bookDrop);

        CBBulkItemHandler items = book.getItems();
        // MowLib's StorageInteractions.insertStack: pages already holding the same item first, then empty ones
        StorageInteractions.insertStack(items, playerHead(player), false);

        // An Iterator lets us remove entries from the collection while looping over it
        Iterator<ItemEntity> iterator = drops.iterator();
        while (iterator.hasNext())
        {
            ItemStack stack = iterator.next().getItem();
            int inserted = StorageInteractions.insertStack(items, stack, false);
            stack.shrink(inserted); // the drop keeps whatever didn't fit
            if (stack.isEmpty()) iterator.remove();
        }
        book.setPage(0);
    }

    @Nullable
    private static ItemEntity findDeathBook(Collection<ItemEntity> drops)
    {
        for (ItemEntity drop : drops)
        {
            if (drop.getItem().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof DeathBookBlock)
            {
                return drop;
            }
        }
        return null;
    }

    // The first replaceable block (air, water, grass...) at or above where the player died, kept inside the world
    // (a player who fell into the void gets the book at the bottom of the world)
    @Nullable
    private static BlockPos findSpot(ServerLevel level, BlockPos deathPos)
    {
        int y = Mth.clamp(deathPos.getY(), level.getMinY(), level.getMaxY());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(deathPos.getX(), y, deathPos.getZ());
        for (int i = 0; i <= SEARCH_UP && pos.getY() <= level.getMaxY(); i++)
        {
            if (level.getBlockState(pos).canBeReplaced()) return pos.immutable();
            pos.move(0, 1, 0);
        }
        return null;
    }

    // The player's head, with the death message as its lore
    private static ItemStack playerHead(ServerPlayer player)
    {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        Component message = player.getCombatTracker().getDeathMessage().copy().withStyle(ChatFormatting.DARK_RED);
        head.set(DataComponents.LORE, new ItemLore(List.of(message)));
        return head;
    }
}
