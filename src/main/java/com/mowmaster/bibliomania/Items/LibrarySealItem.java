package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLockable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

// The Librarian's Seal: right-click a library block (controller, Bookcase, Reading Lectern...) to lock its settings,
// again to unlock. While locked: no dyeing, no Quill link changes, no work card swaps (and, on Card Catalogs, no
// drawer assignments). Using the block as normal still works.
public class LibrarySealItem extends Item
{
    public LibrarySealItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof LibraryLockable lockable)) return InteractionResult.PASS;
        if (!level.isClientSide() && player != null)
        {
            boolean locked = !lockable.isLocked();
            lockable.setLocked(locked);
            LibraryQuillItem.message(player, locked ? "sealed" : "unsealed");
            level.playSound(null, context.getClickedPos(), locked ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundSource.PLAYERS, 0.8f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".seal.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
