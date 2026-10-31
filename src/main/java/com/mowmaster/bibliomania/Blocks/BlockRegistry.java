package com.mowmaster.bibliomania.Blocks;

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
import com.mowmaster.bibliomania.Blocks.BreakfastBook.BreakfastBookItem;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookBlock;
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

    // The Farmer's Almanac: grows a crop on a soil (see CropBookBlock)
    public static final DeferredBlock<CropBookBlock> CROP_BOOK = registerCropBook("crop_book",
            properties -> new CropBookBlock(bookProperties(properties, SoundType.GRASS)));

    // The Book of Essence: for the mobs a Bestiary can't hold
    public static final DeferredBlock<EssenceBookBlock> ESSENCE_BOOK = registerEssenceBook("essence_book",
            properties -> new EssenceBookBlock(bookProperties(properties, SoundType.WOOD)));

    // "The Breakfast Club": food and drinkable potions only, and it can be eaten from (BreakfastBookItem)
    public static final DeferredBlock<BreakfastBookBlock> BREAKFAST_BOOK = registerBreakfastBook("breakfast_book",
            properties -> new BreakfastBookBlock(bookProperties(properties, SoundType.WOOD)));

    // Every item book, for the creative tab and the block entity type
    public static final List<DeferredBlock<? extends BookBlock>> BOOKS = List.of(GRASS_BOOK, WOOL_BOOK, LEATHER_BOOK, DEATH_BOOK, BREAKFAST_BOOK);

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
