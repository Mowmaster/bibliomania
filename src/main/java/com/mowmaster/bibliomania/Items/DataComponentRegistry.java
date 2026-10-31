package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// Bibliomania registers no data components of its own any more: books use MowLib's mowlib:paged_contents.
// This register stays only to keep old worlds working: books saved with "bibliomania:book_contents" load as
// "mowlib:paged_contents" (same format). An alias only applies because the old name is no longer registered.
public class DataComponentRegistry
{
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BibliomaniaReferences.MODID);

    // A static block runs once, when the class is first used: before register() below, as aliases must be
    static
    {
        COMPONENTS.addAlias(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "book_contents"),
                Identifier.fromNamespaceAndPath("mowlib", "paged_contents"));
    }

    public static void register(IEventBus eventBus)
    {
        COMPONENTS.register(eventBus);
    }
}
