package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
// Books with activation (fluids, experience) are placed like any book until activated (see "Activation" below).
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

    // ---- Activation ----
    // Books whose behaviour usesActivation (fluids, experience) only do their hand and inventory actions while
    // activated. Sneak + right-click (in the air or on a block) or the activation key (BibliomaniaKeys, also over the
    // book in an inventory) turns it on and off. Active books shimmer like enchanted items.

    // The activation key's name (its KeyMapping is in the client-only ClientSide/BibliomaniaKeys; items load on servers
    // too, so they only know the name). Component.keybind shows whatever key it's bound to.
    public static final String TOGGLE_KEY = "key." + BibliomaniaReferences.MODID + ".toggle_book";

    public static boolean isActive(ItemStack stack)
    {
        return stack.getOrDefault(DataComponentRegistry.ACTIVATED.get(), false);
    }

    // Whether the book's actions run now: always for books without activation, otherwise only while active
    public static boolean actionsOn(ItemStack stack)
    {
        return !(stack.getItem() instanceof StorageBookItem item) || !item.behaviour().usesActivation() || isActive(stack);
    }

    // Turns it on or off (server), with a sound and a message over the hotbar. false if this book has no activation.
    public static boolean toggle(ItemStack stack, Player player)
    {
        if (!(stack.getItem() instanceof StorageBookItem item) || !item.behaviour().usesActivation()) return false;
        boolean active = !isActive(stack);
        stack.set(DataComponentRegistry.ACTIVATED.get(), active);
        player.level().playSound(null, player.blockPosition(), active ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.4f, 1.6f);
        player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".storage_book." + (active ? "activated" : "deactivated"),
                stack.getHoverName()));
        return true;
    }

    // The enchantment shimmer while active
    @Override
    public boolean isFoil(ItemStack stack)
    {
        return super.isFoil(stack) || isActive(stack);
    }

    // ---- In the hand ----

    // Right-click on a block: sneak toggles activation; an active book does its action; otherwise (null) place the
    // book like any block item
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (behaviour().usesActivation())
        {
            if (player != null && player.isShiftKeyDown()) return toggleHeld(context.getLevel(), player, context.getItemInHand());
            if (!isActive(context.getItemInHand())) return super.useOn(context);
        }
        InteractionResult result = behaviour().useOnHeld(context);
        return result != null ? result : super.useOn(context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (behaviour().usesActivation())
        {
            if (player.isShiftKeyDown()) return toggleHeld(level, player, stack);
            if (!isActive(stack)) return InteractionResult.PASS;
        }
        return behaviour().useHeld(level, player, hand);
    }

    // CONSUME: no arm swing (a swing would be taken for a left-click by the experience book)
    private static InteractionResult toggleHeld(Level level, Player player, ItemStack stack)
    {
        if (!level.isClientSide()) toggle(stack, player);
        return InteractionResult.CONSUME;
    }

    // NeoForge calls this when the holder swings their arm (a left-click, in the air or at something).
    // The swing reaches the server as its own packet, so this runs there too. true = the swing is handled.
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand)
    {
        if (entity instanceof Player player && !player.level().isClientSide() && actionsOn(stack)
                && behaviour().swingHeld(stack, player, hand)) return false;
        return super.onEntitySwing(stack, entity, hand);
    }

    // ---- In an inventory ----

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (self.getCount() != 1 || !slot.allowModification(player) || !actionsOn(self)) return false;
        return behaviour().clickedWith(self, other, slot, clickAction, player);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        if (self.getCount() != 1 || slot.getItem().isEmpty() || !slot.allowModification(player) || !actionsOn(self)) return false;
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
        if (behaviour().usesActivation())
        {
            // "Activated" / "Not activated: sneak + right-click or [V]" (the key's current name, from the key binds)
            tooltip.accept(isActive(stack)
                    ? Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".storage_book.active").withStyle(ChatFormatting.LIGHT_PURPLE)
                    : Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".storage_book.inactive",
                            Component.keybind(TOGGLE_KEY)).withStyle(ChatFormatting.DARK_GRAY));
        }
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
