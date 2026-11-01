package com.mowmaster.bibliomania.Blocks.BuilderBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.transfer.item.ItemResource;

// The Builder's Book: a storage book for blocks only, 8 pages ("slots") of 64. Placed, it works like any storage
// book; in the hand it places blocks from its slots (BuilderBookItem).
public class BuilderBookBlock extends BookBlock
{
    public static final int PAGES = 8;
    public static final int PAGE_CAPACITY = 64;

    public BuilderBookBlock(Properties properties)
    {
        super(properties, PAGE_CAPACITY, PAGES);
    }

    // Placeable blocks only (an item that places a block), and never other books
    @Override
    public boolean accepts(ItemResource resource)
    {
        return resource.getItem() instanceof BlockItem blockItem && !(blockItem.getBlock() instanceof BookBlock);
    }
}
