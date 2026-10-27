package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.bibliomania.TintHandler.IColorableBlock;

// A book with a dyeable cover (wool, leather). The colour lives in the BookBlockEntity and travels with the item as
// minecraft:dyed_color. Dye it in a crafting grid (book + dyes, see BookDyeRecipe); in-world dyeing isn't possible
// because right-clicking with a dye stores it, and sneaking with an item in hand skips the block entirely.
// The cover's model faces use tintindex 0. newBlockEntity comes from BookBlock (a superclass method beats the
// interface's default), so this still gets a BookBlockEntity.
public class TintableBookBlock extends BookBlock implements IColorableBlock
{
    public TintableBookBlock(Properties properties, int pageCapacity, int defaultPages)
    {
        super(properties, pageCapacity, defaultPages);
    }

    // BookBlock's getTintRegionCount (0) would otherwise win: a superclass method beats an interface default
    @Override
    public int getTintRegionCount()
    {
        return 1;
    }

    // The colours are kept in BookBlockEntity, not the plain tint block entity
    @Override
    public boolean usesTintBlockEntity()
    {
        return false;
    }
}
