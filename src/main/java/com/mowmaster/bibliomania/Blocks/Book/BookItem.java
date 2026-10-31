package com.mowmaster.bibliomania.Blocks.Book;

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
public class BookItem extends BlockItem implements IPagedStorageItem
{
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

    @Override
    public boolean accepts(ItemStack self, ItemResource resource)
    {
        return book().accepts(resource);
    }

    @Override
    public boolean allowsInsert(ItemStack self)
    {
        return book().allowsManualInsert();
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
