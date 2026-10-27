package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BiblioReferences;
import com.mowmaster.bibliomania.Blocks.BlockRegistry;
import com.mowmaster.bibliomania.Blocks.Book.BookContents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class CreativeTabRegistry
{
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BiblioReferences.MODID);

    // Every book in each size its recipes make (8 pages plain; copper 12, iron 16, gold 24, as in 1.20)
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BIBLIOMANIA_TAB = TABS.register("bibliomania", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bibliomania"))
            .icon(() -> new ItemStack(BlockRegistry.LEATHER_BOOK.get()))
            .displayItems((parameters, output) -> {
                for (int pages : List.of(8, 12)) output.accept(book(BlockRegistry.GRASS_BOOK.get(), pages));
                for (int pages : List.of(8, 12, 16)) output.accept(book(BlockRegistry.WOOL_BOOK.get(), pages));
                for (int pages : List.of(8, 12, 16, 24)) output.accept(book(BlockRegistry.LEATHER_BOOK.get(), pages));
                output.accept(BlockRegistry.DEATH_BOOK.get());
            }).build());

    private static ItemStack book(ItemLike book, int pages)
    {
        ItemStack stack = new ItemStack(book);
        stack.set(DataComponentRegistry.BOOK_CONTENTS.get(), new BookContents(pages, 0, List.of()));
        return stack;
    }

    public static void register(IEventBus eventBus)
    {
        TABS.register(eventBus);
    }
}
