package com.mowmaster.bibliomania.Blocks.CompactingBook;

import com.mowmaster.mowlib.keys.IKeyPlaceable;
import com.mowmaster.mowlib.filter.HeldFilters;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.mowlib.items.tools.FilterToolItem;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.mowmaster.bibliomania.Blocks.Bookcase.ShelvedBook;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.mowlib.paging.IPageable;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// The Compacting Book as an item: its contents in bibliomania:compacting_contents. On a Bookcase its tiers are the
// bookcase's (and the library's) item storage (CompactingShelfHandler); its tiers are pages for the page keys and
// scrolling (MowLib IPageable). The tooltip lists every tier with how many of it the book makes.
public class CompactingBookItem extends BlockItem implements ShelvedBook, IPageable, IKeyPlaceable
{
    public CompactingBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    public static CompactingContents contents(ItemStack stack)
    {
        CompactingContents contents = stack.get(DataComponentRegistry.COMPACTING_CONTENTS.get());
        return contents == null ? CompactingContents.EMPTY : contents;
    }

    // ---- Filters: right-click the air with the book in the main hand (as other item books, BookItem#use) ----
    // A filter in the offhand goes in (one at a time); MowLib's Filter Tool in the offhand takes it out again.
    // The filter decides which chains an empty book may start (CompactingFilter#allowsChain).
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        // MowLib's filter clicks: a filter in the offhand goes in; its Filter Tool takes it out again
        InteractionResult result = HeldFilters.useOnHeld(level, player, hand, true);
        if (result != null) return result;
        return super.use(level, player, hand);
    }

    // ---- On a Bookcase ----

    @Override
    @SuppressWarnings("unchecked") // R is ItemResource whenever type is the items type
    public <R extends Resource> @Nullable ResourceHandler<R> shelfHandler(TransportType<R> type, ItemAccess access)
    {
        if (type != TransportTypes.ITEMS.get()) return null;
        return (ResourceHandler<R>) new CompactingShelfHandler(access);
    }

    @Override
    public float shelfFullness(ItemStack book)
    {
        return contents(book).fullness();
    }

    // ---- Pages: the tiers ----

    @Override
    public int getPageCount(ItemStack stack)
    {
        return Math.max(1, contents(stack).size());
    }

    @Override
    public int getPage(ItemStack stack)
    {
        return contents(stack).page();
    }

    @Override
    public void setPage(ItemStack stack, int page)
    {
        CompactingContents contents = contents(stack);
        if (contents.isSet()) stack.set(DataComponentRegistry.COMPACTING_CONTENTS.get(), contents.withPage(page));
    }

    // "1,000 Gold Nugget", "111 Gold Ingot", "12 Block of Gold" (the open tier first), or how to start one
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        CompactingContents contents = contents(stack);
        String key = "tooltip." + BibliomaniaReferences.MODID + ".compacting.";
        ItemStack filter = HeldFilters.get(stack);
        if (!filter.isEmpty()) tooltip.accept(Component.translatable("hud." + BibliomaniaReferences.MODID + ".book_filter", filter.getHoverName()).withStyle(ChatFormatting.GRAY));
        if (!contents.isSet())
        {
            tooltip.accept(Component.translatable(key + "empty", CompactingContents.CAPACITY).withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int tier = contents.size() - 1; tier >= 0; tier--)
        {
            Component line = Component.translatable(key + "tier", contents.amount(tier), contents.stack(tier, 1).getHoverName());
            tooltip.accept(tier == contents.page() ? line.copy().withStyle(ChatFormatting.WHITE) : line.copy().withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable(key + "capacity", CompactingContents.CAPACITY, contents.stack(contents.size() - 1, 1).getHoverName())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
