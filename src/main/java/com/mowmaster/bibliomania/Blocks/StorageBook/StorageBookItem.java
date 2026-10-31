package com.mowmaster.bibliomania.Blocks.StorageBook;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// A storage book as an item. Everything it does is its type's StorageBookBehaviour; this class only passes the
// item's hooks on. The contents are in "mowlib:transport_contents" (opened with BookStorage).
// The durability-style bar under the item shows how full it is, in the type's colour.
public class StorageBookItem extends BlockItem
{
    public StorageBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    public StorageBookBlock book()
    {
        return (StorageBookBlock) getBlock();
    }

    private StorageBookBehaviour behaviour()
    {
        return book().behaviour();
    }

    // Item#getPlayerPOVHitResult is protected; behaviours aren't Items, so they ray trace through this
    public static BlockHitResult rayTrace(Level level, Player player, ClipContext.Fluid fluidMode)
    {
        return getPlayerPOVHitResult(level, player, fluidMode);
    }

    // ---- In the hand ----

    // Right-click on a block: the behaviour's, or (null) place the book like any block item
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        InteractionResult result = behaviour().useOnHeld(context);
        return result != null ? result : super.useOn(context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        return behaviour().useHeld(level, player, hand);
    }

    // NeoForge calls this when the holder swings their arm (a left-click, in the air or at something).
    // The swing reaches the server as its own packet, so this runs there too. true = the swing is handled.
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand)
    {
        if (entity instanceof Player player && !player.level().isClientSide() && behaviour().swingHeld(stack, player, hand)) return false;
        return super.onEntitySwing(stack, entity, hand);
    }

    // ---- In an inventory ----

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (self.getCount() != 1 || !slot.allowModification(player)) return false;
        return behaviour().clickedWith(self, other, slot, clickAction, player);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        if (self.getCount() != 1 || slot.getItem().isEmpty() || !slot.allowModification(player)) return false;
        return behaviour().clickedOnto(self, slot, clickAction, player);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot)
    {
        super.inventoryTick(stack, level, entity, slot);
        if (entity instanceof Player player && stack.getCount() == 1) behaviour().inventoryTick(stack, level, player);
    }

    // ---- Tooltip and fill bar ----

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        BookStorage storage = BookStorage.open(stack);
        if (storage != null) behaviour().appendTooltip(storage, tooltip);
    }

    @Override
    public boolean isBarVisible(ItemStack stack)
    {
        BookStorage storage = BookStorage.open(stack);
        return storage != null && storage.amount() > 0;
    }

    // 0 to 13 pixels wide, like a durability bar
    @Override
    public int getBarWidth(ItemStack stack)
    {
        BookStorage storage = BookStorage.open(stack);
        if (storage == null) return 0;
        return Mth.clamp(Math.round(13f * storage.amount() / storage.capacity()), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack)
    {
        return book().type().getRgb();
    }
}
