package com.mowmaster.bibliomania.ClientSide;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.mowlib.paging.IPageable;
import com.mowmaster.mowlib.paging.IPagedStorageItem;
import com.mowmaster.mowlib.paging.PagedContents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.EnumMap;
import java.util.Map;

// When the page of a book in your hand changes (MowLib's page keys, or anything else turning it), shows what's on the
// new page above the hotbar for a moment: "Page 3/8: Bread x12". Works for every paged item (MowLib IPageable), so
// other mods' books too; storage books show the page's item, other paged items just the page number.
// Page turning is MowLib's (the server changes the page and the item is synced back), so instead of hooking into it,
// this watches the held item's page once a client tick and reacts when it changes. Client only.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID, value = Dist.CLIENT)
public final class HeldPageMessages
{
    private HeldPageMessages() {}

    // What was in each hand last tick: the hotbar slot, the item, and its page. A record holds the three together.
    private record Seen(int slot, Item item, int page) {}
    // EnumMap: a map keyed by an enum (here the two hands), compact and fast
    private static final Map<InteractionHand, Seen> LAST = new EnumMap<>(InteractionHand.class);

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null)
        {
            LAST.clear();
            return;
        }
        int selectedSlot = player.getInventory().getSelectedSlot();
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof IPageable pageable) || stack.getCount() != 1)
            {
                LAST.remove(hand);
                continue;
            }
            int slot = hand == InteractionHand.MAIN_HAND ? selectedSlot : -1;
            Seen now = new Seen(slot, stack.getItem(), pageable.getPage(stack));
            Seen before = LAST.put(hand, now);
            // Only a page change of the same book in the same place counts (not switching to another book), and only
            // while no screen is open (scrolling over the book in an inventory shows it in the tooltip already)
            if (before != null && before.slot() == now.slot() && before.item() == now.item() && before.page() != now.page()
                    && minecraft.gui.screen() == null)
            {
                // false: plain white text (true makes it cycle colours, like the "now playing" message)
                minecraft.gui.hud.setOverlayMessage(describePage(stack, pageable, now.page()), false);
            }
        }
    }

    // "Page 3/8: Bread x12", "Page 3/8: empty", or just "Page 3/8" for paged items that don't store items
    private static Component describePage(ItemStack stack, IPageable pageable, int page)
    {
        String key = "message." + BibliomaniaReferences.MODID + ".page";
        int number = page + 1;
        int pages = pageable.getPageCount(stack);
        if (!(stack.getItem() instanceof IPagedStorageItem storage)) return Component.translatable(key, number, pages);
        PagedContents contents = storage.contents(stack);
        ResourceStack<ItemResource> onPage = contents.stack(page);
        if (onPage.isEmpty()) return Component.translatable(key + "_empty", number, pages);
        Component name = onPage.resource().toStack(1).getHoverName();
        return Component.translatable(key + "_item", number, pages, name, onPage.amount());
    }
}
