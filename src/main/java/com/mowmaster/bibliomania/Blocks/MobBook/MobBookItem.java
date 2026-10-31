package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Optional;
import java.util.function.Consumer;

// The Bestiary as an item.
// - Right-click an adult animal (not a pet, not named): it goes into the book. Same kind only; one kind per book.
// - In inventories, clicking the book with something on your cursor:
//     breeding food, left-click (insert): breeds the mobs, using only as much food as they can eat
//     a sword, right-click (extract):      kills one; the drops go into your inventory and the XP to you
//     anything else:                       the bundle "can't" sound, nothing happens
// - Tooltip: text lines (appendHoverText) for the mob, count, babies and breeding; the mob's spawn egg as a picture.
public class MobBookItem extends BlockItem
{
    public MobBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    public static MobBookContents contents(ItemStack stack)
    {
        MobBookContents contents = stack.get(DataComponentRegistry.MOB_CONTENTS.get());
        return contents != null ? contents : MobBookContents.EMPTY;
    }

    // What it holds now (babies whose cooldown is over count as adults)
    private static MobBookContents settled(ItemStack stack, Level level)
    {
        return contents(stack).settle(level.getGameTime());
    }

    private static void setContents(ItemStack stack, MobBookContents contents)
    {
        stack.set(DataComponentRegistry.MOB_CONTENTS.get(), contents);
    }

    // ---- Capturing ----

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand)
    {
        Level level = player.level();
        // Read and write the book in the player's hand, not `stack`: in creative, vanilla passes a copy here,
        // so changes to `stack` were thrown away (and the mob was gone)
        ItemStack book = player.getItemInHand(hand);
        MobBookContents contents = settled(book, level);
        if (!MobBookActions.canCapture(target) || !contents.canCapture(target.getType()))
        {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel)
        {
            setContents(book, contents.withCaptured(target.getType()));
            serverLevel.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    10, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, target.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
            target.discard(); // gone from the world, now a count in the book
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Inventory clicks ----

    // Holding something (or nothing), clicking the book in a slot
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (self.getCount() != 1) return false;
        // Left-click with an empty hand picks the book up as normal
        if (clickAction == ClickAction.PRIMARY && other.isEmpty()) return false;

        Level level = player.level();
        MobBookContents contents = settled(self, level);

        // Breeding food, left-click: breed
        if (clickAction == ClickAction.PRIMARY && slot.allowModification(player) && MobBookActions.isBreedingFood(level, contents, other))
        {
            int eaten = Math.min(other.getCount(), contents.maxFeed());
            if (eaten > 0)
            {
                // Changes are made on the server and sent to the client; the client only plays the sound
                if (level instanceof ServerLevel)
                {
                    setContents(self, contents.withFed(eaten, level.getGameTime()));
                    other.consume(eaten, player);
                    slotsChanged(player);
                }
                player.playSound(SoundEvents.GENERIC_EAT.value(), 0.6f, 1f);
                return true;
            }
        }

        // A sword, right-click: kill one, drops and XP to the player
        if (clickAction == ClickAction.SECONDARY && slot.allowModification(player) && MobBookActions.isKillWeapon(other)
                && contents.adults() > 0 && contents.type().isPresent())
        {
            if (level instanceof ServerLevel serverLevel)
            {
                int killed = MobBookActions.kill(serverLevel, contents.type().get(), 1, player, player.position(), true);
                setContents(self, contents.withKilled(killed));
                slotsChanged(player);
            }
            return true;
        }

        // Anything else isn't allowed
        player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
        return true;
    }

    // Tells the open container the book changed, so the change is sent to the client
    private static void slotsChanged(Player player)
    {
        player.containerMenu.slotsChanged(player.getInventory());
    }

    // ---- Tooltip ----

    // The text lines. Rebuilt every frame while the tooltip is open, so the timers count down live.
    // context.level() is the client's level here (null on the main menu, hence the check).
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        Level level = context.level();
        long now = level != null ? level.getGameTime() : 0;
        MobBookContents contents = contents(stack).settle(now);
        if (contents.isEmpty() || contents.type().isEmpty())
        {
            tooltip.accept(line("empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        // "Cow x12"
        tooltip.accept(line("count", contents.type().get().getDescription(), contents.adults()).withStyle(ChatFormatting.GRAY));
        // "+3 growing (4:12)": babies become adults when the cooldown ends
        if (contents.growing() > 0)
        {
            tooltip.accept(line("growing", contents.growing(), time(contents.cooldownLeft(now))).withStyle(ChatFormatting.GREEN));
        }
        // "Can breed: 5" (adults that can still eat), or "Can breed in: 3:41" when they all have
        int canBreed = contents.maxFeed();
        if (canBreed > 0)
        {
            tooltip.accept(line("can_breed", canBreed).withStyle(ChatFormatting.YELLOW));
        }
        else if (contents.onCooldown(now))
        {
            tooltip.accept(line("breed_in", time(contents.cooldownLeft(now))).withStyle(ChatFormatting.GRAY));
        }
    }

    // Component.translatable fills %s in the lang line with args; it returns a MutableComponent, so withStyle works
    private static MutableComponent line(String name, Object... args)
    {
        return Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".mob_book." + name, args);
    }

    // Ticks as m:ss, rounded up so it reaches 0:00 when done. %02d pads to two digits (4:05, not 4:5).
    private static String time(long ticks)
    {
        long seconds = (ticks + 19) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // The picture: the mob's spawn egg with the count (drawn by ClientMobBookTooltip). None for an empty book.
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack)
    {
        MobBookContents contents = contents(stack);
        if (contents.isEmpty()) return Optional.empty();
        return Optional.of(new MobBookTooltip(contents));
    }
}
