package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// What a storage book does with its kind of contents. One behaviour per transport type, registered in
// StorageBookBehaviours: fluids, experience and energy are Bibliomania's own; a type with none registered gets
// StorageBookBehaviours.GENERIC (fill from / empty into items that hold that type). Another mod adding a transport
// type can register its own behaviour and a StorageBookBlock for it (see BlockRegistry#registerStorageBook).
//
// Every method has a default that does nothing, so a behaviour only overrides what it uses.
// Clicks are called on BOTH sides (client and server) and must return the same answer on both; only the server
// changes anything. Book contents are opened with BookStorage (items) or StorageBookBlockEntity#storage (placed).
public interface StorageBookBehaviour
{
    // true: the book has an "activated" mode (StorageBookItem): sneak + right-click or the activation key turns it
    // on and off, and the held and inventory actions below only run while it's on (so an inactive book is placed
    // and moved like any item). false: they always run.
    default boolean usesActivation()
    {
        return false;
    }

    // ---- Placed book ----

    // Right-click on the placed book with an item. TRY_WITH_EMPTY_HAND = not handled (useWithoutItemPlaced runs next).
    default InteractionResult useItemOnPlaced(ItemStack held, StorageBookBlockEntity book, Level level, BlockPos pos, Player player, InteractionHand hand)
    {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    // Right-click on the placed book with nothing usable (sneak + empty hand is always "pick up", before this)
    default InteractionResult useWithoutItemPlaced(StorageBookBlockEntity book, Level level, BlockPos pos, Player player)
    {
        return InteractionResult.PASS;
    }

    // Left-click on the placed book. true = handled (the click is cancelled: no mining).
    default boolean leftClickPlaced(StorageBookBlockEntity book, Level level, BlockPos pos, Player player)
    {
        return false;
    }

    // Every tick while placed (server)
    default void serverTickPlaced(StorageBookBlockEntity book, ServerLevel level)
    {
    }

    // ---- Held in the hand ----

    // Right-click on a block. null = place the book as a block (the normal thing for a block item);
    // PASS = go on to useHeld (like right-clicking the air). (With activation: only called while active.)
    default @Nullable InteractionResult useOnHeld(UseOnContext context)
    {
        return null;
    }

    // Right-click (in the air, or on a block when useOnHeld gave PASS)
    default InteractionResult useHeld(Level level, Player player, InteractionHand hand)
    {
        return InteractionResult.PASS;
    }

    // Left-click (any swing of the arm while holding the book). Called on the server. true = handled.
    default boolean swingHeld(ItemStack book, Player player, InteractionHand hand)
    {
        return false;
    }

    // ---- In an inventory ----

    // `cursor` (maybe empty) clicked onto the book in a slot. true = handled (nothing else happens).
    default boolean clickedWith(ItemStack book, ItemStack cursor, Slot slot, ClickAction action, Player player)
    {
        return false;
    }

    // The book on the cursor clicked onto the item in `slot`. true = handled.
    default boolean clickedOnto(ItemStack book, Slot slot, ClickAction action, Player player)
    {
        return false;
    }

    // Every tick while in a player's inventory (server)
    default void inventoryTick(ItemStack book, ServerLevel level, Player player)
    {
    }

    // ---- Tooltip ----

    // The text lines (the contents and capacity by default: "Water: 1000/64000")
    default void appendTooltip(BookStorage storage, Consumer<Component> tooltip)
    {
        Component line = storage.storage().describe();
        tooltip.accept(line != null ? line : Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".storage_book.empty"));
    }
}
