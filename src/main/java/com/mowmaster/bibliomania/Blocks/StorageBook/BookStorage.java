package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.transport.TransportContents;
import com.mowmaster.mowlib.transport.TransportStorage;
import com.mowmaster.mowlib.transport.TransportStorageHost;
import com.mowmaster.mowlib.transport.TransportType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// A storage book ITEM's contents, opened as a normal MowLib storage so the same code works on a book in the hand or
// inventory as on a placed one. The contents live in the item's "mowlib:transport_contents" component; opening makes
// a storage of the book's transport type, fills it from the component, and every committed change writes the
// component back (this is the storage's TransportStorageHost, so it's told on each change).
//   BookStorage storage = BookStorage.open(stack);    // then use storage.handler(...) like any ResourceHandler
public final class BookStorage implements TransportStorageHost
{
    private final ItemStack stack;
    private final TransportType<?> type;
    private final int capacity;
    private final TransportStorage<?> storage;
    private boolean loading;

    private BookStorage(ItemStack stack, TransportType<?> type, int capacity)
    {
        this.stack = stack;
        this.type = type;
        this.capacity = capacity;
        this.storage = type.createStorage(this);
        loading = true;
        TransportContents<?> saved = stack.get(MowLibDataComponents.TRANSPORT_CONTENTS.get());
        if (saved != null && saved.type() == type) fill(saved, storage);
        loading = false;
    }

    // The contents of a storage book stack, or null if it isn't one
    public static @Nullable BookStorage open(ItemStack stack)
    {
        if (!(stack.getItem() instanceof StorageBookItem item)) return null;
        StorageBookBlock book = item.book();
        return new BookStorage(stack, book.type(), book.capacity());
    }

    public TransportType<?> type()
    {
        return type;
    }

    public TransportStorage<?> storage()
    {
        return storage;
    }

    // The storage as a handler of a known type's resource, e.g. handler(TransportTypes.FLUIDS.get()) for fluids.
    // The cast is checked: it throws if the book is a different type.
    @SuppressWarnings("unchecked")
    public <R extends Resource> TransportStorage<R> handler(TransportType<R> expected)
    {
        if (expected != type) throw new IllegalArgumentException("Book holds " + type.getId() + ", not " + expected.getId());
        return (TransportStorage<R>) storage;
    }

    public long amount()
    {
        return storage.getAmountAsLong(0);
    }

    public int capacity()
    {
        return capacity;
    }

    // ---- TransportStorageHost ----

    @Override
    public void onStorageChanged(TransportType<?> changed)
    {
        if (!loading) stack.set(MowLibDataComponents.TRANSPORT_CONTENTS.get(), snapshot(type, storage));
    }

    @Override
    public int getCapacity(TransportType<?> asked)
    {
        return capacity;
    }

    // ---- Shared with the placed book ----

    // What a handler holds, as a TransportContents (for the item component). Reads only, changes nothing.
    // The cast is safe: a type's storage always holds that type's resource.
    @SuppressWarnings("unchecked")
    public static <R extends Resource> TransportContents<R> snapshot(TransportType<R> type, ResourceHandler<?> handler)
    {
        ResourceHandler<R> typed = (ResourceHandler<R>) handler;
        List<ResourceStack<R>> stacks = new ArrayList<>();
        for (int index = 0; index < typed.size(); index++)
        {
            R resource = typed.getResource(index);
            int amount = typed.getAmountAsInt(index);
            if (!resource.isEmpty() && amount > 0) stacks.add(new ResourceStack<>(resource, amount));
        }
        return new TransportContents<>(type, stacks);
    }

    // Puts saved contents into a storage of the same type (anything over its capacity is lost)
    @SuppressWarnings("unchecked")
    public static <R extends Resource> void fill(TransportContents<R> contents, TransportStorage<?> storage)
    {
        // try-with-resources: the transaction closes at the end of the block; only a commit keeps the changes
        try (Transaction transaction = Transaction.openRoot())
        {
            contents.insertInto((TransportStorage<R>) storage, transaction);
            transaction.commit();
        }
    }
}
