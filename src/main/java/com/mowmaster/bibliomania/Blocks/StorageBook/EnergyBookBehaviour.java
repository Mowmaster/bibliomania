package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.bibliomania.ServerConfig;
import com.mowmaster.mowlib.transport.AmountResource;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;

// The Book of Energy, in FE (MowLib's EnergyTransportType wraps NeoForge's energy handlers).
// - Placed: cables and machines put energy in and take it out (it offers NeoForge's energy capability, see
//   StorageBookEvents); right-click with a battery fills the book from it, sneak + right-click charges the battery
// - Inventory: charges every other item in your inventory that takes energy, up to
//   ServerConfig.energyChargeRate() FE a tick in total (2000 by default); batteries clicked on it work like placed
public class EnergyBookBehaviour implements StorageBookBehaviour
{
    // Clicking with batteries: the same as the generic behaviour
    @Override
    public InteractionResult useItemOnPlaced(ItemStack held, StorageBookBlockEntity book, Level level, BlockPos pos, Player player, InteractionHand hand)
    {
        return StorageBookBehaviours.GENERIC.useItemOnPlaced(held, book, level, pos, player, hand);
    }

    @Override
    public boolean clickedWith(ItemStack book, ItemStack cursor, Slot slot, ClickAction action, Player player)
    {
        return StorageBookBehaviours.GENERIC.clickedWith(book, cursor, slot, action, player);
    }

    @Override
    public boolean clickedOnto(ItemStack book, Slot slot, ClickAction action, Player player)
    {
        return StorageBookBehaviours.GENERIC.clickedOnto(book, slot, action, player);
    }

    @Override
    public void inventoryTick(ItemStack book, ServerLevel level, Player player)
    {
        BookStorage storage = BookStorage.open(book);
        if (storage == null || storage.amount() <= 0) return;
        ResourceHandler<AmountResource> energy = storage.handler(TransportTypes.ENERGY.get());
        int budget = ServerConfig.energyChargeRate();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && budget > 0; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack == book) continue;
            // The item's energy storage, if it has one (null for everything else)
            ResourceHandler<AmountResource> target = TransportTypes.ENERGY.get().getItemHandler(ItemAccess.forPlayerSlot(player, slot));
            if (target == null) continue;
            budget -= StorageBookBehaviours.move(TransportTypes.ENERGY.get(), energy, target, budget);
        }
    }
}
