package com.mowmaster.bibliomania.Blocks;

import com.mowmaster.bibliomania.Blocks.CompactingBook.CompactingBookItem;
import com.mowmaster.bibliomania.Blocks.CompactingBook.CompactingBookBlock;
import com.mowmaster.bibliomania.Blocks.Requestor.StorageDispenserBlock;
import com.mowmaster.bibliomania.Blocks.Requestor.StorageRequestorBlock;
import com.mowmaster.bibliomania.Blocks.Deposit.StorageDepositBlock;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlock;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlock;
import com.mowmaster.bibliomania.Blocks.Lectern.ReadingLecternBlock;
import com.mowmaster.bibliomania.Blocks.Bookcase.HalfBookcaseBlock;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlock;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.Book.BookItem;
import com.mowmaster.bibliomania.Blocks.Book.DeathBookBlock;
import com.mowmaster.bibliomania.Blocks.Book.TintableBookBlock;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookBlock;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookItem;
import com.mowmaster.bibliomania.Blocks.EssenceBook.EssenceBookBlock;
import com.mowmaster.bibliomania.Blocks.EssenceBook.EssenceBookItem;
import com.mowmaster.bibliomania.Blocks.BreakfastBook.BreakfastBookBlock;
import com.mowmaster.bibliomania.Blocks.BuilderBook.BuilderBookBlock;
import com.mowmaster.bibliomania.Blocks.BuilderBook.BuilderBookItem;
import com.mowmaster.bibliomania.Blocks.BreakfastBook.BreakfastBookItem;
import com.mowmaster.bibliomania.Blocks.BarterBook.BarterBookBlock;
import com.mowmaster.bibliomania.Blocks.BarterBook.BarterBookItem;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookBlock;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookBlock;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookBlock;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookItem;
import com.mowmaster.bibliomania.ServerConfig;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookItem;
import com.mowmaster.bibliomania.Items.ItemRegistry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class BlockRegistry
{
    public static final DeferredRegister.Blocks BCBLOCKS = DeferredRegister.createBlocks(BibliomaniaReferences.MODID);

    // ---- Books: items per page by cover (as in 1.20), 8 pages unless the recipe gives more ----

    public static final DeferredBlock<BookBlock> GRASS_BOOK = registerBook("grass_book",
            properties -> new BookBlock(bookProperties(properties, SoundType.GRASS), 8, 8));
    public static final DeferredBlock<TintableBookBlock> WOOL_BOOK = registerBook("wool_book",
            properties -> new TintableBookBlock(bookProperties(properties, SoundType.WOOL), 16, 8));
    public static final DeferredBlock<TintableBookBlock> LEATHER_BOOK = registerBook("leather_book",
            properties -> new TintableBookBlock(bookProperties(properties, SoundType.WOOD), 32, 8));
    public static final DeferredBlock<DeathBookBlock> DEATH_BOOK = registerBook("death_book",
            properties -> new DeathBookBlock(bookProperties(properties, SoundType.SOUL_SAND)));

    // The Bestiary: holds mobs, not items (its own block entity type and item, so not in BOOKS)
    public static final DeferredBlock<MobBookBlock> MOB_BOOK = registerMobBook("mob_book",
            properties -> new MobBookBlock(bookProperties(properties, SoundType.WOOD)));
    // Books that store one MowLib transport type each (see StorageBookBlock / StorageBookBehaviours)
    public static final DeferredBlock<StorageBookBlock> FLUID_BOOK = registerStorageBook("fluid_book", TransportTypes.FLUIDS, ServerConfig::fluidCapacity);
    public static final DeferredBlock<StorageBookBlock> EXPERIENCE_BOOK = registerStorageBook("experience_book", TransportTypes.EXPERIENCE, ServerConfig::experienceCapacity);
    public static final DeferredBlock<StorageBookBlock> ENERGY_BOOK = registerStorageBook("energy_book", TransportTypes.ENERGY, ServerConfig::energyCapacity);

    // The Compacting Book: one item family (nuggets, ingots, blocks) in one pool (see CompactingBookBlock)
    public static final DeferredBlock<CompactingBookBlock> COMPACTING_BOOK = registerCompactingBook("compacting_book",
            properties -> new CompactingBookBlock(bookProperties(properties, SoundType.WOOD)));

    // The Farmer's Almanac: grows a crop on a soil (see CropBookBlock)
    public static final DeferredBlock<CropBookBlock> CROP_BOOK = registerCropBook("crop_book",
            properties -> new CropBookBlock(bookProperties(properties, SoundType.GRASS)));

    // The Trader's Ledger: a villager to trade with (see VillagerBookBlock)
    public static final DeferredBlock<VillagerBookBlock> VILLAGER_BOOK = registerVillagerBook("villager_book",
            properties -> new VillagerBookBlock(bookProperties(properties, SoundType.WOOD)));

    // The Book of Barter: a piglin that barters for gold (see BarterBookBlock)
    public static final DeferredBlock<BarterBookBlock> BARTER_BOOK = registerBarterBook("barter_book",
            properties -> new BarterBookBlock(bookProperties(properties, SoundType.WOOD)));

    // The Book of Essence: for the mobs a Bestiary can't hold
    public static final DeferredBlock<EssenceBookBlock> ESSENCE_BOOK = registerEssenceBook("essence_book",
            properties -> new EssenceBookBlock(bookProperties(properties, SoundType.WOOD)));

    // "The Breakfast Club": food and drinkable potions only, and it can be eaten from (BreakfastBookItem)
    public static final DeferredBlock<BreakfastBookBlock> BREAKFAST_BOOK = registerBreakfastBook("breakfast_book",
            properties -> new BreakfastBookBlock(bookProperties(properties, SoundType.WOOD)));

    // The Builder's Book: blocks only, and places them from the hand (BuilderBookItem)
    public static final DeferredBlock<BuilderBookBlock> BUILDER_BOOK = registerBuilderBook("builder_book",
            properties -> new BuilderBookBlock(bookProperties(properties, SoundType.WOOD)));

    // ---- Bookcases: six books each, working on the shelf and reachable by hoppers (see BookcaseBlock) ----
    public static final DeferredBlock<BookcaseBlock> BOOKCASE = registerBlock("bookcase",
            properties -> new BookcaseBlock(bookcaseProperties(properties)));
    public static final DeferredBlock<HalfBookcaseBlock> HALF_BOOKCASE = registerBlock("half_bookcase",
            properties -> new HalfBookcaseBlock(bookcaseProperties(properties)));

    // The Library Controller: the core of a library (see LibraryControllerBlock)
    public static final DeferredBlock<LibraryControllerBlock> LIBRARY_CONTROLLER = registerBlock("library_controller",
            properties -> new LibraryControllerBlock(properties.strength(2.5f).sound(SoundType.CHISELED_BOOKSHELF)));

    // Card Catalogs: 1 to 16 drawers (card_catalog_1 to _16; only 1 is crafted, the stonecutter turns any into any)
    public static final List<DeferredBlock<CardCatalogBlock>> CARD_CATALOGS = registerCardCatalogs();

    private static List<DeferredBlock<CardCatalogBlock>> registerCardCatalogs()
    {
        List<DeferredBlock<CardCatalogBlock>> catalogs = new java.util.ArrayList<>();
        for (int drawers = 1; drawers <= 16; drawers++)
        {
            // A lambda can only use variables that never change after they're set ("effectively final"): a copy
            int size = drawers;
            catalogs.add(registerBlock("card_catalog_" + size,
                    properties -> new CardCatalogBlock(properties.strength(2.5f).sound(SoundType.CHISELED_BOOKSHELF).noOcclusion().ignitedByLava(), size)));
        }
        return List.copyOf(catalogs);
    }

    // The Reading Lectern: the way into the Bookcases a work card points at (see ReadingLecternBlock)
    public static final DeferredBlock<ReadingLecternBlock> READING_LECTERN = registerBlock("reading_lectern",
            properties -> new ReadingLecternBlock(properties.strength(2.5f).sound(SoundType.WOOD).noOcclusion().ignitedByLava()));

    // The Storage Deposit: a library's way in (see StorageDepositBlock); the modelled half block and a full block
    public static final DeferredBlock<StorageDepositBlock> STORAGE_DEPOSIT = registerBlock("storage_deposit",
            properties -> new StorageDepositBlock(properties.strength(2.5f).sound(SoundType.WOOD).noOcclusion().ignitedByLava(), false));
    public static final DeferredBlock<StorageDepositBlock> STORAGE_DEPOSIT_FULL = registerBlock("storage_deposit_full",
            properties -> new StorageDepositBlock(properties.strength(2.5f).sound(SoundType.WOOD).ignitedByLava(), true));

    // The library's ways out on a redstone pulse: the Patterned Storage Requestor (StorageRequestorBlock) and the
    // Storage Dispenser (StorageDispenserBlock). Stone, like a Dispenser: mined with a pickaxe
    public static final DeferredBlock<StorageRequestorBlock> STORAGE_REQUESTOR = registerBlock("storage_requestor",
            properties -> new StorageRequestorBlock(properties.strength(3.5f).requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final DeferredBlock<StorageDispenserBlock> STORAGE_DISPENSER = registerBlock("storage_dispenser",
            properties -> new StorageDispenserBlock(properties.strength(3.5f).requiresCorrectToolForDrops().sound(SoundType.STONE)));

    // Wood, mined with an axe like a chiseled bookshelf, and see-through (it's open at the front)
    private static BlockBehaviour.Properties bookcaseProperties(BlockBehaviour.Properties properties)
    {
        return properties.strength(1.5f).sound(SoundType.CHISELED_BOOKSHELF).noOcclusion().ignitedByLava();
    }

    // Every item book, for the creative tab and the block entity type
    public static final List<DeferredBlock<? extends BookBlock>> BOOKS = List.of(GRASS_BOOK, WOOL_BOOK, LEATHER_BOOK, DEATH_BOOK, BREAKFAST_BOOK, BUILDER_BOOK);

    // Books can't be mined (strength -1, like bedrock; left-click takes items out instead) or blown up,
    // aren't pushed by pistons, and don't hide the faces of blocks next to them (noOcclusion)
    private static BlockBehaviour.Properties bookProperties(BlockBehaviour.Properties properties, SoundType sound)
    {
        return properties.strength(-1f, 3600000f).sound(sound).noOcclusion().pushReaction(PushReaction.PUSH_PULL);
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block)
    {
        ItemRegistry.CBITEMS.registerItem(name, properties -> new BlockItem(block.get(), properties.useBlockDescriptionPrefix()));
    }

    // The Compacting Book's item (CompactingBookItem): doesn't stack, holds its chain and pool
    private static <T extends Block> DeferredBlock<T> registerCompactingBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new CompactingBookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    // Book items don't stack (each carries its own contents), and work like bundles in inventories (BookItem)
    private static <T extends Block> DeferredBlock<T> registerBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new BookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    // The Breakfast Club's item: a book you can eat from, with the lore line "By John Hughes" under its name.
    // Lore is the minecraft:lore data component (purple italic lines, like a named item's lore); giving it to the item
    // properties makes every Breakfast Club carry it.
    private static <T extends Block> DeferredBlock<T> registerBreakfastBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new BreakfastBookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)
                .component(DataComponents.LORE, new ItemLore(List.of(
                        Component.translatable("item." + BibliomaniaReferences.MODID + "." + name + ".lore"))))));
        return toReturn;
    }

    private static <T extends Block> DeferredBlock<T> registerBuilderBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new BuilderBookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    // The Bestiary's item captures mobs and has its own inventory clicks (MobBookItem)
    private static <T extends Block> DeferredBlock<T> registerMobBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new MobBookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    private static <T extends Block> DeferredBlock<T> registerEssenceBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new EssenceBookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    private static <T extends Block> DeferredBlock<T> registerCropBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new CropBookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    private static <T extends Block> DeferredBlock<T> registerVillagerBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new VillagerBookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    private static <T extends Block> DeferredBlock<T> registerBarterBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new BarterBookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    // A storage book for any transport type: the block, and its StorageBookItem. Another type (from another mod)
    // gets a book the same way, plus a StorageBookBehaviours.register for what its clicks do.
    // IntSupplier: a function returning an int, here the capacity, read from the config each time
    public static DeferredBlock<StorageBookBlock> registerStorageBook(String name, Supplier<? extends TransportType<?>> type, IntSupplier capacity)
    {
        DeferredBlock<StorageBookBlock> toReturn = BCBLOCKS.registerBlock(name,
                properties -> new StorageBookBlock(bookProperties(properties, SoundType.WOOD), type, capacity));
        ItemRegistry.CBITEMS.registerItem(name, properties -> new StorageBookItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    public static void register(IEventBus eventBus)
    {
        BCBLOCKS.register(eventBus);
    }
}
