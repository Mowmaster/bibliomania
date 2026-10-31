package com.mowmaster.bibliomania.EventHandlers;

import com.mowmaster.bibliomania.Items.ItemRegistry;
import com.mowmaster.mowlib.knapping.KnappingRecipe;
import com.mowmaster.mowlib.knapping.KnappingRecipes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Bibliomania's knapping recipes, run by MowLib's KnappingHandler (replaces our old KnappingHandler):
// flint in both hands (both used up), right-click
//   - any stone (#minecraft:base_stone_overworld) -> a stone knife blade
//   - any log (#minecraft:logs)                    -> an oak knife handle
// Call register() from Bibliomania#commonSetup (inside event.enqueueWork).
public final class BibliomaniaKnapping
{
    private BibliomaniaKnapping() {}

    public static void register()
    {
        // () -> new ItemStack(...) is a lambda: the result is made fresh each time someone knaps
        KnappingRecipes.register(KnappingRecipe.of(Items.FLINT, Items.FLINT, BlockTags.BASE_STONE_OVERWORLD,
                () -> new ItemStack(ItemRegistry.KNIFEPART_BLADE_STONE.get())));
        KnappingRecipes.register(KnappingRecipe.of(Items.FLINT, Items.FLINT, BlockTags.LOGS,
                () -> new ItemStack(ItemRegistry.KNIFEPART_HANDLE_OAK.get())));
    }
}
