package com.mowmaster.bibliomania.ClientSide;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryPriority;
import com.mowmaster.bibliomania.Items.PriorityScrollItem;
import com.mowmaster.bibliomania.Network.AdjustPriorityPayload;
import com.mowmaster.mowlib.client.MowLibKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// MowLib's page keys (Page Down / Page Up) while holding a Priority Scroll and looking at a block with a priority:
// +1 / -1 (sneaking: 10). MowLib reads those key presses in its own client tick handler; this one runs first
// (EventPriority.HIGH) and takes the presses (consumeClick) only in that case, so MowLib's page turning works as usual
// otherwise. Client only.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID, value = Dist.CLIENT)
public final class PriorityKeys
{
    private PriorityKeys() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gui.screen() != null || !holdsScroll(player)) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!LibraryPriority.hasPriority(minecraft.level, hit.getBlockPos())) return;
        int step = LibraryPriority.step(player);
        while (MowLibKeyMappings.PAGE_NEXT.consumeClick()) ClientPacketDistributor.sendToServer(new AdjustPriorityPayload(hit.getBlockPos(), step));
        while (MowLibKeyMappings.PAGE_PREVIOUS.consumeClick()) ClientPacketDistributor.sendToServer(new AdjustPriorityPayload(hit.getBlockPos(), -step));
    }

    private static boolean holdsScroll(Player player)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            if (player.getItemInHand(hand).getItem() instanceof PriorityScrollItem) return true;
        }
        return false;
    }
}
