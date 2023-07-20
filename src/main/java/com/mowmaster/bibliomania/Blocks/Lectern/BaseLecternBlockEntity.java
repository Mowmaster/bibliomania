package com.mowmaster.bibliomania.Blocks.Lectern;

import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockItem;
import com.mowmaster.bibliomania.Registry.DeferredBlockEntityTypes;
import com.mowmaster.bibliomania.Utils.BibliomaniaItemUtils;
import com.mowmaster.mowlib.BlockEntities.MowLibBaseFilterableBlockEntity;
import com.mowmaster.mowlib.api.TransportAndStorage.IFilterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mowmaster.bibliomania.Blocks.Book.BaseBookBlock.BOOK_THICKNESS;
import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class BaseLecternBlockEntity  extends MowLibBaseFilterableBlockEntity
{
    private ItemStackHandler itemHandler = createItemHandlerBookStarter();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> this.itemHandler);
    private ItemStackHandler itemHandlerBookSlot = createItemHandlerPrivateBookSlot();
    private LazyOptional<IItemHandler> itemCapabilityBookSlot = LazyOptional.of(() -> this.itemHandlerBookSlot);

    private List<ItemStack> stacksList = new ArrayList<>();
    private int bookQuality = 0;
    private int bookCover = 0;
    private int currentPage = 0;
    private int pagesCount = 0;
    private BaseLecternBlockEntity getBookEntity() { return this; }
    public int getBookQuality() { return this.bookQuality; }
    public int getBookBlockThickness()
    {
        BlockState thisBlock = getLevel().getBlockState(getPos());
        if(thisBlock.hasProperty(BOOK_THICKNESS))
        {
            return thisBlock.getValue(BOOK_THICKNESS);
        }

        return 0;
    }

    public BaseLecternBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(DeferredBlockEntityTypes.BOOK_STARTER.get(), p_155229_, p_155230_);
    }

    @Override
    public void update() {
        BlockState state = level.getBlockState(getPos());
        this.level.sendBlockUpdated(getPos(), state, state, 3);
        this.setChanged();
    }

    public ItemStackHandler createItemHandlerBookStarter() {
        return new ItemStackHandler(8) {
            @Override
            public void onLoad() {
                super.onLoad();
            }

            @Override
            public void onContentsChanged(int slot) {
                update();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                //Run filter checks here(slot==0)?(true):(false)
                if(slot == getCurrentSlot())
                {
                    IFilterItem filter = getIFilterItem();
                    if(filter == null || !filter.getFilterDirection().insert())return true;
                    return filter.canAcceptItems(getFilterInBlockEntity(),stack);
                }

                return false;
            }

            @Override
            public int getSlots() {
                //hardcode for starter book
                return 8;
            }

            @Override
            public int getStackLimit(int slot, @Nonnull ItemStack stack) {
                //Run filter checks here
                IFilterItem filter = getIFilterItem();
                if(filter == null || !filter.getFilterDirection().insert())return super.getStackLimit(slot, stack);
                return filter.canAcceptCountItems(getBookEntity(), getFilterInBlockEntity(),  stack.getMaxStackSize(), getSlotSizeLimit(),stack);
                //return super.getStackLimit(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {

                int baseStackLimit = 4;
                if(getBookQuality()>0)
                {
                    return baseStackLimit+getBookQuality();
                }
                //Hopefully never mess with this again
                //Amount of items allowed in the slot --- may use for bibliomania???
                return (super.getSlotLimit(slot)<baseStackLimit)?(super.getSlotLimit(slot)):(baseStackLimit);
            }

            @Nonnull
            @Override
            public ItemStack getStackInSlot(int slot) {

                return super.getStackInSlot((slot>getSlots())?(0):(slot));
            }

            /*
                Inserts an ItemStack into the given slot and return the remainder. The ItemStack should not be modified in this function!
                Note: This behaviour is subtly different from IFluidHandler.fill(FluidStack, IFluidHandler.FluidAction)
                Params:
                    slot – Slot to insert into.
                    stack – ItemStack to insert. This must not be modified by the item handler.
                    simulate – If true, the insertion is only simulated
                Returns:
                    The remaining ItemStack that was not inserted (if the entire stack is accepted, then return an empty ItemStack).
                    May be the same as the input ItemStack if unchanged, otherwise a new ItemStack.
                    The returned ItemStack can be safely modified after.
            */
            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                /*IPedestalFilter filter = getIPedestalFilter();
                if(filter != null)
                {
                    if(filter.getFilterDirection().insert())
                    {
                        int countAllowed = filter.canAcceptCountItems(getPedestal(),stack);
                        ItemStack modifiedStack = stack.copy();
                        super.insertItem((slot>getSlots())?(0):(slot), modifiedStack, simulate);
                        ItemStack returnedStack = modifiedStack.copy();
                        returnedStack.setCount(stack.getCount() - countAllowed);
                        return returnedStack;
                    }
                }*/

                return super.insertItem((slot>getSlots())?(0):(slot), stack, simulate);
            }

            /*
                Extracts an ItemStack from the given slot.
                The returned value must be empty if nothing is extracted,
                otherwise its stack size must be less than or equal to amount and ItemStack.getMaxStackSize().
                Params:
                    slot – Slot to extract from.
                    amount – Amount to extract (may be greater than the current stack's max limit)
                    simulate – If true, the extraction is only simulated
                Returns:
                    ItemStack extracted from the slot, must be empty if nothing can be extracted.
                    The returned ItemStack can be safely modified after, so item handlers should return a new or copied stack.
             */
            @Nonnull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {

                if(slot == getCurrentSlot())
                {
                    IFilterItem filter = getIFilterItem();
                    if(filter == null || !filter.getFilterDirection().extract())return super.extractItem((slot>getSlots())?(0):(slot), amount, simulate);
                    return super.extractItem((slot>getSlots())?(0):(slot), Math.min(amount, (filter == null)?(amount):((!filter.getFilterDirection().extract())?(amount):(filter.canAcceptCountItems(getBookEntity(),getFilterInBlockEntity(),  getStackInSlot((slot>getSlots())?(0):(slot)).getMaxStackSize(), getSlotSizeLimit(),getStackInSlot((slot>getSlots())?(0):(slot)))))), simulate);

                }

                return ItemStack.EMPTY;
            }
        };
    }

    public ItemStackHandler createItemHandlerPrivateBookSlot() {
        return new ItemStackHandler(1) {
            @Override
            public void onLoad() {
                super.onLoad();
            }

            @Override
            public void onContentsChanged(int slot) {
                update();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                //Run filter checks here(slot==0)?(true):(false)
                if(stack.getItem() instanceof BaseBookBlockItem)
                {
                    return true;
                }

                return false;
            }

            @Override
            public int getSlots() {
                return 1;
            }

            @Override
            public int getStackLimit(int slot, @Nonnull ItemStack stack) {
                return 1;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Nonnull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return super.getStackInSlot((slot>getSlots())?(0):(slot));
            }

            /*
                Inserts an ItemStack into the given slot and return the remainder. The ItemStack should not be modified in this function!
                Note: This behaviour is subtly different from IFluidHandler.fill(FluidStack, IFluidHandler.FluidAction)
                Params:
                    slot – Slot to insert into.
                    stack – ItemStack to insert. This must not be modified by the item handler.
                    simulate – If true, the insertion is only simulated
                Returns:
                    The remaining ItemStack that was not inserted (if the entire stack is accepted, then return an empty ItemStack).
                    May be the same as the input ItemStack if unchanged, otherwise a new ItemStack.
                    The returned ItemStack can be safely modified after.
            */

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                ItemStack getStackToReturn = super.insertItem(slot, stack, simulate);
                if(getStackToReturn.isEmpty())
                {
                    CompoundTag getTag = stack.getOrCreateTag();
                    loadFromItem(getTag);
                }
                return getStackToReturn;
            }

            /*
                            Extracts an ItemStack from the given slot.
                            The returned value must be empty if nothing is extracted,
                            otherwise its stack size must be less than or equal to amount and ItemStack.getMaxStackSize().
                            Params:
                                slot – Slot to extract from.
                                amount – Amount to extract (may be greater than the current stack's max limit)
                                simulate – If true, the extraction is only simulated
                            Returns:
                                ItemStack extracted from the slot, must be empty if nothing can be extracted.
                                The returned ItemStack can be safely modified after, so item handlers should return a new or copied stack.
                         */

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack getStackToReturn = super.extractItem(slot, amount, simulate);
                if(!getStackToReturn.isEmpty())
                {
                    CompoundTag getTag = getStackToReturn.getOrCreateTag();
                    getStackToReturn.setTag(saveToItem(getTag));
                }
                return getStackToReturn;
            }
        };
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        //Item automation in top/bottom
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if(side.equals(Direction.UP) || side.equals(Direction.DOWN))
            {
                return itemCapability.cast();
            }
        }
        //Book automation in sides
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if(side.equals(Direction.NORTH) || side.equals(Direction.EAST) || side.equals(Direction.SOUTH) || side.equals(Direction.WEST))
            {
                return itemCapabilityBookSlot.cast();
            }
        }
        return super.getCapability(cap, side);
    }

    /*============================================================================
    ==============================================================================
    ===========================     ITEM START       =============================
    ==============================================================================
    ============================================================================*/

    public boolean hasItem() {
        int firstPartialOrNonEmptySlot = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(i);
            if(stackInSlot.getCount() < stackInSlot.getMaxStackSize() || stackInSlot.isEmpty()) {
                firstPartialOrNonEmptySlot = i;
                break;
            }
        }

        return !itemHandler.getStackInSlot(firstPartialOrNonEmptySlot).isEmpty();
    }

    public Optional<Integer> maybeFirstNonEmptySlot() {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if(!itemHandler.getStackInSlot(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public boolean hasItemFirst() {
        return maybeFirstNonEmptySlot().isPresent();
    }

    public Optional<Integer> maybeLastNonEmptySlot() {
        for (int i = itemHandler.getSlots() - 1; i >= 0; i--) {
            if(!itemHandler.getStackInSlot(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public Optional<Integer> maybeFirstSlotWithSpaceForMatchingItem(ItemStack stackToMatch) {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(i);
            if (stackInSlot.isEmpty() || (stackInSlot.getCount() < stackInSlot.getMaxStackSize() && ItemHandlerHelper.canItemStacksStack(stackInSlot, stackToMatch))) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public boolean hasSpaceForItem(ItemStack stackToMatch) {
        return maybeFirstSlotWithSpaceForMatchingItem(stackToMatch).isPresent();
    }

    public ItemStack getItemInPedestal() {
        return maybeFirstNonEmptySlot().map(slot -> itemHandler.getStackInSlot(slot)).orElse(ItemStack.EMPTY);
    }

    public ItemStack getMatchingItemInPedestalOrEmptySlot(ItemStack stackToMatch) {
        return maybeFirstSlotWithSpaceForMatchingItem(stackToMatch).map(slot -> itemHandler.getStackInSlot(slot)).orElse(ItemStack.EMPTY);

    }

    public ItemStack getItemInPedestalFirst() {
        return maybeFirstNonEmptySlot().map(slot -> itemHandler.getStackInSlot(slot)).orElse(ItemStack.EMPTY);

    }

    public int getPedestalSlots() { return itemHandler.getSlots(); }

    public List<ItemStack> getItemStacks() {
        List<ItemStack> listed = new ArrayList<>();
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                listed.add(itemHandler.getStackInSlot(i));
            }
        }
        return listed;
    }

    public int getCurrentSlot()
    {
        return (this.currentPage<=0)?(0):(this.currentPage);
    }

    public void iterateCurrentSlot(int value)
    {
        if((getCurrentSlot()+value < itemHandler.getSlots()) && (getCurrentSlot()+value >= 0))
        {
            this.currentPage +=value;
        }
        else if(getCurrentSlot()+value >= itemHandler.getSlots())
        {
            this.currentPage = 0;
        }
        else
        {
            this.currentPage = itemHandler.getSlots()-1;
        }
        update();
    }

    public void setCurrentSlot(int value)
    {
        if((value < itemHandler.getSlots()) && (value >= 0))
        {
            this.currentPage = value;
        }
        else if(value >= itemHandler.getSlots())
        {
            this.currentPage = 0;
        }
        else
        {
            this.currentPage = itemHandler.getSlots()-1;
        }
        update();
    }

    public ItemStack getItemInPedestal(int slot) {
        if (itemHandler.getSlots() > slot) {
            return itemHandler.getStackInSlot(slot);
        } else {
            return ItemStack.EMPTY;
        }
    }

    public ItemStack removeItem(int numToRemove, boolean simulate) {
        return maybeLastNonEmptySlot().map(slot -> itemHandler.extractItem(slot, numToRemove, simulate)).orElse(ItemStack.EMPTY);
    }

    public ItemStack removeItemFromSlotCount(int slot, int numToRemove, boolean simulate) {
        return itemHandler.extractItem(slot, numToRemove, simulate);
    }

    public ItemStack removeItemStack(ItemStack stackToRemove, boolean simulate) {
        int matchingSlotNumber = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if(ItemHandlerHelper.canItemStacksStack(itemHandler.getStackInSlot(i), stackToRemove)) {
                matchingSlotNumber = i;
                break;
            }
        }
        return itemHandler.extractItem(matchingSlotNumber, stackToRemove.getCount(), simulate);
    }

    public ItemStack removeItem(boolean simulate) {
        return maybeLastNonEmptySlot().map(slot -> {
            ItemStack stack = itemHandler.extractItem(slot, itemHandler.getStackInSlot(slot).getCount(), simulate);
            //update();
            return stack;
        }).orElse(ItemStack.EMPTY);
    }

    public ItemStack removeItemFromSlot(int slot, boolean simulate) {
        return itemHandler.extractItem(slot, itemHandler.getStackInSlot(slot).getCount(), simulate);
    }

    //If resulting insert stack is empty it means the full stack was inserted
    public boolean addItem(ItemStack itemFromBlock, boolean simulate) {
        return addItemStack(itemFromBlock, simulate).isEmpty();
    }

    //Return result not inserted, if all inserted return empty stack
    public ItemStack addItemStack(ItemStack itemFromBlock, boolean simulate) {
        return maybeFirstSlotWithSpaceForMatchingItem(itemFromBlock).map(slot -> {
            if (itemHandler.isItemValid(slot, itemFromBlock)) {
                ItemStack returner = itemHandler.insertItem(slot, itemFromBlock.copy(), simulate);
                if (!simulate) update();
                return returner;
            }
            return itemFromBlock;
        }).orElse(itemFromBlock);
    }

    public ItemStack addItemStackToSlot(int slot, ItemStack itemFromBlock, boolean simulate) {
        if (itemHandler.isItemValid(slot, itemFromBlock)) {
            ItemStack returner = itemHandler.insertItem(slot, itemFromBlock.copy(), simulate);
            if (!simulate) update();
            return returner;
        }
        return itemFromBlock;
    }

    public int getSlotSizeLimit() {
        return maybeFirstNonEmptySlot().map(slot -> itemHandler.getSlotLimit(slot)).orElse(itemHandler.getSlotLimit(0));
    }

    /*============================================================================
    ==============================================================================
    ===========================      ITEM END        =============================
    ==============================================================================
    ============================================================================*/

    /*============================================================================
    ==============================================================================
    ===========================     Book START       =============================
    ==============================================================================
    ============================================================================*/

    public boolean hasItemBook() {
        int firstPartialOrNonEmptySlot = 0;
        for (int i = 0; i < itemHandlerBookSlot.getSlots(); i++) {
            ItemStack stackInSlot = itemHandlerBookSlot.getStackInSlot(i);
            if(stackInSlot.getCount() < stackInSlot.getMaxStackSize() || stackInSlot.isEmpty()) {
                firstPartialOrNonEmptySlot = i;
                break;
            }
        }

        return !itemHandlerBookSlot.getStackInSlot(firstPartialOrNonEmptySlot).isEmpty();
    }

    public Optional<Integer> maybeFirstNonEmptySlotBookStack() {
        for (int i = 0; i < itemHandlerBookSlot.getSlots(); i++) {
            if(!itemHandlerBookSlot.getStackInSlot(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public boolean hasFirstBookStack() {
        return maybeFirstNonEmptySlotBookStack().isPresent();
    }

    public Optional<Integer> maybeLastNonEmptySlotBookStack() {
        for (int i = itemHandlerBookSlot.getSlots() - 1; i >= 0; i--) {
            if(!itemHandlerBookSlot.getStackInSlot(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public Optional<Integer> maybeFirstSlotWithSpaceForMatchingItemBook(ItemStack stackToMatch) {
        for (int i = 0; i < itemHandlerBookSlot.getSlots(); i++) {
            ItemStack stackInSlot = itemHandlerBookSlot.getStackInSlot(i);
            if (stackInSlot.isEmpty() || (stackInSlot.getCount() < stackInSlot.getMaxStackSize() && ItemHandlerHelper.canItemStacksStack(stackInSlot, stackToMatch))) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public ItemStack getBookStackInLectern() {
        return maybeFirstNonEmptySlotBookStack().map(slot -> itemHandlerBookSlot.getStackInSlot(slot)).orElse(ItemStack.EMPTY);
    }

    public ItemStack getBookStackInLecternFirst() {
        return maybeFirstNonEmptySlotBookStack().map(slot -> itemHandlerBookSlot.getStackInSlot(slot)).orElse(ItemStack.EMPTY);

    }

    public int getLecternNumberOfBookSlots() { return itemHandlerBookSlot.getSlots(); }

    public List<ItemStack> getItemStackListOfLecternBooks() {
        List<ItemStack> listed = new ArrayList<>();
        for (int i = 0; i < itemHandlerBookSlot.getSlots(); i++) {
            if (!itemHandlerBookSlot.getStackInSlot(i).isEmpty()) {
                listed.add(itemHandlerBookSlot.getStackInSlot(i));
            }
        }
        return listed;
    }



    public ItemStack getBookStackInLectern(int slot) {
        if (itemHandlerBookSlot.getSlots() > slot) {
            return itemHandlerBookSlot.getStackInSlot(slot);
        } else {
            return ItemStack.EMPTY;
        }
    }

    public ItemStack removeBookStackFromLectern(boolean simulate) {
        return maybeLastNonEmptySlot().map(slot -> {
            ItemStack stack = itemHandlerBookSlot.extractItem(slot, itemHandlerBookSlot.getStackInSlot(slot).getCount(), simulate);
            //update();
            return stack;
        }).orElse(ItemStack.EMPTY);
    }

    //If resulting insert stack is empty it means the full stack was inserted
    public boolean addBookStackToLectern(ItemStack itemFromBlock, boolean simulate) {
        return addItemStack(itemFromBlock, simulate).isEmpty();
    }

    //Return result not inserted, if all inserted return empty stack
    public ItemStack addBookStackToLecternWithValidation(ItemStack itemFromBlock, boolean simulate) {
        return maybeFirstSlotWithSpaceForMatchingItem(itemFromBlock).map(slot -> {
            if (itemHandlerBookSlot.isItemValid(slot, itemFromBlock)) {
                ItemStack returner = itemHandlerBookSlot.insertItem(slot, itemFromBlock.copy(), simulate);
                if (!simulate) update();
                return returner;
            }
            return itemFromBlock;
        }).orElse(itemFromBlock);
    }

    public int getLecternBookStackSlotLimit() {
        return maybeFirstNonEmptySlot().map(slot -> itemHandlerBookSlot.getSlotLimit(slot)).orElse(itemHandlerBookSlot.getSlotLimit(0));
    }

    /*============================================================================
    ==============================================================================
    ===========================      Book END        =============================
    ==============================================================================
    ============================================================================*/



    public void triggerNeighborChange()
    {
        setCurrentSlot(getBookEntity().getRedstonePower());
    }



    @Override
    public void load(CompoundTag p_155245_) {
        super.load(p_155245_);
        CompoundTag invTag = p_155245_.getCompound(MODID + "_bookstorage");
        itemHandler.deserializeNBT(invTag);
        CompoundTag invTagPrivate = p_155245_.getCompound(MODID + "_bookslotstorage");
        itemHandlerBookSlot.deserializeNBT(invTag);
        this.bookQuality = p_155245_.getInt(MODID + "_bookquality");
        this.bookCover = p_155245_.getInt(MODID + "_bookcover");
        this.currentPage = p_155245_.getInt(MODID + "_currentpage");
        this.pagesCount = p_155245_.getInt(MODID + "_bookpagescount");
    }

    public void loadFromItem(CompoundTag p_155245_) {
        CompoundTag invTag = p_155245_.getCompound(MODID + "_bookstorage");
        itemHandler.deserializeNBT(invTag);
        this.bookQuality = p_155245_.getInt(MODID + "_bookquality");
        this.bookCover = p_155245_.getInt(MODID + "_bookcover");
        this.currentPage = p_155245_.getInt(MODID + "_currentpage");
        this.pagesCount = p_155245_.getInt(MODID + "_bookpagescount");
    }

    @Override
    public CompoundTag save(CompoundTag p_58888_) {
        super.save(p_58888_);
        p_58888_.put(MODID + "_bookstorage", itemHandler.serializeNBT());
        p_58888_.put(MODID + "_bookslotstorage", itemHandlerBookSlot.serializeNBT());
        p_58888_.putInt(MODID + "_bookquality", this.bookQuality);
        p_58888_.putInt(MODID + "_bookcover", this.bookCover);
        p_58888_.putInt(MODID + "_currentpage", this.currentPage);
        p_58888_.putInt(MODID + "_bookpagescount", this.pagesCount);
        return p_58888_;
    }

    public CompoundTag saveToItem(CompoundTag p_58888_) {
        super.save(p_58888_);
        p_58888_.putInt(MODID + "_bookquality", this.bookQuality);
        p_58888_.putInt(MODID + "_bookcover", this.bookCover);
        p_58888_.putInt(MODID + "_currentpage", this.currentPage);
        return BibliomaniaItemUtils.writeItemHandlerToNBT(p_58888_,itemHandler,MODID + "_bookstorage");
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        itemCapability.invalidate();
        itemCapabilityBookSlot.invalidate();
    }
}
