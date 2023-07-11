package com.mowmaster.bibliomania.Recipes;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;


public final class BibliomaniaDeferredRecipeSerializers
{
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);


    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
