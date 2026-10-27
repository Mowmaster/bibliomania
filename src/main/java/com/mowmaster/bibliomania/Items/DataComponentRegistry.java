package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BiblioReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookContents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// Data components replace item NBT since 1.20.5: each piece of item data is its own typed, registered component
public class DataComponentRegistry
{
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BiblioReferences.MODID);

    // persistent = saved with the item; with no networkSynchronized codec, the same codec is used to sync it to
    // clients (they need it for the item model's thickness)
    public static final Supplier<DataComponentType<BookContents>> BOOK_CONTENTS = COMPONENTS.registerComponentType(
            "book_contents", builder -> builder.persistent(BookContents.CODEC));

    public static void register(IEventBus eventBus)
    {
        COMPONENTS.register(eventBus);
    }
}
