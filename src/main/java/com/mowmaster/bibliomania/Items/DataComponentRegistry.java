package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.EssenceBook.EssenceContents;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookContents;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
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

    // What a Book of Essence holds (EssenceContents)
    public static final Supplier<DataComponentType<EssenceContents>> ESSENCE_CONTENTS = COMPONENTS.registerComponentType(
            "essence_contents", builder -> builder.persistent(EssenceContents.CODEC).networkSynchronized(EssenceContents.STREAM_CODEC));

    // Whether a Book of Fluids / Experience is "activated" (its hand and inventory actions are on; see StorageBookItem).
    // Codec.BOOL saves it as true/false; ByteBufCodecs.BOOL sends it as one byte.
    public static final Supplier<DataComponentType<Boolean>> ACTIVATED = COMPONENTS.registerComponentType(
            "activated", builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    // The Breakfast Club: for each slot, the uses left of the cake (or other edible block) being eaten there;
    // 0 = none started. A list of whole numbers: saved as [5, 0, 0, ...]; sent as a list of variable-length ints.
    public static final Supplier<DataComponentType<List<Integer>>> USES_LEFT = COMPONENTS.registerComponentType(
            "uses_left", builder -> builder.persistent(Codec.INT.listOf())
                    .networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())));

    public static void register(IEventBus eventBus)
    {
        COMPONENTS.register(eventBus);
    }
}
