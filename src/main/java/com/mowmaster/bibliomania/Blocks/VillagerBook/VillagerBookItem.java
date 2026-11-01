package com.mowmaster.bibliomania.Blocks.VillagerBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

// The Trader's Ledger as an item.
// - Right-click a grown villager with an empty book: it goes into the book, trades, level and all
// - Or a wandering trader: its trades go in (it has no level or profession, so it can't level up, change job, be
//   reset or be bred)
// - Right-click a workstation block (lectern, composter, smithing table...) with a book holding a villager: the
//   villager takes that profession (level 1, new trades); the block stays where it is
// - In an inventory the trades still restock (with the bell) after the timer; the tooltip lists the trades
// - Upgrades, left-clicked onto the book (or the book onto them): enchanted golden apples take emeralds off the prices,
//   nether stars make it restock sooner (see VillagerBookActions)
// Both right-clicks are NeoForge events, which come before the villager's trading screen or the block's own screen.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public class VillagerBookItem extends BlockItem
{
    public VillagerBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    public static VillagerContents contents(ItemStack stack)
    {
        VillagerContents contents = stack.get(DataComponentRegistry.VILLAGER_CONTENTS.get());
        return contents != null ? contents : VillagerContents.EMPTY;
    }

    public static void setContents(ItemStack stack, VillagerContents contents)
    {
        stack.set(DataComponentRegistry.VILLAGER_CONTENTS.get(), contents);
    }

    // ---- Capturing a villager ----

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        ItemStack book = event.getItemStack();
        if (!(book.getItem() instanceof VillagerBookItem)) return;
        Entity target = event.getTarget();
        if (!(target instanceof Villager) && !(target instanceof WanderingTrader)) return;
        Player player = event.getEntity();
        // Cancelled with a result: the villager's trading screen doesn't open, and the arm swings
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (contents(book).hasVillager() || !VillagerBookActions.canCapture(target))
        {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return;
        }
        if (event.getLevel() instanceof ServerLevel serverLevel)
        {
            VillagerContents captured = target instanceof Villager villager
                    ? VillagerBookActions.capture(contents(book), villager)
                    : VillagerBookActions.capture(contents(book), (WanderingTrader) target);
            if (captured == null) return;
            setContents(book, captured);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.5, 0.3, 0);
            serverLevel.playSound(null, target.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.PLAYERS, 1f, 1f);
            target.discard(); // gone from the world, now in the book
        }
    }

    // Can't be placed until it has a villager
    @Override
    public InteractionResult place(BlockPlaceContext context)
    {
        if (!contents(context.getItemInHand()).hasVillager()) return MobBookActions.refusePlacing(context);
        return super.place(context);
    }

    // ---- Workstations in the world ----

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        ItemStack book = event.getItemStack();
        if (!(book.getItem() instanceof VillagerBookItem)) return;
        VillagerContents contents = contents(book);
        // Wandering traders don't take jobs: the block works as normal
        if (!contents.hasVillager() || contents.wandering()) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Optional<Holder<VillagerProfession>> profession = VillagerBookActions.professionFor(state);
        if (profession.isEmpty()) return;
        // Cancelled with a result: the block's own screen (smithing table, barrel...) doesn't open
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level instanceof ServerLevel serverLevel)
        {
            VillagerContents changed = VillagerBookActions.setProfession(serverLevel, contents, profession.get(), state.getBlock());
            if (changed != null)
            {
                setContents(book, changed);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0);
                serverLevel.playSound(null, pos, SoundEvents.VILLAGER_WORK_LIBRARIAN, SoundSource.PLAYERS, 1f, 1f);
            }
        }
    }

    // ---- Restocking in an inventory ----

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot)
    {
        super.inventoryTick(stack, level, entity, slot);
        VillagerContents contents = contents(stack);
        if (contents.restockAt() == 0 || level.getGameTime() < contents.restockAt()) return;
        setContents(stack, VillagerBookActions.restock(contents));
        level.playSound(null, entity.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6f, 1f);
    }

    // ---- Inventory clicks: upgrades ----
    // Left-click an enchanted golden apple (cheaper) or a nether star (restocks sooner) onto the book, or the book
    // onto one: one is used. (Like the other books, either way round.)

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (self.getCount() != 1 || clickAction != ClickAction.PRIMARY || !VillagerBookActions.isUpgrade(other)) return false;
        return upgrade(self, other, player);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        ItemStack target = slot.getItem();
        if (self.getCount() != 1 || clickAction != ClickAction.PRIMARY || !VillagerBookActions.isUpgrade(target)
                || !slot.allowModification(player)) return false;
        boolean handled = upgrade(self, target, player);
        slot.setChanged();
        return handled;
    }

    private static boolean upgrade(ItemStack book, ItemStack item, Player player)
    {
        VillagerContents upgraded = VillagerBookActions.applyUpgrade(contents(book), item);
        if (upgraded == null)
        {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return true;
        }
        if (!player.level().isClientSide())
        {
            setContents(book, upgraded);
            item.shrink(1);
        }
        player.playSound(SoundEvents.VILLAGER_CELEBRATE, 0.6f, 1f);
        return true;
    }

    // ---- Tooltip ----

    // "Librarian, Apprentice (40% to next level)", the restock timer, the upgrades, then every trade:
    // "1 Paper, 24 Emerald -> 1 Enchanted Book (2/12)"
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        VillagerContents contents = contents(stack);
        if (contents.villager().isEmpty())
        {
            tooltip.accept(line("empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        VillagerData data = contents.villager().get();
        if (contents.wandering())
        {
            // "Wandering Trader" (the entity's own name)
            tooltip.accept(Component.empty().append(EntityTypes.WANDERING_TRADER.getDescription()).withStyle(ChatFormatting.GREEN));
        }
        else if (data.level() >= VillagerBookActions.MAX_LEVEL)
        {
            tooltip.accept(line("villager_max", data.profession().value().name(), VillagerBookActions.levelName(data.level())).withStyle(ChatFormatting.GREEN));
        }
        else
        {
            tooltip.accept(line("villager", data.profession().value().name(), VillagerBookActions.levelName(data.level()),
                    VillagerBookActions.percentToNextLevel(data.level(), contents.xp())).withStyle(ChatFormatting.GREEN));
        }
        // "Trades restock in: 7:12" (counts down live; the tooltip is rebuilt every frame)
        if (contents.restockAt() > 0 && context.level() != null)
        {
            long seconds = (Math.max(0, contents.restockAt() - context.level().getGameTime()) + 19) / 20;
            tooltip.accept(line("restock", String.format("%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.GRAY));
        }
        // "Discounts: 3/10, faster restocks: 5"
        if (contents.discounts() > 0 || contents.speedUps() > 0)
        {
            tooltip.accept(line("upgrades", contents.discounts(), ServerConfig.villagerMaxDiscounts(), contents.speedUps())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        for (int index = 0; index < contents.offers().size(); index++)
        {
            MerchantOffer offer = contents.offers().get(index);
            MutableComponent price = describe(offer.getCostA());
            if (!offer.getCostB().isEmpty()) price = price.append(", ").append(describe(offer.getCostB()));
            MutableComponent text = line("trade", price, describe(offer.getResult()), offer.getUses(), offer.getMaxUses());
            boolean shown = index == Math.floorMod(contents.page(), contents.offers().size());
            tooltip.accept(text.withStyle(offer.isOutOfStock() ? ChatFormatting.DARK_GRAY : shown ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
    }

    private static MutableComponent describe(ItemStack stack)
    {
        return Component.empty().append(stack.getCount() + " ").append(stack.getHoverName());
    }

    private static MutableComponent line(String name, Object... args)
    {
        return Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".villager_book." + name, args);
    }
}
