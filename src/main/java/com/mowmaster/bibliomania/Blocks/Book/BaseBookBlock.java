package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.mowlib.BlockEntities.MowLibBaseFilterableBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class BaseBookBlock extends MowLibBaseFilterableBlock
{

    public static final IntegerProperty BOOK_THICKNESS = IntegerProperty.create("book_thickness", 0, 5);

    public BaseBookBlock(Properties p_152915_) {
        super(p_152915_);
    }


}
