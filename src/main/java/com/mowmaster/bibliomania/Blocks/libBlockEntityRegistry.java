package com.mowmaster.bibliomania.Blocks;

import com.mowmaster.bibliomania.BiblioReferences;
import com.mowmaster.bibliomania.TintHandler.IColorableBlock;
import com.mowmaster.bibliomania.TintHandler.libTintBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class libBlockEntityRegistry
{
    public static final DeferredRegister<BlockEntityType<?>> CBBLOCKENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BiblioReferences.MODID);

    public static final Supplier<BlockEntityType<libTintBlockEntity>> TINT_BLOCK_ENTITY = CBBLOCKENTITIES.register("tint_block_entity",
            () -> new BlockEntityType<>(libTintBlockEntity::new, colorableBlocks()));

    // Every block in our register that implements IColorableBlock
    private static Block[] colorableBlocks()
    {
        List<Block> blocks = new ArrayList<>();
        for (var holder : BlockRegistry.BCBLOCKS.getEntries())
        {
            if (holder.get() instanceof IColorableBlock colorable && colorable.usesTintBlockEntity()) blocks.add(holder.get());
        }
        return blocks.toArray(new Block[0]);
    }

    public static void register(IEventBus eventBus)
    {
        CBBLOCKENTITIES.register(eventBus);
    }
}
