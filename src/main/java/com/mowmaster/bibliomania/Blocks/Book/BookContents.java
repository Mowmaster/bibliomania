package com.mowmaster.bibliomania.Blocks.Book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.StorageHandler.libBulkItemHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.List;

// What a book item carries (the "bibliomania:book_contents" data component): its page count, the page it's open at,
// and what each page holds. The block entity reads this when the book is placed and writes it back when it's picked up.
// Recipes set it on their result to make bigger books, e.g. {"bibliomania:book_contents": {"pages": 12}}.
// A record is an immutable class whose fields, constructor, getters, equals and hashCode are generated from the
// header. Data components must be immutable and compare by value, so records suit them.
public record BookContents(int pages, int page, List<ResourceStack<ItemResource>> stacks)
{
    public static final int DEFAULT_PAGES = 8;
    public static final BookContents EMPTY = new BookContents(DEFAULT_PAGES, 0, List.of());

    // Only "pages" is needed; a missing page or stack list means a new, empty book
    public static final Codec<BookContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(1, 256).optionalFieldOf("pages", DEFAULT_PAGES).forGetter(BookContents::pages),
                    Codec.INT.optionalFieldOf("page", 0).forGetter(BookContents::page),
                    libBulkItemHandler.STACK_CODEC.listOf().optionalFieldOf("stacks", List.of()).forGetter(BookContents::stacks))
            .apply(instance, BookContents::new));

    // A compact constructor runs before the fields are set: copy the list so nobody can change it afterwards
    public BookContents
    {
        stacks = List.copyOf(stacks);
    }

    public long totalAmount()
    {
        long total = 0;
        for (ResourceStack<ItemResource> stack : stacks) total += stack.amount();
        return total;
    }

    // How full the book is, 0 to 1, given how much each page holds
    public float fullness(int pageCapacity)
    {
        return (float) totalAmount() / ((long) pageCapacity * pages);
    }
}
