package com.mowmaster.bibliomania.Blocks;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookBlockEntity;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// (The disabled TINT_BLOCK_ENTITY is gone: MowLib registers mowlib:tint_block_entity for every mod's plain dyeable
// blocks.)
public class libBlockEntityRegistry
{
    public static final DeferredRegister<BlockEntityType<?>> CBBLOCKENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BibliomaniaReferences.MODID);

    // Shared by every book (grass, wool, leather, death); the block decides pages, capacity and colours
    public static final Supplier<BlockEntityType<BookBlockEntity>> BOOK = CBBLOCKENTITIES.register("book",
            () -> new BlockEntityType<>(BookBlockEntity::new,
                    BlockRegistry.BOOKS.stream().map(book -> (Block) book.get()).toArray(Block[]::new)));

    // The Bestiary
    public static final Supplier<BlockEntityType<MobBookBlockEntity>> MOB_BOOK = CBBLOCKENTITIES.register("mob_book",
            () -> new BlockEntityType<>(MobBookBlockEntity::new, BlockRegistry.MOB_BOOK.get()));

    public static void register(IEventBus eventBus)
    {
        CBBLOCKENTITIES.register(eventBus);
    }
}
