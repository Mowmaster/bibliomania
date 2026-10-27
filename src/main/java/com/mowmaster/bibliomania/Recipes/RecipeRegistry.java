package com.mowmaster.bibliomania.Recipes;

import com.mojang.serialization.MapCodec;
import com.mowmaster.bibliomania.BiblioReferences;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class RecipeRegistry
{
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BiblioReferences.MODID);

    // The recipe has no settings, so both codecs just produce the single instance.
    // Used by data/bibliomania/recipe/book_dye.json: {"type": "bibliomania:book_dye"}
    public static final Supplier<RecipeSerializer<BookDyeRecipe>> BOOK_DYE = RECIPE_SERIALIZERS.register("book_dye",
            () -> new RecipeSerializer<>(MapCodec.unit(BookDyeRecipe.INSTANCE), StreamCodec.unit(BookDyeRecipe.INSTANCE)));

    public static void register(IEventBus eventBus)
    {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
