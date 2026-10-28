package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.StorageHandler.libBulkItemHandler;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// The book as an item. In inventories it works like a bundle:
// - Holding the book, left-click an item in a slot: stores it.      Holding an item, left-click the book: stores it.
// - Holding the book, right-click an empty slot: takes the selected page out into it.
//   Empty-handed, right-click the book: takes the selected page out onto your cursor.
// - Hover the book and scroll: selects a page (see BookScrollHandler); the tooltip shows the contents like a bundle.
// Items go onto pages already holding the same item first, then empty pages. Each take-out is at most one stack.
// The death book can be emptied this way but not filled. In the world the book is placed and used as a block.
public class BookItem extends BlockItem
{
    public BookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    private BookBlock book()
    {
        return (BookBlock) getBlock();
    }

    // The item's contents, or an empty book with the block's default pages
    public BookContents contents(ItemStack stack)
    {
        BookContents contents = stack.get(DataComponentRegistry.BOOK_CONTENTS.get());
        return contents != null ? contents : new BookContents(book().defaultPages(), 0, List.of());
    }

    // ---- Holding the book, clicking a slot ----

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        if (self.getCount() != 1) return false;
        ItemStack other = slot.getItem();

        // The death book can't be filled by hand: let the click do what it normally would
        if (clickAction == ClickAction.PRIMARY && !other.isEmpty() && book().allowsManualInsert())
        {
            int fits = insert(self, other, true);
            if (fits > 0)
            {
                // safeTake respects slots you can't take from (e.g. crafting results); store what actually came out
                ItemStack taken = slot.safeTake(other.getCount(), fits, player);
                insert(self, taken, false);
                player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
            }
            else
            {
                player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            }
            slotsChanged(player);
            return true;
        }
        if (clickAction == ClickAction.SECONDARY && other.isEmpty())
        {
            ItemStack removed = extractSelected(self);
            if (!removed.isEmpty())
            {
                ItemStack left = slot.safeInsert(removed);
                if (!left.isEmpty()) insert(self, left, false); // the slot didn't take it all: put the rest back
                player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
            }
            slotsChanged(player);
            return true;
        }
        return false;
    }

    // ---- Holding something (or nothing), clicking the book ----

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (self.getCount() != 1) return false;

        if (clickAction == ClickAction.PRIMARY && !other.isEmpty() && book().allowsManualInsert())
        {
            if (slot.allowModification(player))
            {
                int inserted = insert(self, other, false);
                if (inserted > 0)
                {
                    other.shrink(inserted);
                    player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
                }
                else
                {
                    player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
                }
            }
            slotsChanged(player);
            return true;
        }
        if (clickAction == ClickAction.SECONDARY && other.isEmpty())
        {
            if (slot.allowModification(player))
            {
                ItemStack removed = extractSelected(self);
                if (!removed.isEmpty())
                {
                    carriedItem.set(removed);
                    player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
                }
            }
            slotsChanged(player);
            return true;
        }
        // Anything else (e.g. left-click with an empty hand picks the book up as normal)
        return false;
    }

    // Tells the open container the book changed, so the change is sent to the client
    private static void slotsChanged(Player player)
    {
        player.containerMenu.slotsChanged(player.getInventory());
    }

    // ---- Contents ----

    // A handler holding a copy of the item's pages, to reuse the storage rules (capacity, one item per page)
    private libBulkItemHandler handlerFor(BookContents contents)
    {
        libBulkItemHandler items = new libBulkItemHandler(contents.pages(), book().pageCapacity(), book()::accepts, () -> {});
        List<ResourceStack<ItemResource>> stacks = contents.stacks();
        for (int index = 0; index < Math.min(stacks.size(), contents.pages()); index++)
        {
            ResourceStack<ItemResource> stack = stacks.get(index);
            if (!stack.isEmpty()) items.set(index, stack.resource(), stack.amount());
        }
        return items;
    }

    // Stores as much of the stack as fits, onto matching pages first, then empty ones. Does not change the stack.
    // With simulate, only works out how much would fit. Returns how many went in (or would).
    public int insert(ItemStack book, ItemStack stack, boolean simulate)
    {
        if (stack.isEmpty() || !book().allowsManualInsert()) return 0;
        ItemResource resource = ItemResource.of(stack);
        if (!book().accepts(resource)) return 0;

        BookContents contents = contents(book);
        libBulkItemHandler items = handlerFor(contents);
        // A transaction that isn't committed is undone when it closes: that's how simulate works
        try (Transaction transaction = Transaction.openRoot())
        {
            int inserted = 0;
            for (int pass = 0; pass < 2; pass++)
            {
                for (int index = 0; index < items.size() && inserted < stack.getCount(); index++)
                {
                    ItemResource onPage = items.getResource(index);
                    boolean wanted = pass == 0 ? onPage.equals(resource) : onPage.isEmpty();
                    if (wanted) inserted += items.insert(index, resource, stack.getCount() - inserted, transaction);
                }
            }
            if (!simulate && inserted > 0)
            {
                transaction.commit();
                book.set(DataComponentRegistry.BOOK_CONTENTS.get(), new BookContents(contents.pages(), contents.page(), items.copyToList()));
            }
            return inserted;
        }
    }

    // Takes up to one stack off the selected page
    public ItemStack extractSelected(ItemStack book)
    {
        BookContents contents = contents(book);
        libBulkItemHandler items = handlerFor(contents);
        int page = Math.floorMod(contents.page(), contents.pages());
        ItemResource resource = items.getResource(page);
        if (resource.isEmpty()) return ItemStack.EMPTY;

        try (Transaction transaction = Transaction.openRoot())
        {
            int extracted = items.extract(page, resource, resource.getMaxStackSize(), transaction);
            transaction.commit();
            book.set(DataComponentRegistry.BOOK_CONTENTS.get(), new BookContents(contents.pages(), page, items.copyToList()));
            return resource.toStack(extracted);
        }
    }

    // ---- Page selection (scrolling over the book) ----

    // The next page holding something, forward (+1) or back (-1) from the selected one, wrapping round.
    // Returns the current page if no other page holds anything.
    public int nextFilledPage(ItemStack book, int direction)
    {
        BookContents contents = contents(book);
        List<ResourceStack<ItemResource>> stacks = contents.stacks();
        for (int step = 1; step <= contents.pages(); step++)
        {
            int page = Math.floorMod(contents.page() + direction * step, contents.pages());
            if (page < stacks.size() && !stacks.get(page).isEmpty()) return page;
        }
        return contents.page();
    }

    public void setPage(ItemStack book, int page)
    {
        BookContents contents = contents(book);
        book.set(DataComponentRegistry.BOOK_CONTENTS.get(),
                new BookContents(contents.pages(), Math.floorMod(page, contents.pages()), contents.stacks()));
    }

    // ---- Tooltip: the vanilla bundle tooltip, built from the pages that hold something ----

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack)
    {
        BookContents contents = contents(stack);
        List<ItemStackTemplate> items = new ArrayList<>();
        int selected = -1;
        for (int index = 0; index < contents.stacks().size(); index++)
        {
            ResourceStack<ItemResource> page = contents.stacks().get(index);
            if (page.isEmpty()) continue;
            if (index == contents.page()) selected = items.size();
            items.add(ItemStackTemplate.fromNonEmptyStack(page.resource().toStack(page.amount())));
        }
        if (items.isEmpty()) return Optional.empty();

        BundleContents.Mutable bundle = new BundleContents(items).asMutable();
        // Highlights the selected page's item and shows its name, like a bundle's selected item
        if (selected >= 0) bundle.toggleSelectedItem(selected);
        return Optional.of(new BundleTooltip(bundle.toImmutable()));
    }
}
