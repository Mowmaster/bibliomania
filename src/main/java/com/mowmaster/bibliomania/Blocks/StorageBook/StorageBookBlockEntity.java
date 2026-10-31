package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.transport.TransportContents;
import com.mowmaster.mowlib.transport.TransportStorage;
import com.mowmaster.mowlib.transport.TransportStorageHost;
import com.mowmaster.mowlib.transport.TransportType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// A placed storage book: one MowLib TransportStorage of the book's type (made by the type, sized by the block's
// capacity). This block entity is its TransportStorageHost. The storage is also offered to pipes and cables as the
// type's capability (StorageBookEvents).
// Clients get the contents (for the sphere) and when something last flowed in or out (for the flow animation).
public class StorageBookBlockEntity extends BlockEntity implements TransportStorageHost
{
    private static final String TAG_STORAGE = "storage";
    private static final String TAG_FLOW_TICK = "flow_tick";
    private static final String TAG_FLOW_IN = "flow_in";

    private final TransportStorage<?> storage;
    // The game tick something last went in or out, and which way (synced, for the flow animation)
    private long flowTick = 0;
    private boolean flowIn = true;
    // Server: the amount at the last sync, to tell in from out; whether a sync is due
    private long lastAmount;
    private boolean needsSync;
    private boolean pickedUp;
    // Server: the tick of the last left-click (holding left-click clicks again and again)
    private long lastClick = -100;
    // Client: the last non-empty resource, so the "flowing out" animation still knows what it looks like when emptied
    @Nullable private Resource lastResource;

    public StorageBookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.STORAGE_BOOK.get(), pos, state);
        // getCapacity below reads the block, which the super constructor has just set
        this.storage = book().type().createStorage(this);
    }

    public StorageBookBlock book()
    {
        return (StorageBookBlock) getBlockState().getBlock();
    }

    public TransportType<?> type()
    {
        return book().type();
    }

    public TransportStorage<?> storage()
    {
        return storage;
    }

    // The storage as its type's handler, for a behaviour that knows the type (throws if the book is another type)
    @SuppressWarnings("unchecked")
    public <R extends Resource> ResourceHandler<R> storage(TransportType<R> expected)
    {
        if (expected != type()) throw new IllegalArgumentException("Book holds " + type().getId() + ", not " + expected.getId());
        return (ResourceHandler<R>) storage;
    }

    // For capabilities: the storage if this book holds `asked`, otherwise null
    @SuppressWarnings("unchecked")
    public <R extends Resource> @Nullable ResourceHandler<R> storageFor(TransportType<R> asked)
    {
        return asked == type() ? (ResourceHandler<R>) storage : null;
    }

    public long amount()
    {
        return storage.getAmountAsLong(0);
    }

    // 0 to 1
    public float fullness()
    {
        return Math.min(1f, (float) amount() / book().capacity());
    }

    // What's stored now, or (client) the last thing that was
    public @Nullable Resource displayResource()
    {
        Resource now = storage.getResource(0);
        if (!now.isEmpty()) lastResource = now;
        return lastResource;
    }

    public long flowTick()
    {
        return flowTick;
    }

    public boolean flowIn()
    {
        return flowIn;
    }

    // true (and remembers now) if at least `delay` ticks have passed since the last left-click
    public boolean clickReady(int delay)
    {
        if (level == null) return false;
        long now = level.getGameTime();
        if (now - lastClick < delay && now >= lastClick) return false;
        lastClick = now;
        return true;
    }

    // ---- TransportStorageHost ----

    @Override
    public void onStorageChanged(TransportType<?> type)
    {
        setChanged();
        needsSync = true;
    }

    @Override
    public int getCapacity(TransportType<?> type)
    {
        return book().capacity();
    }

    // ---- Ticking (server) ----

    // The behaviour's tick, then at most every 4 ticks: send the new contents to clients (pipes can change it every
    // tick; a block update each tick would be a lot), noting which way it flowed, and update the book's thickness
    public void serverTick(ServerLevel serverLevel)
    {
        book().behaviour().serverTickPlaced(this, serverLevel);
        if (!needsSync || serverLevel.getGameTime() % 4 != 0) return;
        needsSync = false;
        long amount = amount();
        if (amount != lastAmount)
        {
            flowIn = amount > lastAmount;
            flowTick = serverLevel.getGameTime();
            lastAmount = amount;
        }
        if (getBlockState().getBlock() instanceof BookBlock bookBlock)
        {
            bookBlock.updateThickness(serverLevel, worldPosition, getBlockState(), fullness());
        }
        serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- Block <-> item: the contents travel in the item's mowlib:transport_contents ----

    public ItemStack toItem()
    {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
        // collectComponents() runs collectImplicitComponents below
        stack.applyComponents(collectComponents());
        return stack;
    }

    public void markPickedUp()
    {
        pickedUp = true;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components)
    {
        super.applyImplicitComponents(components);
        TransportContents<?> saved = components.get(MowLibDataComponents.TRANSPORT_CONTENTS.get());
        if (saved != null && saved.type() == type()) BookStorage.fill(saved, storage);
        lastAmount = amount();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        if (!storage.isEmpty()) components.set(MowLibDataComponents.TRANSPORT_CONTENTS.get(), BookStorage.snapshot(type(), storage));
    }

    // Removed by anything other than a pickup (explosion, /setblock...): drop the book with its contents
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (pickedUp || !(level instanceof ServerLevel)) return;
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), toItem());
    }

    // ---- Saving and syncing ----

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        // putChild: the storage saves itself into its own sub-object (NeoForge's ValueIOSerializable)
        output.putChild(TAG_STORAGE, storage);
        output.putLong(TAG_FLOW_TICK, flowTick);
        output.putBoolean(TAG_FLOW_IN, flowIn);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        // Emptied first: a storage may leave its old contents when the saved data has none (an emptied book)
        clear(storage);
        // childOrEmpty: an empty object when missing
        storage.deserialize(input.childOrEmpty(TAG_STORAGE));
        flowTick = input.getLongOr(TAG_FLOW_TICK, 0L);
        flowIn = input.getBooleanOr(TAG_FLOW_IN, true);
        lastAmount = amount();
    }

    // Takes everything out (committed); the cast is safe: a handler's resources are its own type
    @SuppressWarnings("unchecked")
    private static <R extends Resource> void clear(ResourceHandler<?> handler)
    {
        ResourceHandler<R> typed = (ResourceHandler<R>) handler;
        try (Transaction transaction = Transaction.openRoot())
        {
            for (int index = 0; index < typed.size(); index++)
            {
                R resource = typed.getResource(index);
                if (!resource.isEmpty()) typed.extract(index, resource, typed.getAmountAsInt(index), transaction);
            }
            transaction.commit();
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        return saveCustomOnly(registries); // runs saveAdditional into a tag
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this); // uses getUpdateTag
    }
}
