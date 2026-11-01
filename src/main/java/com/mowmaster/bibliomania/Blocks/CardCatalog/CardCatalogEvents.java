package com.mowmaster.bibliomania.Blocks.CardCatalog;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// Sneak + right-click with an item on a Card Catalog drawer re-sets the drawer (CardCatalogBlock#useItemOn). Vanilla
// skips a block's own right-click while sneaking with something in hand, so here the block is told to use it anyway
// (TriState.TRUE), only on drawer fronts: elsewhere sneak-clicks still place blocks against it as normal.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class CardCatalogEvents
{
    private CardCatalogEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (!event.getEntity().isShiftKeyDown() || event.getItemStack().isEmpty()) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (CardCatalogBlock.wantsSneakClick(state, event.getHitVec())) event.setUseBlock(TriState.TRUE);
    }
}
