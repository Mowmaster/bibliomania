package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// A book item with an "activated" mode (the bibliomania:activated component): the Books of Fluids and Experience
// (their hand and inventory actions only work while active) and the Builder's Book (places from a random slot while
// active). Sneak + right-click or the activation key (ClientSide/BibliomaniaKeys, also over the book in an inventory)
// turns it on and off; active books shimmer like enchanted items (each item's isFoil).
public interface ActivatableBook
{
    // The activation key's name (its KeyMapping is in the client-only BibliomaniaKeys; items load on servers too, so
    // they only know the name). Component.keybind shows whatever key it's bound to.
    String TOGGLE_KEY = "key." + BibliomaniaReferences.MODID + ".toggle_book";

    // Whether this book (this stack) has the mode at all
    boolean usesActivation(ItemStack stack);

    static boolean isActive(ItemStack stack)
    {
        return stack.getOrDefault(DataComponentRegistry.ACTIVATED.get(), false);
    }

    // A single book whose activation can be toggled
    static boolean canToggle(ItemStack stack)
    {
        return stack.getCount() == 1 && stack.getItem() instanceof ActivatableBook book && book.usesActivation(stack);
    }

    // Turns it on or off (server), with a sound and a message over the hotbar. false if this isn't such a book.
    static boolean toggle(ItemStack stack, Player player)
    {
        if (!canToggle(stack)) return false;
        boolean active = !isActive(stack);
        stack.set(DataComponentRegistry.ACTIVATED.get(), active);
        player.level().playSound(null, player.blockPosition(), active ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.4f, 1.6f);
        player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".storage_book." + (active ? "activated" : "deactivated"),
                stack.getHoverName()));
        return true;
    }
}
