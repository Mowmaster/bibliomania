package com.mowmaster.bibliomania.Blocks.Lectern;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookItem;
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

// Left-clicking a Reading Lectern (an event, like the books' left-clicks: it also works in creative): harvests an open
// Almanac, or takes from the open page: one item, or a stack while sneaking. The click is cancelled on both sides so
// it doesn't start mining the lectern. It's mined as normal when the open page shows nothing, or with an axe (except
// on an open Almanac, where the axe harvests).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class LecternEvents
{
    private LecternEvents() {}

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Level level = event.getLevel();
        if (!(level.getBlockEntity(event.getPos()) instanceof ReadingLecternBlockEntity lectern)) return;
        // The client knows whether the open page shows something (it's synced); nothing shown: mine it as normal
        if (lectern.getShown().isEmpty()) return;
        Player player = event.getEntity();
        boolean almanac = lectern.getShownBookPage() < 0 && lectern.getShownBook().getItem() instanceof CropBookItem;
        if (player.getMainHandItem().is(ItemTags.AXES) && !almanac) return;
        event.setCanceled(true);
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (LecternActions.attack(serverLevel, player, lectern)) return;
        ItemStack taken = lectern.take(player.isShiftKeyDown() ? 64 : 1);
        if (taken.isEmpty()) return;
        StorageInteractions.giveToPlayer(player, taken);
        level.playSound(null, event.getPos(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.8f, 1f);
    }
}
