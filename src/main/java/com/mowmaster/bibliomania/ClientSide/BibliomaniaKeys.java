package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.platform.InputConstants;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookItem;
import com.mowmaster.bibliomania.Network.ToggleBookPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Bibliomania's keys (Options > Controls > Key Binds, under "Bibliomania"), and what they do. Client only.
// - Toggle book activation (default V): over a storage book in an inventory, toggles that book; in the world,
//   toggles the held one (main hand first). The server does the toggling (ToggleBookPayload).
// Same pattern as MowLib's page keys (MowLibKeyMappings / PageInputHandler): since 1.21.9 a key's category is a
// KeyMapping.Category (an id) that must be registered too; since 26.3 key codes are SDL scancodes, so always the
// InputConstants.KEY_* constants.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID, value = Dist.CLIENT)
public final class BibliomaniaKeys
{
    private BibliomaniaKeys() {}

    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "bibliomania"));

    public static final KeyMapping TOGGLE_BOOK = new KeyMapping(StorageBookItem.TOGGLE_KEY, InputConstants.KEY_V, CATEGORY);

    // A mod bus event: @EventBusSubscriber sends it there by its type
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event)
    {
        event.registerCategory(CATEGORY);
        event.register(TOGGLE_BOOK);
    }

    // ---- In inventories: the hovered book ----

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event)
    {
        // 26.x passes keys as a KeyEvent; InputConstants.getKey turns it into the Key a KeyMapping compares with
        if (!TOGGLE_BOOK.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()))) return;
        // <?>: any container screen, whatever its menu type
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Slot slot = screen.getHoveredSlot(); // public getter added by NeoForge
        if (slot == null || !isToggleable(slot.getItem())) return;
        ClientPacketDistributor.sendToServer(new ToggleBookPayload(slot.index));
        event.setCanceled(true); // the key did its job; nothing else should use it
    }

    // ---- In the world: the held book ----

    // Key presses outside screens are counted and read here once a tick (consumeClick is true once per press)
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        while (TOGGLE_BOOK.consumeClick())
        {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;
            if (isToggleable(player.getItemInHand(InteractionHand.MAIN_HAND)))
            {
                ClientPacketDistributor.sendToServer(new ToggleBookPayload(ToggleBookPayload.MAIN_HAND));
            }
            else if (isToggleable(player.getItemInHand(InteractionHand.OFF_HAND)))
            {
                ClientPacketDistributor.sendToServer(new ToggleBookPayload(ToggleBookPayload.OFF_HAND));
            }
        }
    }

    // A storage book with an activation mode (Book of Fluids, Book of Experience)
    private static boolean isToggleable(ItemStack stack)
    {
        return stack.getCount() == 1 && stack.getItem() instanceof StorageBookItem item && item.book().behaviour().usesActivation();
    }
}
