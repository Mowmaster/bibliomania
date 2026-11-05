package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.mowlib.blockentity.CBSyncedBlockEntity;
import net.minecraft.world.entity.player.Player;
import com.mowmaster.mowlib.filter.HeldFilters;
import net.minecraft.world.item.ItemStackTemplate;
import com.mowmaster.bibliomania.Blocks.Library.LibraryPriority;
import net.minecraft.world.level.block.Block;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.mowlib.filter.IFilterHolder;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.display.ISpinningItemHolder;
import com.mowmaster.mowlib.paging.IPageHolder;
import com.mowmaster.mowlib.paging.PagedContents;
import com.mowmaster.mowlib.paging.PagedStorage;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.storage.CBBulkItemHandler;
import com.mowmaster.mowlib.tint.CBTintData;
import com.mowmaster.mowlib.tint.ITintHolder;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

// The block entity for every book. The pages are a MowLib PagedStorage (one item type per page, up to the block's
// page capacity), the cover colour a MowLib CBTintData. IPageHolder lets MowLib's page keys turn the open page;
// ISpinningItemHolder makes MowLib's SpinningItemRenderer show the open page's item above the book.
// Only the open page can be filled or emptied by hand. Saved data is the same as before the switch to MowLib.
public class BookBlockEntity extends CBSyncedBlockEntity implements ITintHolder, IPageHolder, ISpinningItemHolder, PickupableBook, IFilterHolder, LibraryPriority.Holder
{
    private final CBTintData tints;
    private final PagedStorage pages;
    // Set while the book is being picked up, so removing the block doesn't also drop it
    private boolean pickedUp = false;
    // Its filter (MowLib HeldFilters), or EMPTY
    private ItemStack filter = ItemStack.EMPTY;
    private static final String TAG_FILTER = "filter";
    // Where it comes among the books on its Bookcase (LibraryPriority); set while placed, kept on the item
    private int priority;
    private static final String TAG_PRIORITY = "priority";

    public BookBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.BOOK.get(), pos, state);
        BookBlock block = (BookBlock) state.getBlock();
        this.tints = new CBTintData(this, block.getTintRegionCount());
        // What a page takes: the book's own rules, and its filter (read when asked, so a new filter counts at once)
        this.pages = new PagedStorage(this, block.pageCapacity(), block.defaultPages(),
                resource -> block.accepts(resource) && HeldFilters.allows(filter, resource), this::onItemsChanged);
    }

    private BookBlock block()
    {
        return (BookBlock) getBlockState().getBlock();
    }

    // ---- Pages (IPageHolder and the book's own clicks) ----

    public CBBulkItemHandler getItems() { return pages.getItems(); }
    @Override public int getPage() { return pages.getPage(); }
    @Override public int getPageCount() { return pages.getPageCount(); }
    @Override public void setPage(int page) { pages.setPage(page); }

    public ItemStack getOpenPageStack() { return pages.getOpenPageStack(); }
    public int insertIntoOpenPage(ItemStack stack) { return pages.insertIntoOpenPage(stack); }
    public ItemStack extractFromOpenPage(int maxAmount) { return pages.extractFromOpenPage(maxAmount); }

    // ---- The spinning item (ISpinningItemHolder, drawn by MowLib's SpinningItemRenderer) ----

    @Override public ItemStack getDisplayedItem() { return pages.getOpenPageStack(); }

    // Just above the pages, which rise with the thickness (the thickness is in the block state, which clients have)
    @Override public double getDisplayHeight() { return (getBlockState().getValue(BookBlock.THICKNESS) + 4) / 16.0; }

    @Override public float getDisplayScale() { return 1.5f; }

    // Contents changed: the book gets thicker or thinner (PagedStorage has already saved and synced)
    private void onItemsChanged()
    {
        if (level != null && !level.isClientSide())
        {
            block().updateThickness(level, worldPosition, getBlockState(), pages.getItems().fullness());
        }
    }

    // ---- Colours ----

    @Override public int getColor(int region) { return tints.getColor(region); }
    @Override public void setColor(int region, int color) { tints.setColor(region, color); }
    @Override public int getTint(int tintIndex) { return tints.getTint(tintIndex); }

    // ---- Filter (IFilterHolder): see MowLib HeldFilters ----

    @Override
    public ItemStack getFilter()
    {
        return filter;
    }

    // One filter from the stack, if it's a filter and there isn't one yet (never into a take-out-only book)
    @Override
    public boolean insertFilter(ItemStack stack)
    {
        if (!filter.isEmpty() || !HeldFilters.isFilter(stack) || !block().allowsManualInsert()) return false;
        filter = stack.split(1);
        filterChanged();
        return true;
    }

    @Override
    public ItemStack removeFilter()
    {
        ItemStack removed = filter;
        filter = ItemStack.EMPTY;
        if (!removed.isEmpty()) filterChanged();
        return removed;
    }

    private void filterChanged()
    {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }


    // ---- Library priority (LibraryPriority) ----

    @Override
    public int getPriority()
    {
        return priority;
    }

    @Override
    public void setPriority(int newPriority)
    {
        if (newPriority == priority) return;
        priority = newPriority;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // The storage books' own pickup (BookBlock#pickUp: the Book of Death spills its items first), for MowLib's
    // pick-up key and the sneak + right-click alike
    @Override
    public void pickUp(ServerLevel level, BlockPos pos, BlockState state, Player player)
    {
        if (state.getBlock() instanceof BookBlock book) book.pickUp(level, pos, state, this, player);
    }

    @Override
    public int maxPriority()
    {
        return LibraryPriority.MAX_BOOK;
    }

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
        PagedContents contents = components.get(MowLibDataComponents.PAGED_CONTENTS.get());
        if (contents != null) pages.load(contents);
        ItemStackTemplate itemFilter = components.get(MowLibDataComponents.HELD_FILTER.get());
        filter = itemFilter == null ? ItemStack.EMPTY : itemFilter.create();
        Integer itemPriority = components.get(DataComponentRegistry.PRIORITY.get());
        priority = itemPriority == null ? 0 : itemPriority;
        // The item's dyed_color onto the cover (does nothing for books without a dyeable cover: no regions)
        tints.applyItemColor(components, 0);
    }

    // Block turned into an item (picked up, or creative pick-block with Ctrl)
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        components.set(MowLibDataComponents.PAGED_CONTENTS.get(), pages.toContents());
        if (!filter.isEmpty()) components.set(MowLibDataComponents.HELD_FILTER.get(), ItemStackTemplate.fromNonEmptyStack(filter));
        if (priority > 0) components.set(DataComponentRegistry.PRIORITY.get(), priority);
        // The cover colour onto the item as dyed_color (nothing if undyed)
        tints.collectItemColor(components);
    }

    // ---- Removed by anything other than a pickup (explosion, /setblock...): drop the book with its contents ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (pickedUp || !(level instanceof ServerLevel)) return;
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), toItem());
    }

    // ---- Saving (keys "color", "pages", "page", "stacks": unchanged) ----

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        tints.save(output);
        pages.save(output);
        // OPTIONAL_CODEC: also saves "no filter" (an empty stack)
        output.store(TAG_FILTER, ItemStack.OPTIONAL_CODEC, filter);
        output.putInt(TAG_PRIORITY, priority);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        tints.load(input);
        pages.load(input);
        filter = input.read(TAG_FILTER, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        priority = input.getIntOr(TAG_PRIORITY, 0);
    }

    // ---- Syncing to clients: everything, so the renderer knows the open page and the colour ----

}
