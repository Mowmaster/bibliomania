package com.mowmaster.bibliomania.Blocks.Bookcase;

import com.mowmaster.mowlib.paging.IPagedStorageItem;
import com.mowmaster.mowlib.paging.PagedContents;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.ArrayList;
import java.util.List;

// An item book's pages as an item handler while it's in a Bookcase: one index per page, each holding one kind of item
// up to the book's page capacity. The book's own rules apply: what it accepts (Breakfast Club: food; Builder's Book:
// blocks), and whether anything can be put in at all (the Book of Death is take-out only).
// ItemAccessResourceHandler (NeoForge) does the inserting and extracting; this class only says how a page is read from
// the book's mowlib:paged_contents and how a changed page is written back (update returns the changed book, which
// the ItemAccess swaps in, within the transaction).
public class PagedShelfHandler extends ItemAccessResourceHandler<ItemResource>
{
    private final IPagedStorageItem book;
    // The book item this was made for: if a pipe keeps this handler after the book is swapped for another, the other
    // book is never read or written as if it had pages
    private final Item bookItem;

    public PagedShelfHandler(ItemAccess access, IPagedStorageItem book)
    {
        super(access, book.contents(access.getResource().toStack(1)).pages());
        this.book = book;
        this.bookItem = access.getResource().getItem();
    }

    private PagedContents contents(ItemResource bookResource)
    {
        return book.contents(bookResource.toStack(1));
    }

    @Override
    protected ItemResource getResourceFrom(ItemResource bookResource, int index)
    {
        return bookResource.is(bookItem) ? contents(bookResource).stack(index).resource() : ItemResource.EMPTY;
    }

    @Override
    protected int getAmountFrom(ItemResource bookResource, int index)
    {
        return bookResource.is(bookItem) ? contents(bookResource).stack(index).amount() : 0;
    }

    @Override
    protected int getCapacity(int index, ItemResource resource)
    {
        return book.pageCapacity(itemAccess.getResource().toStack(1));
    }

    // Only what the book accepts, and nothing at all into a take-out-only book (taking out never asks this)
    @Override
    public boolean isValid(int index, ItemResource resource)
    {
        if (!itemAccess.getResource().is(bookItem)) return false;
        ItemStack self = itemAccess.getResource().toStack(1);
        return book.allowsInsert(self) && book.accepts(self, resource);
    }

    // The book with page `index` holding `amount` of `resource` (an empty page at 0)
    @Override
    protected ItemResource update(ItemResource bookResource, int index, ItemResource resource, int amount)
    {
        PagedContents contents = contents(bookResource);
        // A page the book doesn't have (a smaller book swapped in): EMPTY makes the insert or extract do nothing
        if (index >= contents.pages()) return ItemResource.EMPTY;
        List<ResourceStack<ItemResource>> stacks = new ArrayList<>(contents.stacks());
        while (stacks.size() < contents.pages()) stacks.add(new ResourceStack<>(ItemResource.EMPTY, 0));
        stacks.set(index, amount > 0 ? new ResourceStack<>(resource, amount) : new ResourceStack<>(ItemResource.EMPTY, 0));
        return bookResource.with(MowLibDataComponents.PAGED_CONTENTS.get(), new PagedContents(contents.pages(), contents.page(), stacks));
    }
}
