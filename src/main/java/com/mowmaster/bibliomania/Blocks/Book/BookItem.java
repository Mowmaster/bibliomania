package com.mowmaster.bibliomania.Blocks.Book;

import java.util.List;
import net.minecraft.network.chat.Component;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryPriority;
import com.mowmaster.mowlib.reading.ReadingPapers;
import com.mowmaster.mowlib.reading.ReadingPaper;
import com.mowmaster.mowlib.keys.IKeyPlaceable;
import com.mowmaster.mowlib.filter.HeldFilters;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.items.tools.FilterToolItem;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.bibliomania.Blocks.Bookcase.PagedShelfHandler;
import com.mowmaster.bibliomania.Blocks.Bookcase.ShelvedBook;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.mowlib.paging.IPagedStorageItem;
import com.mowmaster.mowlib.paging.PagedStorageInteractions;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Optional;

// The book as an item. MowLib's IPagedStorageItem does the work; this only gives the book's settings:
// - In inventories it works like a bundle (PagedStorageInteractions): click items in and out, the page grid tooltip.
// - Scrolling over it, or the page keys (MowLib's, default Page Down / Page Up), select a page.
// - The death book can be emptied this way but not filled. In the world the book is placed and used as a block.
public class BookItem extends BlockItem implements IPagedStorageItem, ShelvedBook, IKeyPlaceable
{
    // ---- On a Bookcase (ShelvedBook): its pages are the bookcase's item storage ----

    @Override
    @SuppressWarnings("unchecked") // R is ItemResource whenever type is the items type
    public <R extends Resource> @Nullable ResourceHandler<R> shelfHandler(TransportType<R> type, ItemAccess access)
    {
        if (type != TransportTypes.ITEMS.get()) return null;
        return (ResourceHandler<R>) new PagedShelfHandler(access, this);
    }

    @Override
    public float shelfFullness(ItemStack book)
    {
        return fullness(book);
    }

    public BookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    private BookBlock book()
    {
        return (BookBlock) getBlock();
    }

    // ---- IPagedStorageItem settings: the same as the placed book's ----

    @Override
    public int pageCapacity(ItemStack stack)
    {
        return book().pageCapacity();
    }

    @Override
    public int defaultPages(ItemStack stack)
    {
        return book().defaultPages();
    }

    // The book's own rules, and its filter if it has one (MowLib HeldFilters)
    @Override
    public boolean accepts(ItemStack self, ItemResource resource)
    {
        return book().accepts(resource) && HeldFilters.holderAllows(self, resource);
    }

    @Override
    public boolean allowsInsert(ItemStack self)
    {
        return book().allowsManualInsert();
    }

    // ---- Filters: right-click the air with the book in the main hand ----
    // A filter in the offhand goes in (not into a take-out-only book, nor while it has one); MowLib's Filter Tool in
    // the offhand takes it out again.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        // MowLib's filter clicks: a filter in the offhand goes in (not into a take-out-only book); its Filter Tool takes
        // it out again
        InteractionResult result = HeldFilters.useOnHeld(level, player, hand, allowsInsert(player.getMainHandItem()));
        if (result != null) return result;
        return InteractionResult.PASS;
    }

    // ---- The reading HUD (MowLib IReadableItem): MowLib's paged paper, with the book's library priority under its name ----

    @Override
    public @Nullable ReadingPaper read(ItemStack stack, Level level, Player player)
    {
        int priority = LibraryPriority.ofBook(stack);
        List<Component> afterName = priority > 0
                ? List.of(Component.translatable("hud." + BibliomaniaReferences.MODID + ".priority", priority)) : List.of();
        return ReadingPapers.pagedItem(stack, level, player, afterName, List.of());
    }

    // ---- Bundle-style clicks and the tooltip (an interface can't override Item methods, so these forward) ----

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        return PagedStorageInteractions.stackedOnOther(this, self, slot, clickAction, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        return PagedStorageInteractions.otherStackedOnMe(this, self, other, slot, clickAction, player, carriedItem);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack)
    {
        // A take-out-only book (the Book of Death: 64 pages) only shows its pages when something's in them
        if (!allowsInsert(stack) && contents(stack).totalAmount() <= 0) return Optional.empty();
        return PagedStorageInteractions.tooltipImage(this, stack);
    }
}
