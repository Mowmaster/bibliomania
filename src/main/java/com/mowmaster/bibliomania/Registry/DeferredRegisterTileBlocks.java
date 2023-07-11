package com.mowmaster.bibliomania.Registry;

import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlock;
import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class DeferredRegisterTileBlocks
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,MODID);

    public static final RegistryObject<Block> TILE_BOOK_STARTER = registerBookBlock("block_book_starter",
            () -> new BaseBookBlock(BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD)));

    private static <T extends Block> RegistryObject<T> registerBookBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBookBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBookBlockItem(String name, RegistryObject<T> block) {
        DeferredRegisterItems.ITEMS.register(name, () -> new BaseBookBlockItem(block.get(),
                new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
