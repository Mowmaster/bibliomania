package com.mowmaster.bibliomania.Blocks.BarterBook;

import com.mowmaster.bibliomania.Blocks.Bookcase.ShelvedBook;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.ServerConfig;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// The Book of Barter as an item. It starts empty:
// - Right-click a grown piglin: it goes into the book (gone from the world). Only then can the book be placed and barter.
// - In inventories, left-click gold (ingots or blocks) onto the book, or the book onto gold: the whole stack goes in
//   (as far as it fits); the same for a nether star or heavy core (one used). Gold can't be taken back out.
// - In a player's inventory it barters too: the items go into the inventory, and drop at the player's feet once
//   it's full.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public class BarterBookItem extends BlockItem implements ShelvedBook
{
    // On a Bookcase (ShelvedBook): bartering goes on as in an inventory, the goods into the bookcase's item books
    // (what doesn't fit pops out of the front)
    @Override
    public boolean shelfTick(ItemStack book, ServerLevel level, BookcaseBlockEntity shelf, int slot)
    {
        BarterActions.Result result = BarterActions.tick(level, contents(book), shelf.center());
        if (result.contents() == null) return false;
        setContents(book, result.contents());
        for (ItemStack item : result.items()) shelf.storeOrDrop(item);
        return true;
    }

    public BarterBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    public static BarterContents contents(ItemStack stack)
    {
        BarterContents contents = stack.get(DataComponentRegistry.BARTER_CONTENTS.get());
        return contents != null ? contents : BarterContents.EMPTY;
    }

    public static void setContents(ItemStack stack, BarterContents contents)
    {
        stack.set(DataComponentRegistry.BARTER_CONTENTS.get(), contents);
    }

    // ---- Taking in a piglin ----

    // NeoForge's EntityInteract comes before the piglin reacts to the click
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        ItemStack book = event.getItemStack();
        if (!(book.getItem() instanceof BarterBookItem) || !(event.getTarget() instanceof Piglin piglin)) return;
        Player player = event.getEntity();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        BarterContents contents = contents(book);
        if (contents.piglin() || piglin.isBaby())
        {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return;
        }
        if (event.getLevel() instanceof ServerLevel serverLevel)
        {
            setContents(book, contents.withPiglin());
            serverLevel.sendParticles(ParticleTypes.POOF, piglin.getX(), piglin.getY() + 1, piglin.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            serverLevel.playSound(null, piglin.blockPosition(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.PLAYERS, 1f, 1f);
            piglin.discard();
        }
    }

    // Can't be placed until it has a piglin
    @Override
    public InteractionResult place(BlockPlaceContext context)
    {
        if (!contents(context.getItemInHand()).piglin()) return MobBookActions.refusePlacing(context);
        return super.place(context);
    }

    // ---- Bartering in an inventory ----

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot)
    {
        super.inventoryTick(stack, level, entity, slot);
        if (!(entity instanceof Player player) || stack.getCount() != 1) return;
        BarterActions.Result result = BarterActions.tick(level, contents(stack), player.position());
        if (result.contents() == null) return;
        setContents(stack, result.contents());
        // Into the inventory; what doesn't fit drops at the player's feet
        for (ItemStack item : result.items()) StorageInteractions.giveToPlayer(player, item);
    }

    // ---- Inventory clicks: gold and upgrades ----

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (self.getCount() != 1 || clickAction != ClickAction.PRIMARY || other.isEmpty()) return false;
        return give(self, other, player);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        ItemStack target = slot.getItem();
        if (self.getCount() != 1 || clickAction != ClickAction.PRIMARY || target.isEmpty() || !slot.allowModification(player)) return false;
        boolean handled = give(self, target, player);
        if (handled) slot.setChanged();
        return handled;
    }

    // Gold (as much of the stack as fits) or an upgrade (one) from `item` into the book. false if it's neither.
    // The Reading Lectern standing in for this book: gold for the piglin (as much as fits), or an upgrade. Server.
    public static boolean lecternUse(ItemStack book, ItemStack held, Player player)
    {
        return !held.isEmpty() && give(book, held, player);
    }

    private static boolean give(ItemStack book, ItemStack item, Player player)
    {
        BarterContents contents = contents(book);
        int fits = BarterActions.goldItemsThatFit(contents, item);
        BarterContents changed = null;
        int used = 0;
        if (fits > 0)
        {
            changed = contents.withGold(contents.gold() + fits * BarterActions.goldValue(item));
            used = fits;
        }
        else if (BarterActions.isUpgrade(item))
        {
            changed = BarterActions.applyUpgrade(contents, item);
            used = 1;
        }
        else if (BarterActions.goldValue(item) == 0)
        {
            return false;
        }
        if (changed == null)
        {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return true;
        }
        if (!player.level().isClientSide())
        {
            setContents(book, changed);
            item.shrink(used);
        }
        player.playSound(SoundEvents.PIGLIN_ADMIRING_ITEM, 0.6f, 1f);
        return true;
    }

    // ---- Tooltip ----

    // "Gold: 42/4096", "Next barter in: 0:04", "Barters 2 at a time (2 gold)", "Faster: 3"
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        BarterContents contents = contents(stack);
        if (!contents.piglin()) tooltip.accept(line("no_piglin").withStyle(ChatFormatting.GRAY));
        tooltip.accept(line("gold", contents.gold(), ServerConfig.barterMaxGold()).withStyle(ChatFormatting.GOLD));
        if (contents.nextDrop() > 0 && context.level() != null)
        {
            long ticks = Math.max(0, contents.nextDrop() - context.level().getGameTime());
            long seconds = (ticks + 19) / 20;
            tooltip.accept(line("next", String.format("%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.GRAY));
        }
        else if (contents.piglin() && contents.gold() <= 0)
        {
            tooltip.accept(line("empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (contents.yields() > 0) tooltip.accept(line("yields", contents.perBarter()).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (contents.speedUps() > 0) tooltip.accept(line("speed", contents.speedUps()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static MutableComponent line(String name, Object... args)
    {
        return Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".barter_book." + name, args);
    }
}
