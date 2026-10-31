package com.mowmaster.bibliomania.Blocks.CropBook;

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
//     item (heavy core) any time
//     right-click with an empty cursor on the book: takes the seed out (its growth is lost), or with no seed the soil
// - In a player's inventory it has a growth chance every ServerConfig.cropInventoryInterval() ticks (1 minute);
//   once fully grown it's harvested straight into the inventory and starts again.
public class CropBookItem extends BlockItem
{
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

    // ---- Inventory clicks ----

    // The book on the cursor, clicked onto an item: left-click puts one of it in
    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        if (self.getCount() != 1 || clickAction != ClickAction.PRIMARY || !slot.allowModification(player)) return false;
        ItemStack target = slot.getItem();
        CropContents planted = CropBookActions.insert(contents(self), target);
        if (planted == null) return false;
        if (!player.level().isClientSide())
        {
            setContents(self, planted);
            target.shrink(1);
            slot.setChanged();
        }
        player.playSound(SoundEvents.CROP_PLANTED, 0.8f, 1f);
        return true;
    }

    // Something (or nothing) on the cursor, clicked onto the book
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (self.getCount() != 1 || !slot.allowModification(player)) return false;
        CropContents contents = contents(self);
        // Right-click with an empty cursor: take the top thing out onto the cursor
        if (clickAction == ClickAction.SECONDARY && other.isEmpty())
        {
            ItemStack top = CropBookActions.topItem(contents);
            if (top.isEmpty()) return false;
            if (!player.level().isClientSide())
            {
                setContents(self, contents.withoutTop());
                carriedItem.set(top);
            }
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1f);
            return true;
        }
        // Left-click with soil or a seed: put one in
        if (clickAction == ClickAction.PRIMARY && !other.isEmpty())
        {
            CropContents planted = CropBookActions.insert(contents, other);
            if (planted == null)
            {
                player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
                return true;
            }
            if (!player.level().isClientSide())
            {
                setContents(self, planted);
                other.shrink(1);
            }
            player.playSound(SoundEvents.CROP_PLANTED, 0.8f, 1f);
            return true;
        }
        return false;
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
        if (contents.grown())
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
