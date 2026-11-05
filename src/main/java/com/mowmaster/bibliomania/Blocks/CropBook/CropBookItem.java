package com.mowmaster.bibliomania.Blocks.CropBook;

import com.mowmaster.mowlib.storage.InventoryClicks;
import com.mowmaster.mowlib.storage.InventoryClick;
import com.mowmaster.mowlib.storage.IInventoryUsable;
import com.mowmaster.mowlib.keys.IKeyPlaceable;
import com.mowmaster.bibliomania.Blocks.Bookcase.ShelvedBook;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.ServerConfig;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// The Farmer's Almanac as an item.
// - In inventories, either way round (the book on the cursor onto an item, or the item onto the book):
//     left-click with soil (first) or a seed that grows on it: puts one in; a boost item (nether star) or a yield
//     item (heavy core) any time; bone meal grows it a stage (a flower gives one of itself); a water bucket pours
//     water in, an empty bucket takes it out
//     right-click with an empty cursor on the book: takes the seed out (its growth is lost), or with no seed the soil
// - In a player's inventory it has a growth chance every ServerConfig.cropInventoryInterval() ticks (1 minute);
//   once fully grown it's harvested straight into the inventory and starts again.
public class CropBookItem extends BlockItem implements ShelvedBook, IKeyPlaceable, IInventoryUsable
{
    // ---- On a Bookcase (ShelvedBook): grows on the same timer as in an inventory; the harvest goes into the
    // bookcase's item books, and if they can't take all of it the crop stays ripe and tries again next time ----

    @Override
    public boolean shelfTick(ItemStack book, ServerLevel level, BookcaseBlockEntity shelf, int slot)
    {
        CropContents contents = contents(book);
        if (!contents.planted()) return false;
        long now = level.getGameTime();
        int interval = CropBookActions.inventoryInterval(contents);
        if (contents.nextTick() == 0 || contents.nextTick() > now + interval)
        {
            setContents(book, contents.withNextTick(now + interval));
            return true;
        }
        if (now < contents.nextTick()) return false;
        CropContents grown = CropBookActions.tryGrow(contents, level.getRandom());
        CropContents next = (grown != null ? grown : contents).withNextTick(now + interval);
        // No tool and no harvester: like the inventory's harvest
        if (next.grown())
        {
            // Into the library's (or bookcase's) item books; if it doesn't all fit it stays ripe and waits
            if (shelf.storeAll(CropBookActions.harvest(level, next, shelf.getBlockPos(), ItemStack.EMPTY, null))) next = next.withStage(0);
            else shelf.pause(slot);
        }
        setContents(book, next);
        return true;
    }

    public CropBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    public static CropContents contents(ItemStack stack)
    {
        CropContents contents = stack.get(DataComponentRegistry.CROP_CONTENTS.get());
        return contents != null ? contents : CropContents.EMPTY;
    }

    public static void setContents(ItemStack stack, CropContents contents)
    {
        stack.set(DataComponentRegistry.CROP_CONTENTS.get(), contents);
    }

    // ---- Growing in the inventory ----

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot)
    {
        super.inventoryTick(stack, level, entity, slot);
        if (!(entity instanceof Player player) || stack.getCount() != 1) return;
        CropContents contents = contents(stack);
        if (!contents.planted()) return;
        long now = level.getGameTime();
        int interval = CropBookActions.inventoryInterval(contents);
        if (contents.nextTick() == 0 || contents.nextTick() > now + interval)
        {
            setContents(stack, contents.withNextTick(now + interval));
            return;
        }
        if (now < contents.nextTick()) return;

        CropContents grown = CropBookActions.tryGrow(contents, level.getRandom());
        CropContents next = (grown != null ? grown : contents).withNextTick(now + interval);
        if (next.grown())
        {
            // Ripe: harvested into the inventory (no tool), and planted again
            for (ItemStack drop : CropBookActions.harvest(level, next, player.blockPosition(), ItemStack.EMPTY, player))
            {
                StorageInteractions.giveToPlayer(player, drop);
            }
            next = next.withStage(0);
        }
        setContents(stack, next);
    }

    // ---- Inventory clicks (MowLib InventoryClicks does the guards; usedWith is what the book does) ----
    // Either way round: the book on the cursor clicked onto `other` in a slot, or `other` on the cursor clicked onto the
    // book. A left-click with an empty cursor picks the book up as normal.

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        return InventoryClicks.stackedOnOther(this, self, slot, clickAction, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        return InventoryClicks.otherStackedOnMe(this, self, other, slot, clickAction, player, carriedItem);
    }

    // - right-click with an empty cursor: the top thing (crop, then soil) comes out onto the cursor
    // - left-click with a bucket: water in or out; the bucket becomes the other one (in place, or one of a stack's
    //   into the inventory)
    // - left-click with bone meal: like on the placed book (a ripe crop is harvested straight into the inventory)
    // - left-click with soil or a seed: one goes in (the cursor's seed refused with the fail sound if it can't)
    @Override
    public boolean usedWith(ItemStack self, ItemStack other, ClickAction action, Player player, InventoryClick click)
    {
        CropContents contents = contents(self);
        boolean server = !player.level().isClientSide();
        if (action == ClickAction.SECONDARY && other.isEmpty())
        {
            ItemStack top = CropBookActions.topItem(contents);
            if (top.isEmpty()) return false;
            if (server)
            {
                setContents(self, contents.withoutTop());
                click.giveTaken(top, player);
            }
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1f);
            return true;
        }
        if (action != ClickAction.PRIMARY || other.isEmpty()) return false;
        CropContents watered = CropBookActions.useBucket(contents, other);
        if (watered != null)
        {
            if (server)
            {
                setContents(self, watered);
                click.exchangeOne(other, CropBookActions.bucketAfter(other), player);
            }
            player.playSound(watered.water() ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL, 0.8f, 1f);
            return true;
        }
        if (CropBookActions.isBoneMeal(other)) return boneMeal(self, other, player);
        CropContents planted = CropBookActions.insert(contents, other);
        if (planted == null)
        {
            // The cursor's item onto the book: refused; the book onto an item it can't plant: a normal click
            if (click.kind() != InventoryClick.Kind.CURSOR) return false;
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return true;
        }
        if (server)
        {
            setContents(self, planted);
            other.shrink(1);
        }
        player.playSound(SoundEvents.CROP_PLANTED, 0.8f, 1f);
        return true;
    }


    // One bone meal from `boneMeal` on the book (true = handled, even if it did nothing): the next stage, or a
    // flower's flower into the inventory. If that ripens it, it's harvested into the inventory and starts again, the
    // same as when it ripens there on its own.
    private static boolean boneMeal(ItemStack book, ItemStack boneMeal, Player player)
    {
        CropBookActions.BoneMeal result = CropBookActions.boneMeal(contents(book), player.getRandom());
        if (result == null)
        {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return true;
        }
        if (player.level() instanceof ServerLevel level)
        {
            CropContents next = result.contents();
            for (ItemStack drop : result.drops()) StorageInteractions.giveToPlayer(player, drop);
            if (next.grown())
            {
                for (ItemStack drop : CropBookActions.harvest(level, next, player.blockPosition(), ItemStack.EMPTY, player))
                {
                    StorageInteractions.giveToPlayer(player, drop);
                }
                next = next.withStage(0);
            }
            setContents(book, next);
            boneMeal.shrink(1);
        }
        player.playSound(SoundEvents.BONE_MEAL_USE, 0.8f, 1f);
        return true;
    }

    // ---- Tooltip ----

    // "Soil: Dirt (speed x1.0)", "Crop: Wheat Seeds, stage 3/8" or "Ripe", or what's still needed
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        CropContents contents = contents(stack);
        // "Yield: 2 (+50% drops)"
        if (contents.yields() > 0)
        {
            tooltip.accept(line("yields", contents.yields(), Math.round(contents.yields() * ServerConfig.cropYieldPercent()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        // "Boosts: 3 (+30% growth)"
        if (contents.boosts() > 0)
        {
            tooltip.accept(line("boosts", contents.boosts(), Math.round(contents.boosts() * ServerConfig.cropBoostPercent()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (contents.soil().isEmpty())
        {
            tooltip.accept(line("no_soil").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(line("soil", contents.soil().get().getName()).withStyle(ChatFormatting.GRAY));
        if (contents.water()) tooltip.accept(line("water").withStyle(ChatFormatting.AQUA));
        CropProfile profile = contents.profile();
        if (profile == null)
        {
            tooltip.accept(line("no_seed").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(line("crop", new ItemStack(profile.seed()).getHoverName(), contents.stage() + 1, profile.maxStage() + 1)
                .withStyle(ChatFormatting.GREEN));
        // "Growth chance: 41%" (each random tick when placed, each timer in an inventory)
        tooltip.accept(line("chance", Math.round(CropBookActions.growChance(contents) * 100)).withStyle(ChatFormatting.DARK_GRAY));
        if (profile.dead() != null && !contents.water())
        {
            tooltip.accept(line("dead").withStyle(ChatFormatting.GOLD));
        }
        else if (CropBookActions.needsWater(contents))
        {
            tooltip.accept(line("needs_water").withStyle(ChatFormatting.RED));
        }
        else if (profile.flower())
        {
            tooltip.accept(line("flower").withStyle(ChatFormatting.GOLD));
        }
        else if (contents.grown())
        {
            tooltip.accept(line("ripe").withStyle(ChatFormatting.GOLD));
        }
        else if (contents.nextTick() > 0 && context.level() != null)
        {
            // "Next growth chance in: 0:42" (in an inventory; counts down live, the tooltip is rebuilt every frame)
            long ticks = Math.max(0, contents.nextTick() - context.level().getGameTime());
            long seconds = (ticks + 19) / 20;
            tooltip.accept(line("next", String.format("%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static MutableComponent line(String name, Object... args)
    {
        return Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".crop_book." + name, args);
    }
}
