package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.Atlas.AtlasData;
import com.mowmaster.bibliomania.Blocks.CompactingBook.CompactingContents;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.BlockPos;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.CropBook.CropContents;
import com.mowmaster.bibliomania.Blocks.BarterBook.BarterContents;
import com.mowmaster.bibliomania.Blocks.EssenceBook.EssenceContents;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookContents;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerContents;
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

    // The MowLib filter an item book holds (BookFilter): it only takes what the filter allows. A component value must
    // be immutable (with equals/hashCode), which an ItemStack isn't, so it's kept as an ItemStackTemplate: the
    // immutable form of a stack (item, count, components). BookFilter turns it back into a stack.
    public static final Supplier<DataComponentType<ItemStackTemplate>> BOOK_FILTER = COMPONENTS.registerComponentType(
            "book_filter", builder -> builder.persistent(ItemStackTemplate.CODEC).networkSynchronized(ItemStackTemplate.STREAM_CODEC));

    // A storage book's library priority (LibraryPriority): set on the placed book with the Priority Scroll, kept on the
    // item. Codec.INT saves a number; VAR_INT sends it in as few bytes as it needs.
    public static final Supplier<DataComponentType<Integer>> PRIORITY = COMPONENTS.registerComponentType(
            "priority", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // What a Compacting Book holds (CompactingContents: its chain of tiers and one pool)
    public static final Supplier<DataComponentType<CompactingContents>> COMPACTING_CONTENTS = COMPONENTS.registerComponentType(
            "compacting_contents", builder -> builder.persistent(CompactingContents.CODEC).networkSynchronized(CompactingContents.STREAM_CODEC));

    // An Atlas's zoom and markers (AtlasData); its explored map is kept by the world (AtlasSavedData)
    public static final Supplier<DataComponentType<AtlasData>> ATLAS = COMPONENTS.registerComponentType(
            "atlas", builder -> builder.persistent(AtlasData.CODEC).networkSynchronized(AtlasData.STREAM_CODEC));

    // What a Farmer's Almanac holds (CropContents)
    public static final Supplier<DataComponentType<CropContents>> CROP_CONTENTS = COMPONENTS.registerComponentType(
            "crop_contents", builder -> builder.persistent(CropContents.CODEC).networkSynchronized(CropContents.STREAM_CODEC));

    // What a Trader's Ledger holds (VillagerContents)
    public static final Supplier<DataComponentType<VillagerContents>> VILLAGER_CONTENTS = COMPONENTS.registerComponentType(
            "villager_contents", builder -> builder.persistent(VillagerContents.CODEC).networkSynchronized(VillagerContents.STREAM_CODEC));

    // The Library Controller a Librarian's Quill is working on (BlockPos.CODEC saves [x, y, z]; STREAM_CODEC sends it)
    public static final Supplier<DataComponentType<BlockPos>> LIBRARY_LINK = COMPONENTS.registerComponentType(
            "library_link", builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    // A book's id, given when a Card Catalog drawer is set to it (so the drawer finds that one book on the shelves).
    // UUIDUtil: vanilla's codecs for UUIDs.
    public static final Supplier<DataComponentType<UUID>> BOOK_ID = COMPONENTS.registerComponentType(
            "book_id", builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    // An Atlas copied in the crafting grid (AtlasCopyRecipe), not yet carried: the id of the Atlas whose explored map
    // it gets. The first time it's carried, the server copies that map to a new id of its own (AtlasItem.finishCopy).
    public static final Supplier<DataComponentType<UUID>> ATLAS_COPY_OF = COMPONENTS.registerComponentType(
            "atlas_copy_of", builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    // What a Book of Barter holds (BarterContents)
    public static final Supplier<DataComponentType<BarterContents>> BARTER_CONTENTS = COMPONENTS.registerComponentType(
            "barter_contents", builder -> builder.persistent(BarterContents.CODEC).networkSynchronized(BarterContents.STREAM_CODEC));

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
