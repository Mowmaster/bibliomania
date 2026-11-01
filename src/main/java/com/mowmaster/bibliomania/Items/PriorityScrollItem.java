package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryPriority;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

// The Priority Scroll: sets which shelves (Bookcases, Chiseled Bookshelves) a library fills first, and which placed
// storage books come first on their Bookcase (LibraryPriority).
// - right-click a block: +1 (sneaking +10); left-click: -1 (-10), see PriorityEvents
// - the page keys while holding it and looking at one: the same (ClientSide/PriorityKeys)
// Its right-click goes before the block's own (LibraryToolEvents), so a Bookcase doesn't hand out a book.
public class PriorityScrollItem extends Item
{
    public PriorityScrollItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !LibraryPriority.hasPriority(level, context.getClickedPos())) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel) LibraryPriority.adjust(serverLevel, context.getClickedPos(), LibraryPriority.step(player), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".priority_scroll.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
