package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.mowlib.keys.IActivatable;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// A book with an "activated" mode (Book of Fluids, Book of Experience, Builder's Book): MowLib's activation key
// (IActivatable, default V) over it in an inventory or held, or sneak + right-click, turns it on and off. The mode is
// MowLib's mowlib:activated component (old worlds' bibliomania:activated is aliased to it in DataComponentRegistry).
public interface ActivatableBook extends IActivatable
{
    // The activation key's name, for tooltips (Component.keybind shows whatever key it's bound to). A compile-time
    // constant, so naming it doesn't load the client-only key class on a server.
    String TOGGLE_KEY = com.mowmaster.mowlib.client.MowLibKeyMappings.ACTIVATE_NAME;

    // Whether this book (this stack) has the mode at all
    boolean usesActivation(ItemStack stack);

    // ---- IActivatable: the key flips the mode ----

    @Override
    default boolean canActivate(ItemStack stack)
    {
        return canToggle(stack);
    }

    @Override
    default void activate(ItemStack stack, ServerPlayer player)
    {
        toggle(stack, player);
    }

    static boolean isActive(ItemStack stack)
    {
        return IActivatable.isActive(stack);
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
        stack.set(MowLibDataComponents.ACTIVATED.get(), active);
        player.level().playSound(null, player.blockPosition(), active ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.4f, 1.6f);
        player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".storage_book." + (active ? "activated" : "deactivated"),
                stack.getHoverName()));
        return true;
    }
}
