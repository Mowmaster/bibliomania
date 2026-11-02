package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.mowlib.filter.ITransportFilter;
import com.mowmaster.mowlib.paging.PagedContents;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import com.mowmaster.mowlib.items.filters.BaseFilterItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// An item book's filter: one of MowLib's filters (mowlib:filter_*) put into the book. The book then only takes what
// the filter allows (by hand, in an inventory, on a Bookcase, from the library); what's already in it stays.
// - On the item it's the bibliomania:book_filter component; a placed book keeps it in its block entity (IFilterHolder)
//   and they're moved across like the contents.
// - In: hold the filter in the offhand and right-click the placed book, or right-click the air with the book in the
//   main hand. Out: the same with MowLib's Filter Tool in the offhand (left-click for a placed book).
// - The Reading Goggles show a whitelisted book as its items and their totals, not page by page (summary).
public final class BookFilter
{
    private BookFilter() {}

    // The book item's filter (a new stack each time: create() makes one from the template), or EMPTY
    public static ItemStack get(ItemStack book)
    {
        ItemStackTemplate filter = book.get(DataComponentRegistry.BOOK_FILTER.get());
        return filter == null ? ItemStack.EMPTY : filter.create();
    }

    // Puts a filter on a book item (EMPTY takes it off). fromNonEmptyStack: a stack as an immutable template.
    public static void set(ItemStack book, ItemStack filter)
    {
        if (filter.isEmpty()) book.remove(DataComponentRegistry.BOOK_FILTER.get());
        else book.set(DataComponentRegistry.BOOK_FILTER.get(), ItemStackTemplate.fromNonEmptyStack(filter));
    }

    public static boolean isFilter(ItemStack stack)
    {
        return stack.getItem() instanceof ITransportFilter;
    }

    // Whether the filter lets the item in: no filter lets everything in. The book's own rules are checked apart.
    public static boolean allows(ItemStack filter, ItemResource resource)
    {
        if (!(filter.getItem() instanceof ITransportFilter transportFilter)) return true;
        return transportFilter.allows(filter, TransportTypes.ITEMS.get(), resource);
    }

    // What the Reading Goggles show for a book with a whitelist filter: each item it holds with its total over all
    // pages, then the filter's other items at 0. Null (show it page by page as usual) without a filter, with a
    // blacklist, or when it holds something the filter doesn't allow.
    public static @Nullable List<ResourceStack<ItemResource>> summary(ItemStack book, PagedContents contents)
    {
        ItemStack filter = get(book);
        if (!(filter.getItem() instanceof ITransportFilter transportFilter) || transportFilter.isBlacklist(filter)) return null;
        // LinkedHashMap: keeps the order things were first found in; merge adds to an item already there
        Map<ItemResource, Integer> totals = new LinkedHashMap<>();
        for (ResourceStack<ItemResource> stack : contents.stacks())
        {
            if (stack.isEmpty()) continue;
            if (!allows(filter, stack.resource())) return null;
            totals.merge(stack.resource(), stack.amount(), Integer::sum);
        }
        for (ItemStackTemplate template : BaseFilterItem.getEntry(filter, TransportTypes.ITEMS.get()).list())
        {
            ItemStack listed = template.create();
            if (!listed.isEmpty() && !isFilter(listed)) totals.putIfAbsent(ItemResource.of(listed), 0);
        }
        List<ResourceStack<ItemResource>> summary = new ArrayList<>();
        totals.forEach((resource, amount) -> summary.add(new ResourceStack<>(resource, amount)));
        return summary;
    }
}
