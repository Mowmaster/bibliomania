package com.mowmaster.bibliomania.Blocks.Bookcase;

import com.mowmaster.mowlib.transport.ItemTransportStorage;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.transport.TransportContents;
import com.mowmaster.mowlib.transport.TransportStorage;
import com.mowmaster.mowlib.transport.TransportType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.List;

// A Book of Fluids, Experience or Energy as a handler of its type while it's in a Bookcase: one index, read from the
// book's mowlib:transport_contents. What it may hold and how much comes from the book's own storage (ItemTransportStorage, the
// same one the item uses), so its rules are respected. Changes are written back as a changed book through the
// ItemAccess (see PagedShelfHandler).
public class TransportShelfHandler<R extends Resource> extends ItemAccessResourceHandler<R>
{
    private final TransportType<R> type;
    // The kind of book this was made for: a handler kept by a pipe after the book was taken out reads as an empty book
    private final Item bookItem;

    public TransportShelfHandler(ItemAccess access, TransportType<R> type)
    {
        super(access, 1);
        this.type = type;
        this.bookItem = access.getResource().getItem();
    }

    // The book's storage, as it is in `bookResource` (opened fresh each time: it's only a view of the component)
    private TransportStorage<R> storage(ItemResource bookResource)
    {
        // A different book (or none) in the slot now: read as an empty book of the original kind
        ItemTransportStorage storage = bookResource.is(bookItem) ? ItemTransportStorage.open(bookResource.toStack(1)) : null;
        if (storage == null) storage = ItemTransportStorage.open(new ItemStack(bookItem));
        if (storage == null) throw new IllegalStateException("Not a storage book: " + bookItem);
        return storage.handler(type);
    }

    @Override
    protected R getResourceFrom(ItemResource bookResource, int index)
    {
        return storage(bookResource).getResource(0);
    }

    @Override
    protected int getAmountFrom(ItemResource bookResource, int index)
    {
        return storage(bookResource).getAmountAsInt(0);
    }

    @Override
    protected int getCapacity(int index, R resource)
    {
        return storage(itemAccess.getResource()).getCapacityAsInt(0, resource);
    }

    @Override
    public boolean isValid(int index, R resource)
    {
        return storage(itemAccess.getResource()).isValid(0, resource);
    }

    // The book holding `amount` of `resource` (nothing at 0)
    @Override
    protected ItemResource update(ItemResource bookResource, int index, R resource, int amount)
    {
        if (amount <= 0) return bookResource.without(MowLibDataComponents.TRANSPORT_CONTENTS.get());
        TransportContents<R> contents = new TransportContents<>(type, List.of(new ResourceStack<>(resource, amount)));
        return bookResource.with(MowLibDataComponents.TRANSPORT_CONTENTS.get(), contents);
    }
}
