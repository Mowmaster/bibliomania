package com.mowmaster.bibliomania.Blocks;

import com.mowmaster.bibliomania.Blocks.Requestor.StorageDispenserBlockEntity;
import com.mowmaster.bibliomania.Blocks.Requestor.StorageRequestorBlockEntity;
import com.mowmaster.bibliomania.Blocks.Deposit.StorageDepositBlockEntity;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Lectern.ReadingLecternBlockEntity;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookBlockEntity;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.EssenceBook.EssenceBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.BarterBook.BarterBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookBlockEntity;
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

    // The Trader's Ledger
    public static final Supplier<BlockEntityType<VillagerBookBlockEntity>> VILLAGER_BOOK = CBBLOCKENTITIES.register("villager_book",
            () -> new BlockEntityType<>(VillagerBookBlockEntity::new, BlockRegistry.VILLAGER_BOOK.get()));

    // The Book of Barter
    public static final Supplier<BlockEntityType<BarterBookBlockEntity>> BARTER_BOOK = CBBLOCKENTITIES.register("barter_book",
            () -> new BlockEntityType<>(BarterBookBlockEntity::new, BlockRegistry.BARTER_BOOK.get()));

    // The Book of Essence
    public static final Supplier<BlockEntityType<EssenceBookBlockEntity>> ESSENCE_BOOK = CBBLOCKENTITIES.register("essence_book",
            () -> new BlockEntityType<>(EssenceBookBlockEntity::new, BlockRegistry.ESSENCE_BOOK.get()));

    // Both Bookcases (full and half)
    public static final Supplier<BlockEntityType<BookcaseBlockEntity>> BOOKCASE = CBBLOCKENTITIES.register("bookcase",
            () -> new BlockEntityType<>(BookcaseBlockEntity::new, BlockRegistry.BOOKCASE.get(), BlockRegistry.HALF_BOOKCASE.get()));

    // The Reading Lectern
    public static final Supplier<BlockEntityType<ReadingLecternBlockEntity>> READING_LECTERN = CBBLOCKENTITIES.register("reading_lectern",
            () -> new BlockEntityType<>(ReadingLecternBlockEntity::new, BlockRegistry.READING_LECTERN.get()));

    // The Library Controller
    public static final Supplier<BlockEntityType<LibraryControllerBlockEntity>> LIBRARY_CONTROLLER = CBBLOCKENTITIES.register("library_controller",
            () -> new BlockEntityType<>(LibraryControllerBlockEntity::new, BlockRegistry.LIBRARY_CONTROLLER.get()));

    // Every Card Catalog size
    public static final Supplier<BlockEntityType<CardCatalogBlockEntity>> CARD_CATALOG = CBBLOCKENTITIES.register("card_catalog",
            () -> new BlockEntityType<>(CardCatalogBlockEntity::new, BlockRegistry.CARD_CATALOGS.stream().map(block -> (Block) block.get()).toArray(Block[]::new)));

    // Both Storage Deposits (half and full)
    public static final Supplier<BlockEntityType<StorageDepositBlockEntity>> STORAGE_DEPOSIT = CBBLOCKENTITIES.register("storage_deposit",
            () -> new BlockEntityType<>(StorageDepositBlockEntity::new, BlockRegistry.STORAGE_DEPOSIT.get(), BlockRegistry.STORAGE_DEPOSIT_FULL.get()));

    // The Patterned Storage Requestor
    public static final Supplier<BlockEntityType<StorageRequestorBlockEntity>> STORAGE_REQUESTOR = CBBLOCKENTITIES.register("storage_requestor",
            () -> new BlockEntityType<>(StorageRequestorBlockEntity::new, BlockRegistry.STORAGE_REQUESTOR.get()));

    // The Storage Dispenser
    public static final Supplier<BlockEntityType<StorageDispenserBlockEntity>> STORAGE_DISPENSER = CBBLOCKENTITIES.register("storage_dispenser",
            () -> new BlockEntityType<>(StorageDispenserBlockEntity::new, BlockRegistry.STORAGE_DISPENSER.get()));

    public static void register(IEventBus eventBus)
    {
        CBBLOCKENTITIES.register(eventBus);
    }
}
