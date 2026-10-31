package com.mowmaster.bibliomania.Blocks.VillagerBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
// - Right-click a workstation block (lectern, composter, smithing table...) with a book holding a villager: the
//   villager takes that profession (level 1, new trades); the block stays where it is
// - In an inventory the trades still restock (with the bell) after the timer; the tooltip lists the trades
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
        if (!(target instanceof Villager)) return;
        Player player = event.getEntity();
        // Cancelled with a result: the villager's trading screen doesn't open, and the arm swings
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (contents(book).hasVillager() || !VillagerBookActions.canCapture(target))
        {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return;
        }
        if (event.getLevel() instanceof ServerLevel serverLevel && target instanceof Villager villager)
        {
            setContents(book, VillagerBookActions.capture(villager));
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.5, 0.3, 0);
            serverLevel.playSound(null, target.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.PLAYERS, 1f, 1f);
            villager.discard(); // gone from the world, now in the book
        }
    }

    // ---- Workstations in the world ----

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        ItemStack book = event.getItemStack();
        if (!(book.getItem() instanceof VillagerBookItem)) return;
        VillagerContents contents = contents(book);
        if (!contents.hasVillager()) return;
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

    // ---- Tooltip ----

    // "Librarian, level 3 (40 xp)", then every trade: "> 1 Paper, 24 Emerald -> Enchanted Book (2/12)"
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
        tooltip.accept(line("villager", data.profession().value().name(), data.level(), contents.xp()).withStyle(ChatFormatting.GREEN));
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
