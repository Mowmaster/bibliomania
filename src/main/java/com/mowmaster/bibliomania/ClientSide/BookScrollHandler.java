package com.mowmaster.bibliomania.ClientSide;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookItem;
import com.mowmaster.bibliomania.Network.SelectBookPagePayload;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Scrolling over a book in an inventory selects the next or previous page (empty ones too), like scrolling over
// a bundle. Vanilla does this for bundles with an ItemSlotMouseAction, but screens only let themselves add those,
// so we listen for the scroll instead. The page changes here straight away (so the tooltip updates) and the server
// is told (so taking items out uses the same page).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID, value = Dist.CLIENT)
public class BookScrollHandler
{
    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event)
    {
        // <?> is a wildcard: any container screen, whatever menu type it has
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Slot slot = screen.getHoveredSlot();
        if (slot == null) return;
        ItemStack stack = slot.getItem();
        if (!(stack.getItem() instanceof BookItem book) || stack.getCount() != 1) return;
        if (event.getScrollDeltaY() == 0) return;

        // Scrolling down moves forward through the pages, like the bundle
        int direction = event.getScrollDeltaY() < 0 ? 1 : -1;
        int page = book.nextPage(stack, direction);
        book.setPage(stack, page);
        ClientPacketDistributor.sendToServer(new SelectBookPagePayload(slot.index, page));
        event.setCanceled(true); // don't also scroll the screen (e.g. the creative tab list)
    }
}
