package com.mowmaster.bibliomania.Blocks;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookBlockEntity;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.EssenceBook.EssenceBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookBlock;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    // Every storage book (fluids, experience, energy, and any other StorageBookBlock, also other mods'): the valid
    // blocks are found by looking through all registered blocks (they're all registered before block entity types).
    // The Set constructor allows any number of blocks.
    public static final Supplier<BlockEntityType<StorageBookBlockEntity>> STORAGE_BOOK = CBBLOCKENTITIES.register("storage_book",
            () -> new BlockEntityType<>(StorageBookBlockEntity::new, BuiltInRegistries.BLOCK.stream()
                    .filter(block -> block instanceof StorageBookBlock).collect(Collectors.toSet())));

    // The Farmer's Almanac
    public static final Supplier<BlockEntityType<CropBookBlockEntity>> CROP_BOOK = CBBLOCKENTITIES.register("crop_book",
            () -> new BlockEntityType<>(CropBookBlockEntity::new, BlockRegistry.CROP_BOOK.get()));

    // The Book of Essence
    public static final Supplier<BlockEntityType<EssenceBookBlockEntity>> ESSENCE_BOOK = CBBLOCKENTITIES.register("essence_book",
            () -> new BlockEntityType<>(EssenceBookBlockEntity::new, BlockRegistry.ESSENCE_BOOK.get()));

    public static void register(IEventBus eventBus)
    {
        CBBLOCKENTITIES.register(eventBus);
    }
}
