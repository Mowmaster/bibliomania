package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.Atlas.AtlasItem;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.BibliomaniaTags;
import com.mowmaster.mowlib.items.tools.CraftingToolItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ItemRegistry {

    public static final DeferredRegister.Items CBITEMS = DeferredRegister.createItems(BibliomaniaReferences.MODID);

    // ---- Book-making materials (same ids as 1.20) ----
    // grass fibers (cut grass with a knife) -> twisted thread -> weave -> grass book cover
    // wool fibers (string or wool + knife)  -> twisted thread -> weave -> wool book cover (leather books use the thread)

    public static final DeferredItem<Item> FIBERS_GRASS = CBITEMS.registerSimpleItem("fibers_grass");
    public static final DeferredItem<Item> FIBERS_WOOL = CBITEMS.registerSimpleItem("fibers_wool");
    public static final DeferredItem<Item> THREAD_TWISTED_GRASS = CBITEMS.registerSimpleItem("thread_twisted_grass");
    public static final DeferredItem<Item> THREAD_TWISTED_WOOL = CBITEMS.registerSimpleItem("thread_twisted_wool");
    public static final DeferredItem<Item> WEAVE_GRASS = CBITEMS.registerSimpleItem("weave_grass");
    public static final DeferredItem<Item> WEAVE_WOOL = CBITEMS.registerSimpleItem("weave_wool");
    // The pages of a book: 8 paper cut with a knife
    public static final DeferredItem<Item> BOOKBLOCK_STARTER = CBITEMS.registerSimpleItem("bookblock_starter");

    // ---- Knife ----
    // Parts are knapped with flint in both hands on stone or a log (MowLib Dual Handed recipes: data/bibliomania/mowlib/dual_handed_recipe/), then crafted together

    public static final DeferredItem<Item> KNIFEPART_BLADE_STONE = CBITEMS.registerSimpleItem("knifepart_blade_stone");
    public static final DeferredItem<Item> KNIFEPART_HANDLE_OAK = CBITEMS.registerSimpleItem("knifepart_handle_oak");
    // Stone durability and enchantability; 0.5 attack damage and -2 attack speed as in 1.20.
    // Item.Properties#tool sets the TOOL (mining), WEAPON and attribute components and makes it unstackable.
    // The last argument is how many seconds a hit disables a shield for (0: knives don't).
    public static final DeferredItem<CraftingToolItem> KNIFE_STONE = CBITEMS.registerItem("knife_stone",
            properties -> new CraftingToolItem(properties.tool(ToolMaterial.STONE, BibliomaniaTags.MINEABLE_WITH_KNIFE, 0.5f, -2.0f, 0f)));

    // ---- Reading Glasses ----
    // A helmet (Item.Properties#humanoidArmor sets the armour, equippable and durability components, and makes it
    // unstackable) that always has Reading Goggles (see ReadingGlassesItem)
    public static final DeferredItem<ReadingGlassesItem> READING_GLASSES = CBITEMS.registerItem("reading_glasses",
            properties -> new ReadingGlassesItem(properties.humanoidArmor(ReadingGlassesItem.MATERIAL, ArmorType.HELMET)));

    // ---- Library tools ----
    // The Librarian's Quill links blocks to a library (LibraryQuillItem); the Librarian's Seal locks settings
    public static final DeferredItem<LibraryQuillItem> LIBRARY_QUILL = CBITEMS.registerItem("library_quill",
            properties -> new LibraryQuillItem(properties.stacksTo(1)));
    public static final DeferredItem<LibrarySealItem> LIBRARY_SEAL = CBITEMS.registerItem("library_seal",
            properties -> new LibrarySealItem(properties.stacksTo(1)));

    // The Bookbinder's Bone Folder turns blocks to face the side clicked (BoneFolderItem)
    public static final DeferredItem<BoneFolderItem> BONE_FOLDER = CBITEMS.registerItem("bone_folder",
            properties -> new BoneFolderItem(properties.stacksTo(1)));

    // The Priority Scroll sets which shelves and books a library fills first (PriorityScrollItem)
    public static final DeferredItem<PriorityScrollItem> PRIORITY_SCROLL = CBITEMS.registerItem("priority_scroll",
            properties -> new PriorityScrollItem(properties.stacksTo(1)));

    // The Atlas: a map of where you've been, shown while it's carried (Atlas/AtlasItem)
    public static final DeferredItem<AtlasItem> ATLAS = CBITEMS.registerItem("atlas",
            properties -> new AtlasItem(properties.stacksTo(1)));

    // Atlas unlocks: right-clicked while carrying an Atlas, it may map underground, the Nether or the End
    // (Atlas/AtlasUnlocks; what each unlocks is the bibliomania:atlas_unlock item data map)
    public static final DeferredItem<Item> ATLAS_UNLOCK_UNDERGROUND = CBITEMS.registerSimpleItem("atlas_unlock_underground");
    public static final DeferredItem<Item> ATLAS_UNLOCK_NETHER = CBITEMS.registerSimpleItem("atlas_unlock_nether");
    public static final DeferredItem<Item> ATLAS_UNLOCK_END = CBITEMS.registerSimpleItem("atlas_unlock_end");

    // The Card Catalog's icon for an energy drawer (a red lightning bolt): only ever drawn, so not in the creative tab
    public static final DeferredItem<Item> ENERGY_ICON = CBITEMS.registerSimpleItem("energy_icon");

    // In creative tab order
    public static final List<DeferredItem<? extends Item>> MATERIALS = List.of(
            KNIFEPART_BLADE_STONE, KNIFEPART_HANDLE_OAK, KNIFE_STONE,
            FIBERS_GRASS, THREAD_TWISTED_GRASS, WEAVE_GRASS,
            FIBERS_WOOL, THREAD_TWISTED_WOOL, WEAVE_WOOL,
            BOOKBLOCK_STARTER, READING_GLASSES, LIBRARY_QUILL, LIBRARY_SEAL, BONE_FOLDER, PRIORITY_SCROLL, ATLAS,
            ATLAS_UNLOCK_UNDERGROUND, ATLAS_UNLOCK_NETHER, ATLAS_UNLOCK_END);

    public static void register(IEventBus eventBus)
    {
        CBITEMS.register(eventBus);
    }
}
