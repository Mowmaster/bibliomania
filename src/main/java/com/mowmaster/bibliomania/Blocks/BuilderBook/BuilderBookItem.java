package com.mowmaster.bibliomania.Blocks.BuilderBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookItem;
import com.mowmaster.bibliomania.Items.ActivatableBook;
import com.mowmaster.mowlib.paging.PagedContents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// The Builder's Book as an item: a normal storage book in inventories (BookItem: bundle-style clicks, scroll or the
// page keys to pick a slot; blocks only), and in the hand it builds:
// - Right-click a block: places the selected slot's block there instead of the book (one used from the slot). The
//   block's own BlockItem places it, so it faces, connects and behaves exactly as if placed by hand.
// - Activated (sneak + right-click or the activation key): each right-click places from a random slot instead.
// - The book itself is placed with MowLib's place key (IKeyPlaceable, default G).
public class BuilderBookItem extends BookItem implements ActivatableBook
{
    public BuilderBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    @Override
    public boolean usesActivation(ItemStack stack)
    {
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return super.isFoil(stack) || ActivatableBook.isActive(stack);
    }

    // Sneak + right-click in the air toggles random mode
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide()) ActivatableBook.toggle(player.getItemInHand(hand), player);
        return InteractionResult.SUCCESS;
    }

    // Right-click a block: sneaking toggles random mode, otherwise a block from the book goes there
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack book = context.getItemInHand();
        if (player != null && player.isShiftKeyDown())
        {
            if (!level.isClientSide()) ActivatableBook.toggle(book, player);
            return InteractionResult.SUCCESS;
        }
        int page = pageToPlace(book, level);
        if (page < 0) return InteractionResult.FAIL; // nothing to place
        // The server places it (the random slot is picked there) and tells the client
        if (!(level instanceof ServerLevel)) return InteractionResult.SUCCESS;

        PagedContents contents = contents(book);
        ResourceStack<ItemResource> onPage = contents.stack(page);
        if (!(onPage.resource().getItem() instanceof BlockItem blockItem)) return InteractionResult.FAIL;
        // One of the block, placed by its own BlockItem as if it were held, where the book was clicked
        ItemStack placing = onPage.resource().toStack(1);
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        InteractionResult result = blockItem.place(new BlockPlaceContext(level, player, context.getHand(), placing, hit));
        if (!result.consumesAction()) return result;
        // place used up the one item unless the player is in creative (it plays the sound for everyone but the
        // placer, who normally predicts it; here the client didn't, so it's played for them too)
        if (placing.isEmpty()) takeOne(book, page);
        if (player != null)
        {
            SoundType sound = blockItem.getBlock().defaultBlockState().getSoundType();
            level.playSound(null, context.getClickedPos(), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    // The slot to place from: the selected one, or while active a random slot holding a block (-1 if there's none)
    private int pageToPlace(ItemStack book, Level level)
    {
        PagedContents contents = contents(book);
        if (!ActivatableBook.isActive(book))
        {
            int selected = contents.selectedPage();
            return holdsBlock(contents, selected) ? selected : -1;
        }
        List<Integer> pages = new ArrayList<>();
        for (int page = 0; page < contents.pages(); page++)
        {
            if (holdsBlock(contents, page)) pages.add(page);
        }
        return pages.isEmpty() ? -1 : pages.get(level.getRandom().nextInt(pages.size()));
    }

    private static boolean holdsBlock(PagedContents contents, int page)
    {
        ResourceStack<ItemResource> stack = contents.stack(page);
        return !stack.isEmpty() && stack.resource().getItem() instanceof BlockItem;
    }

    // One fewer in `page` (the page list is copied, changed and stored as new contents)
    private void takeOne(ItemStack book, int page)
    {
        PagedContents contents = contents(book);
        ResourceStack<ItemResource> current = contents.stack(page);
        List<ResourceStack<ItemResource>> stacks = new ArrayList<>(contents.stacks());
        while (stacks.size() <= page) stacks.add(new ResourceStack<>(ItemResource.EMPTY, 0));
        stacks.set(page, current.amount() <= 1 ? new ResourceStack<>(ItemResource.EMPTY, 0)
                : new ResourceStack<>(current.resource(), current.amount() - 1));
        setContents(book, new PagedContents(contents.pages(), contents.page(), stacks));
    }

    // "Places: the selected slot" / "Places: a random slot (activated)"
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        boolean random = ActivatableBook.isActive(stack);
        tooltip.accept(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".builder_book." + (random ? "random" : "selected"))
                .withStyle(random ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".builder_book.keys",
                Component.keybind(ActivatableBook.TOGGLE_KEY), Component.keybind(PLACE_BOOK_KEY)).withStyle(ChatFormatting.DARK_GRAY));
    }

    // MowLib's place key (IKeyPlaceable, default G): its name, for the tooltip
    public static final String PLACE_BOOK_KEY = com.mowmaster.mowlib.client.MowLibKeyMappings.PLACE_NAME;
}
