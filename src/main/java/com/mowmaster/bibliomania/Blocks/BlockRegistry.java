package com.mowmaster.bibliomania.Blocks;

import com.mowmaster.bibliomania.BiblioReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.Book.DeathBookBlock;
import com.mowmaster.bibliomania.Blocks.Book.TintableBookBlock;
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

public class BlockRegistry
{
    public static final DeferredRegister.Blocks BCBLOCKS = DeferredRegister.createBlocks(BiblioReferences.MODID);

    // ---- Books: items per page by cover (as in 1.20), 8 pages unless the recipe gives more ----

    public static final DeferredBlock<BookBlock> GRASS_BOOK = registerBook("grass_book",
            properties -> new BookBlock(bookProperties(properties, SoundType.GRASS), 8, 8));
    public static final DeferredBlock<TintableBookBlock> WOOL_BOOK = registerBook("wool_book",
            properties -> new TintableBookBlock(bookProperties(properties, SoundType.WOOL), 16, 8));
    public static final DeferredBlock<TintableBookBlock> LEATHER_BOOK = registerBook("leather_book",
            properties -> new TintableBookBlock(bookProperties(properties, SoundType.WOOD), 32, 8));
    public static final DeferredBlock<DeathBookBlock> DEATH_BOOK = registerBook("death_book",
            properties -> new DeathBookBlock(bookProperties(properties, SoundType.SOUL_SAND)));

    // Every book, for the creative tab and the block entity type
    public static final List<DeferredBlock<? extends BookBlock>> BOOKS = List.of(GRASS_BOOK, WOOL_BOOK, LEATHER_BOOK, DEATH_BOOK);

    // Books can't be mined (strength -1, like bedrock; left-click takes items out instead) or blown up,
    // aren't pushed by pistons, and don't hide the faces of blocks next to them (noOcclusion)
    private static BlockBehaviour.Properties bookProperties(BlockBehaviour.Properties properties, SoundType sound)
    {
        return properties.strength(-1f, 3600000f).sound(sound).noOcclusion().pushReaction(PushReaction.BLOCK);
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

    // Book items don't stack (each carries its own contents)
    private static <T extends Block> DeferredBlock<T> registerBook(String name, Function<BlockBehaviour.Properties, T> function)
    {
        DeferredBlock<T> toReturn = BCBLOCKS.registerBlock(name, function);
        ItemRegistry.CBITEMS.registerItem(name, properties -> new BlockItem(toReturn.get(), properties.useBlockDescriptionPrefix().stacksTo(1)));
        return toReturn;
    }

    public static void register(IEventBus eventBus)
    {
        BCBLOCKS.register(eventBus);
    }
}
