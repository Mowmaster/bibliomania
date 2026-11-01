package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryPriority;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// The Priority Scroll's left-click (an event, like the books' left-clicks: it also works in creative, and is
// cancelled so the block isn't mined or "attacked"), and the priority line on storage book tooltips.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class PriorityEvents
{
    private PriorityEvents() {}

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof PriorityScrollItem)) return;
        if (!LibraryPriority.hasPriority(event.getLevel(), event.getPos())) return;
        event.setCanceled(true);
        if (event.getLevel() instanceof ServerLevel level) LibraryPriority.adjust(level, event.getPos(), -LibraryPriority.step(player), player);
    }

    // "Library priority: 20" on a book that has one
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        int priority = LibraryPriority.ofBook(event.getItemStack());
        if (priority <= 0) return;
        event.getToolTip().add(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".priority", priority).withStyle(ChatFormatting.GRAY));
    }
}
