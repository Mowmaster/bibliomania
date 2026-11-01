package com.mowmaster.bibliomania.Blocks.CardCatalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookItem;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerContents;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.mowlib.paging.IPagedStorageItem;
import com.mowmaster.mowlib.paging.PagedContents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

// What a card catalog drawer is set to:
// - ITEM:  an item (icon = the item, one of it, components and all): opening the drawer has the library's lecterns
//          show that item from the whole library (see ReadingLecternBlockEntity#selectItem)
// - BOOK:  one particular book (an Almanac, Bestiary, Book of Essence, Book of Barter...; icon = that kind of book):
//          opening it turns the lecterns to that book. Books are told apart by a bibliomania:book_id given to the book
//          when the drawer is set (it stays with the book while it's an item, on a shelf or in an inventory)
// - TRADE: one trade of one Trader's Ledger (book id + the trade's number; icon = what the trade gives): opening it
//          turns the lecterns to that Ledger with that trade picked. Drawn emerald green, and can't be dyed.
public record DrawerEntry(Kind kind, ItemStack icon, Optional<UUID> book, int trade)
{
    public enum Kind { ITEM, BOOK, TRADE }

    // Kind saved by name ("ITEM"...); xmap turns a codec of one type into one of another, both ways
    private static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(Kind::valueOf, Kind::name);

    public static final Codec<DrawerEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    KIND_CODEC.fieldOf("kind").forGetter(DrawerEntry::kind),
                    ItemStack.CODEC.fieldOf("icon").forGetter(DrawerEntry::icon),
                    UUIDUtil.CODEC.optionalFieldOf("book").forGetter(DrawerEntry::book),
                    Codec.INT.optionalFieldOf("trade", 0).forGetter(DrawerEntry::trade))
            .apply(instance, DrawerEntry::new));

    // A drawer number with its entry, for saving the drawers that are set
    public record Slot(int drawer, DrawerEntry entry)
    {
        public static final Codec<Slot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("drawer").forGetter(Slot::drawer),
                        DrawerEntry.CODEC.fieldOf("entry").forGetter(Slot::entry))
                .apply(instance, Slot::new));
    }

    // The entry a drawer clicked with `held` gets, or null if it can't be set from it:
    // - a Trader's Ledger: its picked trade
    // - an item book (a storage book): the item on its open page (nothing on it: nothing set)
    // - any other Bibliomania book: that book
    // - anything else: that item
    // Books get their bibliomania:book_id here if they don't have one yet (server: `held` is the real stack).
    public static @Nullable DrawerEntry from(ItemStack held)
    {
        if (held.isEmpty()) return null;
        if (held.getItem() instanceof VillagerBookItem)
        {
            VillagerContents contents = VillagerBookItem.contents(held);
            MerchantOffer offer = contents.currentOffer();
            if (offer == null) return null;
            int trade = Math.floorMod(contents.page(), contents.offers().size());
            return new DrawerEntry(Kind.TRADE, offer.getResult().copyWithCount(1), Optional.of(bookId(held)), trade);
        }
        if (held.getItem() instanceof IPagedStorageItem paged)
        {
            PagedContents contents = paged.contents(held);
            ResourceStack<ItemResource> open = contents.stack(contents.selectedPage());
            if (open.isEmpty()) return null;
            return new DrawerEntry(Kind.ITEM, open.resource().toStack(1), Optional.empty(), 0);
        }
        if (held.getItem() instanceof BlockItem item && item.getBlock() instanceof BookBlock)
        {
            return new DrawerEntry(Kind.BOOK, new ItemStack(held.getItem()), Optional.of(bookId(held)), 0);
        }
        return new DrawerEntry(Kind.ITEM, held.copyWithCount(1), Optional.empty(), 0);
    }

    // The book's id, given one now if it has none
    public static UUID bookId(ItemStack book)
    {
        UUID id = book.get(DataComponentRegistry.BOOK_ID.get());
        if (id == null)
        {
            id = UUID.randomUUID();
            book.set(DataComponentRegistry.BOOK_ID.get(), id);
        }
        return id;
    }
}
