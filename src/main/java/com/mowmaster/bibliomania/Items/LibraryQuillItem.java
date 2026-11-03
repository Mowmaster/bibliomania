package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLockable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

// The Librarian's Quill: changes which blocks are in a library.
// - Right-click a Library Controller: the quill remembers it (bibliomania:library_link)
// - Then right-click a library block (Bookcase, Reading Lectern...):
//     not touching the library: links it (and whatever touches it), within LibraryControllerBlockEntity.linkRange() (server config)
//     linked: unlinks it
//     touching the library: takes it out (it stays out until clicked again)
// - Sneak + right-click the air: forgets the controller
// Nothing changes while the controller, or the clicked block, is locked (Librarian's Seal).
public class LibraryQuillItem extends Item
{
    public LibraryQuillItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack quill = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (!(level instanceof ServerLevel serverLevel) || player == null) return InteractionResult.SUCCESS;
        BlockEntity clicked = level.getBlockEntity(pos);

        if (clicked instanceof LibraryControllerBlockEntity)
        {
            quill.set(DataComponentRegistry.LIBRARY_LINK.get(), pos.immutable());
            message(player, "quill.selected", pos.getX(), pos.getY(), pos.getZ());
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        BlockPos controllerPos = quill.get(DataComponentRegistry.LIBRARY_LINK.get());
        if (controllerPos == null || !level.isLoaded(controllerPos)
                || !(level.getBlockEntity(controllerPos) instanceof LibraryControllerBlockEntity controller))
        {
            message(player, "quill.no_controller");
            return InteractionResult.SUCCESS;
        }
        if (controller.isLocked() || (clicked instanceof LibraryLockable lockable && lockable.isLocked()))
        {
            message(player, "locked");
            return InteractionResult.SUCCESS;
        }
        LibraryControllerBlockEntity.LinkChange change = controller.toggle(serverLevel, pos);
        message(player, "quill." + change.name().toLowerCase());
        level.playSound(null, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.8f, 1f);
        return InteractionResult.SUCCESS;
    }

    // Sneak + right-click the air: forget the controller
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        ItemStack quill = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !quill.has(DataComponentRegistry.LIBRARY_LINK.get())) return InteractionResult.PASS;
        if (!level.isClientSide())
        {
            quill.remove(DataComponentRegistry.LIBRARY_LINK.get());
            message(player, "quill.cleared");
        }
        return InteractionResult.SUCCESS;
    }

    // Shines while it remembers a controller
    @Override
    public boolean isFoil(ItemStack stack)
    {
        return stack.has(DataComponentRegistry.LIBRARY_LINK.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        BlockPos controller = stack.get(DataComponentRegistry.LIBRARY_LINK.get());
        tooltip.accept(controller == null
                ? line("quill.tooltip_none").withStyle(ChatFormatting.GRAY)
                : line("quill.tooltip", controller.getX(), controller.getY(), controller.getZ()).withStyle(ChatFormatting.GOLD));
        tooltip.accept(line("quill.tooltip_use").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static net.minecraft.network.chat.MutableComponent line(String key, Object... args)
    {
        return Component.translatable("tooltip." + BibliomaniaReferences.MODID + "." + key, args);
    }

    static void message(Player player, String key, Object... args)
    {
        player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".library." + key, args));
    }
}
