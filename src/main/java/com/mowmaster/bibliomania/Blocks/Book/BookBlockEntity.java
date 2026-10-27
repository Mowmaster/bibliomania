package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.StorageHandler.libBulkItemHandler;
import com.mowmaster.bibliomania.TintHandler.ITintHolder;
import com.mowmaster.bibliomania.TintHandler.libTintData;
import com.mowmaster.bibliomania.TintHandler.libTintBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// The block entity for every book. Each page is one slot of a libBulkItemHandler: one kind of item, up to the
// block's page capacity, ignoring normal stack sizes. Only the open page can be filled or emptied by hand.
// Books are manual only for now (no hopper/pipe capability); lecterns and shelves will add automation later.
public class BookBlockEntity extends BlockEntity implements ITintHolder
{
    private static final String TAG_PAGES = "pages";
    private static final String TAG_PAGE = "page";

    private final libTintData tints;
    // Not final: the page count comes from the item when the book is placed, so the handler is rebuilt then
    private libBulkItemHandler items;
    private int page = 0;
    // Set while the book is being picked up, so removing the block doesn't also drop it
    private boolean pickedUp = false;

    public BookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.BOOK.get(), pos, state);
        BookBlock block = (BookBlock) state.getBlock();
        this.tints = new libTintData(this, block.getTintRegionCount());
        this.items = newHandler(block.defaultPages());
    }

    private BookBlock block()
    {
        return (BookBlock) getBlockState().getBlock();
    }

    private libBulkItemHandler newHandler(int pages)
    {
        BookBlock block = block();
        return new libBulkItemHandler(pages, block.pageCapacity(), block::accepts, this::onItemsChanged);
    }

    // ---- Pages ----

    public libBulkItemHandler getItems()
    {
        return items;
    }

    public int getPage()
    {
        return page;
    }

    public int getPageCount()
    {
        return items.size();
    }

    // Moves forward (+1) or back (-1), wrapping round at either end
    public void turnPage(int direction)
    {
        // Math.floorMod keeps the result positive: floorMod(-1, 8) is 7, where -1 % 8 would be -1
        setPage(Math.floorMod(page + direction, getPageCount()));
    }

    public void setPage(int newPage)
    {
        page = Math.floorMod(newPage, getPageCount());
        setChanged();
        sync();
    }

    // What the open page holds, as a normal ItemStack (for the renderer); at most one stack's worth
    public ItemStack getOpenPageStack()
    {
        ItemResource resource = items.getResource(page);
        if (resource.isEmpty()) return ItemStack.EMPTY;
        return resource.toStack((int) Math.min(items.getAmountAsLong(page), resource.getMaxStackSize()));
    }

    // Puts as much of the stack as fits into the open page (does not change the stack). Returns how many went in.
    public int insertIntoOpenPage(ItemStack stack)
    {
        if (stack.isEmpty()) return 0;
        // try-with-resources: the transaction is closed (and undone if not committed) at the end of the block
        try (Transaction transaction = Transaction.openRoot())
        {
            int inserted = items.insert(page, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    // Takes up to maxAmount (and at most one normal stack) out of the open page
    public ItemStack extractFromOpenPage(int maxAmount)
    {
        ItemResource resource = items.getResource(page);
        if (resource.isEmpty()) return ItemStack.EMPTY;
        int amount = Math.min(maxAmount, resource.getMaxStackSize());
        try (Transaction transaction = Transaction.openRoot())
        {
            int extracted = items.extract(page, resource, amount, transaction);
            transaction.commit();
            return resource.toStack(extracted);
        }
    }

    private void onItemsChanged()
    {
        setChanged();
        if (level != null && !level.isClientSide())
        {
            block().updateThickness(level, worldPosition, getBlockState(), items.fullness());
            sync();
        }
    }

    // Sends getUpdatePacket() to nearby clients, so the renderer shows the open page's item
    private void sync()
    {
        if (level != null && !level.isClientSide())
        {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- Colours ----

    @Override public int getColor(int region) { return tints.getColor(region); }
    @Override public void setColor(int region, int color) { tints.setColor(region, color); }
    @Override public int getTint(int tintIndex) { return tints.getTint(tintIndex); }

    // ---- Block <-> item ----

    // The book as an item: same block, with its pages, contents and colour
    public ItemStack toItem()
    {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
        // collectComponents() runs collectImplicitComponents below
        stack.applyComponents(collectComponents());
        return stack;
    }

    // Picked up by a player: the item has the contents, so removing the block must not drop anything
    public void markPickedUp()
    {
        pickedUp = true;
    }

    // Item placed (or a book created by the death handler): load its contents and colour
    @Override
    protected void applyImplicitComponents(DataComponentGetter components)
    {
        super.applyImplicitComponents(components);
        BookContents contents = components.get(DataComponentRegistry.BOOK_CONTENTS.get());
        if (contents != null) loadContents(contents);

        DyedItemColor dyed = components.get(DataComponents.DYED_COLOR);
        if (dyed != null && getTintRegionCount() > 0) tints.setColor(0, dyed.rgb());
    }

    // Block turned into an item (picked up, or creative pick-block with Ctrl)
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        components.set(DataComponentRegistry.BOOK_CONTENTS.get(), new BookContents(getPageCount(), page, items.copyToList()));
        if (getTintRegionCount() > 0 && tints.getColor(0) != libTintBlockEntity.UNDYED)
        {
            components.set(DataComponents.DYED_COLOR, new DyedItemColor(tints.getColor(0)));
        }
    }

    private int getTintRegionCount()
    {
        return block().getTintRegionCount();
    }

    private void loadContents(BookContents contents)
    {
        items = newHandler(contents.pages());
        List<ResourceStack<ItemResource>> stacks = contents.stacks();
        for (int index = 0; index < Math.min(stacks.size(), contents.pages()); index++)
        {
            ResourceStack<ItemResource> stack = stacks.get(index);
            if (!stack.isEmpty()) items.set(index, stack.resource(), stack.amount());
        }
        page = Math.floorMod(contents.page(), contents.pages());
    }

    // ---- Removed by anything other than a pickup (explosion, /setblock...): drop the book with its contents ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (pickedUp || !(level instanceof ServerLevel)) return;
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), toItem());
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        tints.save(output);
        output.putInt(TAG_PAGES, getPageCount());
        output.putInt(TAG_PAGE, page);
        items.serialize(output);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        tints.load(input);
        items = newHandler(input.getIntOr(TAG_PAGES, block().defaultPages()));
        // deserialize replaces the slot list with the saved one (and its size)
        items.deserialize(input);
        page = Math.floorMod(input.getIntOr(TAG_PAGE, 0), getPageCount());
    }

    // ---- Syncing to clients: everything, so the renderer knows the open page and the colour ----

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
