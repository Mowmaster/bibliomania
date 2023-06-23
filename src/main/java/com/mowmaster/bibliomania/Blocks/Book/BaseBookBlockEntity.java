package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.mowlib.BlockEntities.MowLibBaseFilterableBlock;
import com.mowmaster.mowlib.BlockEntities.MowLibBaseFilterableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BaseBookBlockEntity extends MowLibBaseFilterableBlockEntity {
    public BaseBookBlockEntity(BlockEntityType<?> p_155228_, BlockPos p_155229_, BlockState p_155230_) {
        super(p_155228_, p_155229_, p_155230_);
    }


    //Allows inserting of filters, work cards, redstone, and glowstone


}
