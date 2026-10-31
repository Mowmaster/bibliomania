package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

// The Bestiary as an item.
// - Right-click an adult animal (not a pet, not named): it goes into the book. Same kind only; one kind per book.
// - In inventories, either way round (the book on the cursor clicked onto an item, or the item onto the book):
//     breeding food, left-click: breeds the mobs, using only as much food as they can eat
//     a dye, left-click:         recolours dyeable mobs (sheep)
//     a sword, right-click:      kills one; the drops go into your inventory and the XP to you
//     shears, right-click:       shears every adult; the drops go into your inventory
//   Clicking the book with anything else on the cursor plays the bundle "can't" sound; clicking the book (on the
//   cursor) onto anything else just swaps them as normal.
// - Egg layers (chickens...) lay into your inventory every ServerConfig.dropInterval() ticks while the book is in it (MobBookDrops).
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
            // The first mob's look (variant, colour) becomes the book's; later ones just add to the count
            setContents(book, contents.withCaptured(target.getType(), MobAppearance.capture(target)));
            serverLevel.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    10, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, target.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
            target.discard(); // gone from the world, now a count in the book
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Egg laying / shedding in a player's inventory (see MobBookDrops) ----

    // Called every tick on the server for each item in an entity's inventory (26.x: only on the server, hence the
    // ServerLevel; slot is the armour/hand slot it's in, or null for the rest of the inventory)
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot)
    {
        super.inventoryTick(stack, level, entity, slot);
        if (!(entity instanceof Player player) || stack.getCount() != 1) return;
        MobBookDrops.Result result = MobBookDrops.tick(level, settled(stack, level), player.position());
        if (result.contents() == null) return; // nothing changed (almost every tick)
        setContents(stack, result.contents());
        // Into the inventory; what doesn't fit drops at the player's feet
        for (ItemStack item : result.items()) StorageInteractions.giveToPlayer(player, item);
    }

    // ---- Inventory clicks ----
    // Both ways round work: the book on the cursor clicked onto an item, or an item on the cursor clicked onto the book.
    //   left-click with breeding food:  breeds the mobs, using only as much food as they can eat
    //   right-click with a sword:       kills one; the drops go into your inventory and the XP to you
    // Changes are made on the server (it does the click too, and sends the result to the client);
    // the client only plays the sound, so it isn't heard twice.

    // Book on the cursor, clicked onto `other` in a slot
    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        if (self.getCount() != 1) return false;
        ItemStack other = slot.getItem();
        // Onto an empty slot, or an item it can't use: the book is placed/swapped as normal
        if (other.isEmpty() || !slot.allowModification(player)) return false;
        return useOn(self, other, clickAction, player);
    }

    // `other` on the cursor (or nothing), clicked onto the book in a slot
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (self.getCount() != 1) return false;
        // Left-click with an empty hand picks the book up as normal
        if (clickAction == ClickAction.PRIMARY && other.isEmpty()) return false;
        if (slot.allowModification(player) && useOn(self, other, clickAction, player)) return true;

        // Anything else isn't allowed
        player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
        return true;
    }

    // The book `self` used with `other` (food, a dye, a sword or shears). Returns true if something happened.
    private static boolean useOn(ItemStack self, ItemStack other, ClickAction clickAction, Player player)
    {
        Level level = player.level();
        MobBookContents contents = settled(self, level);

        // Breeding food, left-click: breed
        if (clickAction == ClickAction.PRIMARY && MobBookActions.isBreedingFood(level, contents, other))
        {
            int eaten = Math.min(other.getCount(), contents.maxFeed());
            if (eaten <= 0) return false; // all have eaten this cycle (or the book is full)
            if (level instanceof ServerLevel)
            {
                setContents(self, contents.withFed(eaten, level.getGameTime()));
                other.consume(eaten, player); // creative players keep their food
            }
            else player.playSound(SoundEvents.GENERIC_EAT.value(), 0.6f, 1f);
            return true;
        }

        // A dye, left-click: recolour (one dye used)
        if (clickAction == ClickAction.PRIMARY && MobBookActions.canDye(contents, other))
        {
            if (level instanceof ServerLevel)
            {
                setContents(self, contents.withAppearance(MobAppearance.dyed(contents.appearance(), MobBookActions.dyeColor(other))));
                other.consume(1, player);
            }
            else player.playSound(SoundEvents.DYE_USE, 1f, 1f);
            return true;
        }

        // Shears, right-click: shear every adult, drops to the player, one durability used
        if (clickAction == ClickAction.SECONDARY && MobBookActions.isShears(other) && MobBookActions.canShear(level, contents))
        {
            if (level instanceof ServerLevel serverLevel)
            {
                for (ItemStack drop : MobBookActions.shear(serverLevel, contents, player, other, player.position()))
                {
                    StorageInteractions.giveToPlayer(player, drop);
                }
                setContents(self, contents.withSheared(level.getGameTime()));
                // This hurtAndBreak overload is for items not held in a hand; the lambda runs if they break (nothing to do)
                other.hurtAndBreak(1, serverLevel, player, broken -> {});
            }
            return true;
        }

        // A sword, right-click: kill one, drops and XP to the player
        if (clickAction == ClickAction.SECONDARY && MobBookActions.isKillWeapon(other)
                && contents.adults() > 0 && contents.type().isPresent())
        {
            if (level instanceof ServerLevel serverLevel)
            {
                int killed = MobBookActions.kill(serverLevel, contents, 1, player, player.position(), true);
                setContents(self, contents.withKilled(killed));
            }
            return true;
        }
        return false;
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
        // "Colour: Red" for dyeable mobs (the colour names are vanilla's own lang lines)
        DyeColor color = contents.color();
        if (color != null)
        {
            tooltip.accept(line("color", Component.translatable("color.minecraft." + color.getName())).withStyle(ChatFormatting.GRAY));
        }
        // "Regrows in: 1:12" after shearing
        if (contents.regrowing(now))
        {
            tooltip.accept(line("regrow_in", time(contents.regrowLeft(now))).withStyle(ChatFormatting.GRAY));
        }
        // "Next drops in: 6:02" for egg layers (the timer starts on the first tick after capture)
        if (contents.nextDrop() > 0 && MobBookDrops.hasDrops(contents))
        {
            tooltip.accept(line("drop_in", time(contents.dropLeft(now))).withStyle(ChatFormatting.GRAY));
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
