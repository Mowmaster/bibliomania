package com.mowmaster.bibliomania.Blocks.CropBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// Left-clicking a placed Farmer's Almanac harvests it once ripe (with the hand or any tool). It's an event for the
// same reason as the mob books': creative players never "attack" blocks. The click is always cancelled on a crop
// book, so nothing is mined.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class CropBookEvents
{
    private CropBookEvents() {}

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Level level = event.getLevel();
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof CropBookBlock)) return;
        event.setCanceled(true);
        if (level instanceof ServerLevel serverLevel) CropBookBlock.harvest(serverLevel, event.getPos(), event.getEntity());
    }
}
