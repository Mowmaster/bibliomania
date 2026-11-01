package com.mowmaster.bibliomania.Blocks.Book;

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
public class BookItem extends BlockItem implements IPagedStorageItem, ShelvedBook
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

    // The book's own rules, and its filter if it has one (BookFilter)
    @Override
    public boolean accepts(ItemStack self, ItemResource resource)
    {
        return book().accepts(resource) && BookFilter.allows(BookFilter.get(self), resource);
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
        ItemStack self = player.getMainHandItem();
        ItemStack offhand = player.getOffhandItem();
        if (BookFilter.isFilter(offhand))
        {
            if (!allowsInsert(self) || !BookFilter.get(self).isEmpty()) return InteractionResult.FAIL;
            if (!level.isClientSide())
            {
                self.set(DataComponentRegistry.BOOK_FILTER.get(), offhand.split(1));
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }
        if (offhand.getItem() instanceof FilterToolItem && !BookFilter.get(self).isEmpty())
        {
            if (!level.isClientSide())
            {
                ItemStack filter = BookFilter.get(self);
                self.remove(DataComponentRegistry.BOOK_FILTER.get());
                StorageInteractions.giveToPlayer(player, filter);
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
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
        return PagedStorageInteractions.tooltipImage(this, stack);
    }
}
