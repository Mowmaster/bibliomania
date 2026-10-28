package com.mowmaster.bibliomania.EventHandlers;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.BibliomaniaTags;
import com.mowmaster.bibliomania.Items.ItemRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

// Cutting grass (anything in #bibliomania:grass_fiber_plants) with a knife sometimes drops grass fibers.
// 1.20 did this with a global loot modifier. 26.3 rewrote loot conditions, so this uses the block break event
// instead, which is simpler to follow and doesn't depend on the loot JSON format.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public class GrassFiberHandler
{
    private static final float FIBER_CHANCE = 0.2f;

    // LOWEST so that if another mod cancels the break, we never see it (cancelled events skip later listeners)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BreakBlockEvent event)
    {
        // The event fires on both sides; only the server drops items
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (player.isCreative()) return;
        if (!event.getState().is(BibliomaniaTags.GRASS_FIBER_PLANTS)) return;
        if (!player.getMainHandItem().is(BibliomaniaTags.KNIVES)) return;

        if (level.getRandom().nextFloat() < FIBER_CHANCE)
        {
            Block.popResource(level, event.getPos(), new ItemStack(ItemRegistry.FIBERS_GRASS.get()));
        }
    }
}
