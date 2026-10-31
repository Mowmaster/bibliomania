package com.mowmaster.bibliomania.Items;

import com.mojang.serialization.MapCodec;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// Bibliomania's recipe serializers (the "type" a recipe JSON names). Registered from the Bibliomania constructor.
// A recipe with nothing to configure (its result is worked out in code) saves as just its type: MapCodec.unit and
// StreamCodec.unit always give the same instance, like MowLib's MowLibRecipes.
public final class RecipeRegistry
{
    private RecipeRegistry() {}

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BibliomaniaReferences.MODID);

    // The Trader's Ledger's reset / new villager recipe (data/bibliomania/recipe/villager_book_villager.json)
    public static final Supplier<RecipeSerializer<VillagerBookRecipe>> VILLAGER_BOOK = SERIALIZERS.register("villager_book",
            () -> new RecipeSerializer<>(MapCodec.unit(VillagerBookRecipe.INSTANCE), StreamCodec.unit(VillagerBookRecipe.INSTANCE)));

    public static void register(IEventBus eventBus)
    {
        SERIALIZERS.register(eventBus);
    }
}
