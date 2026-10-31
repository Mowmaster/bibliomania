package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookContents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// Bibliomania's data components. The item books use MowLib's mowlib:paged_contents; this register also keeps old
// worlds working: books saved with "bibliomania:book_contents" load as "mowlib:paged_contents" (same format).
// An alias only applies because the old name is no longer registered.
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

    // What a Bestiary holds (mob type, counts, breeding cooldown). persistent = saved with the item;
    // networkSynchronized = sent to clients (the tooltip and the placed book's display need it).
    public static final Supplier<DataComponentType<MobBookContents>> MOB_CONTENTS = COMPONENTS.registerComponentType(
            "mob_contents", builder -> builder.persistent(MobBookContents.CODEC).networkSynchronized(MobBookContents.STREAM_CODEC));

    public static void register(IEventBus eventBus)
    {
        COMPONENTS.register(eventBus);
    }
}
