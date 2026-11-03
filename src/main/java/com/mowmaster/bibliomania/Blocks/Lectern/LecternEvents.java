package com.mowmaster.bibliomania.Blocks.Lectern;

import com.mowmaster.bibliomania.Blocks.Library.LibraryCosts;
import com.mowmaster.mowlib.tint.IColorableBlock;
import com.mowmaster.mowlib.paging.IPagedStorageItem;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// Left-clicking a Reading Lectern (an event, like the books' left-clicks: it also works in creative): a whole book open
// gets the book's own left-click (LecternActions#attack); a page of items gives one item, or a stack while sneaking.
// The click is cancelled on both sides so it doesn't start mining the lectern. It's mined as normal when the open page
// shows nothing, with sneak + an axe, or with an axe on a page of items.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class LecternEvents
{
    private LecternEvents() {}

    // Sneaking with something in either hand makes vanilla skip the block's own right-click (it goes to the item
    // instead). On the lectern's arrows, sneak + right-click must reach the block: it turns the open book's pages
    // (a Ledger's trades). TriState.TRUE: use the block whatever vanilla would do.
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (!event.getEntity().isShiftKeyDown()) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof ReadingLecternBlock)) return;
        // ... and sneak + a dye dyes the lectern (without sneaking, an open book gets the dye)
        if (ReadingLecternBlock.arrowAt(state, event.getHitVec()) != 0 || IColorableBlock.dyeColorOf(event.getItemStack()) != null)
        {
            event.setUseBlock(TriState.TRUE);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Level level = event.getLevel();
        if (!(level.getBlockEntity(event.getPos()) instanceof ReadingLecternBlockEntity lectern)) return;
        // The client knows whether the open page shows something (it's synced); nothing shown: mine it as normal
        if (lectern.getShown().isEmpty()) return;
        Player player = event.getEntity();
        // A library out of knowledge (LibraryCosts): nothing can be taken (the lectern can still be mined with an axe)
        if (!player.getMainHandItem().is(ItemTags.AXES) && LibraryCosts.blocked(level, lectern, event.getPos(), player))
        {
            event.setCanceled(true);
            return;
        }
        // A library-wide type drawer open: its left-click (take experience...); sneak + axe still mines
        if (lectern.showsType())
        {
            if (player.getMainHandItem().is(ItemTags.AXES) && player.isShiftKeyDown()) return;
            event.setCanceled(true);
            if (level instanceof ServerLevel serverLevel) LecternActions.attackType(serverLevel, player, lectern);
            return;
        }
        boolean book = lectern.showsWholeBook();
        // Sneaking with an axe always mines it; an axe alone mines it unless a whole book is open (the book gets the
        // click: an Almanac's tree is cut with it)
        if (player.getMainHandItem().is(ItemTags.AXES) && (player.isShiftKeyDown() || !book)) return;
        event.setCanceled(true);
        if (!(level instanceof ServerLevel serverLevel)) return;
        // A whole book: only its own left-click (harvest, kill, take experience...)
        if (book)
        {
            LecternActions.attack(serverLevel, player, lectern);
            return;
        }
        ItemStack taken = lectern.take(player.isShiftKeyDown() ? 64 : 1);
        if (taken.isEmpty()) return;
        // An item book in the main hand takes it first (its own rules and filter; IPagedStorageItem#insert changes
        // the held book), whatever doesn't fit goes to the player
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof IPagedStorageItem paged && held.getCount() == 1)
        {
            taken.shrink(paged.insert(held, taken, false));
        }
        if (!taken.isEmpty()) StorageInteractions.giveToPlayer(player, taken);
        level.playSound(null, event.getPos(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.8f, 1f);
    }
}
