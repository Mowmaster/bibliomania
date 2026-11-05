package com.mowmaster.bibliomania.Blocks.CompactingBook;

import com.mowmaster.bibliomania.Blocks.Book.BookFilter;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;

// A Compacting Book on a Bookcase, as an item handler: one index per tier (nuggets, ingots, blocks), each showing how
// many of it the book's pool makes; putting a tier in or taking one out changes the pool by its value. An empty book
// has one index that takes the first item with a chain (CompactingChains) and becomes that chain.
// NeoForge's ItemAccessResourceHandler does the inserting/extracting; update() turns the new amount of one tier into
// the new pool, written back as a changed book (within the transaction).
public class CompactingShelfHandler extends ItemAccessResourceHandler<ItemResource>
{
    private final Item bookItem;

    public CompactingShelfHandler(ItemAccess access)
    {
        super(access, Math.max(1, contents(access.getResource()).size()));
        this.bookItem = access.getResource().getItem();
    }

    private static CompactingContents contents(ItemResource book)
    {
        // ItemResource#get: a component of the stack it stands for
        CompactingContents contents = book.get(DataComponentRegistry.COMPACTING_CONTENTS.get());
        return contents == null ? CompactingContents.EMPTY : contents;
    }

    // The book's contents, or EMPTY if another item has been swapped into the slot
    private CompactingContents current(ItemResource book)
    {
        return book.is(bookItem) ? contents(book) : CompactingContents.EMPTY;
    }

    @Override
    protected ItemResource getResourceFrom(ItemResource book, int index)
    {
        return current(book).resource(index);
    }

    @Override
    protected int getAmountFrom(ItemResource book, int index)
    {
        return current(book).amount(index);
    }

    // Each index only takes its own tier (an index holding 0 would otherwise take anything); an empty book takes
    // anything with a chain
    @Override
    public boolean isValid(int index, ItemResource resource)
    {
        if (!itemAccess.getResource().is(bookItem)) return false;
        CompactingContents contents = current(itemAccess.getResource());
        if (contents.isSet()) return resource.equals(contents.resource(index));
        if (index != 0 || !resource.getComponentsPatch().isEmpty()) return false;
        List<CompactingContents.Tier> chain = CompactingChains.find(resource.getItem());
        // An empty book only starts a chain its filter allows (BookFilter#allowsChain)
        return chain != null && BookFilter.allowsChain(BookFilter.get(itemAccess.getResource()), chain);
    }

    // What the index can hold: what it has plus the room left (an empty book: a full book of that item)
    @Override
    protected int getCapacity(int index, ItemResource resource)
    {
        CompactingContents contents = current(itemAccess.getResource());
        if (contents.isSet()) return contents.amount(index) + contents.room(index);
        List<CompactingContents.Tier> chain = CompactingChains.find(resource.getItem());
        if (chain == null) return 0;
        CompactingContents fresh = CompactingContents.of(chain, 0);
        return fresh.room(fresh.tierOf(resource));
    }

    // The book with tier `index` now at `amount`: the pool changes by the difference times the tier's value
    @Override
    protected ItemResource update(ItemResource book, int index, ItemResource resource, int amount)
    {
        CompactingContents contents = current(book);
        int tier = index;
        if (!contents.isSet())
        {
            List<CompactingContents.Tier> chain = CompactingChains.find(resource.getItem());
            if (chain == null) return ItemResource.EMPTY;
            contents = CompactingContents.of(chain, 0);
            tier = contents.tierOf(resource);
            if (tier < 0) return ItemResource.EMPTY;
        }
        CompactingContents changed = contents.plus(tier, (long) amount - contents.amount(tier));
        return changed.isSet() ? book.with(DataComponentRegistry.COMPACTING_CONTENTS.get(), changed)
                : book.without(DataComponentRegistry.COMPACTING_CONTENTS.get());
    }
}
