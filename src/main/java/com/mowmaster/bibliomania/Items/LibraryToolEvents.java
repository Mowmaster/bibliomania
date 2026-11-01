package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.util.TriState;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// The Librarian's Quill and Seal work on library blocks that have right-clicks of their own (a lectern would store
// the quill, a bookcase would hand out a book): with either in hand, the block's own right-click is skipped and the
// tool's useOn runs instead. TriState.FALSE: don't use the block, whatever vanilla would do.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class LibraryToolEvents
{
    private LibraryToolEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        Item held = event.getItemStack().getItem();
        if (held instanceof LibraryQuillItem || held instanceof LibrarySealItem) event.setUseBlock(TriState.FALSE);
    }
}
