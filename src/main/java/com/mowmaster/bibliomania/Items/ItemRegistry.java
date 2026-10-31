package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.BibliomaniaTags;
import com.mowmaster.mowlib.items.tools.CraftingToolItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
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
    // Parts are knapped with flint in both hands (MowLib's KnappingHandler; recipes in BibliomaniaKnapping), then crafted together

    public static final DeferredItem<Item> KNIFEPART_BLADE_STONE = CBITEMS.registerSimpleItem("knifepart_blade_stone");
    public static final DeferredItem<Item> KNIFEPART_HANDLE_OAK = CBITEMS.registerSimpleItem("knifepart_handle_oak");
    // Stone durability and enchantability; 0.5 attack damage and -2 attack speed as in 1.20.
    // Item.Properties#tool sets the TOOL (mining), WEAPON and attribute components and makes it unstackable.
    // The last argument is how many seconds a hit disables a shield for (0: knives don't).
    public static final DeferredItem<CraftingToolItem> KNIFE_STONE = CBITEMS.registerItem("knife_stone",
            properties -> new CraftingToolItem(properties.tool(ToolMaterial.STONE, BibliomaniaTags.MINEABLE_WITH_KNIFE, 0.5f, -2.0f, 0f)));

    // In creative tab order
    public static final List<DeferredItem<? extends Item>> MATERIALS = List.of(
            KNIFEPART_BLADE_STONE, KNIFEPART_HANDLE_OAK, KNIFE_STONE,
            FIBERS_GRASS, THREAD_TWISTED_GRASS, WEAVE_GRASS,
            FIBERS_WOOL, THREAD_TWISTED_WOOL, WEAVE_WOOL,
            BOOKBLOCK_STARTER);

    public static void register(IEventBus eventBus)
    {
        CBITEMS.register(eventBus);
    }
}
