package com.mowmaster.bibliomania.Compat.JEI;

import com.mowmaster.bibliomania.Registry.DeferredRegisterItems;
import com.mowmaster.bibliomania.Registry.DeferredRegisterTileBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

@mezz.jei.api.JeiPlugin
public class JeiPlugin implements IModPlugin
{
    protected static IJeiRuntime runtime;

    public static IJeiRuntime getJeiRuntime() {
        return runtime;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(MODID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;

        this.registerIngredientDescription(registration, DeferredRegisterItems.FIBERS_GRASS.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.FIBERS_WOOL.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.THREAD_TWISTED_GRASS.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.THREAD_TWISTED_WOOL.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.THREAD_SPUN_GRASS.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.WEAVE_GRASS.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.WEAVE_WOOL.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.WOVEN_GRASS.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.STARTER_BOOK_BLOCK.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.KNIFEPART_BLADE_STONE.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.KNIFEPART_HANDLE_OAK.get());
        this.registerIngredientDescription(registration, DeferredRegisterItems.KNIFE_STONE.get());
        this.registerIngredientDescription(registration, DeferredRegisterTileBlocks.TILE_BOOK_STARTER.get());
        this.registerIngredientInteraction(registration, DeferredRegisterTileBlocks.TILE_BOOK_STARTER.get());

    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IModPlugin.super.registerRecipeTransferHandlers(registration);
        IStackHelper stackHelper = registration.getJeiHelpers().getStackHelper();
        IRecipeTransferHandlerHelper handlerHelper = registration.getTransferHelper();
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {

    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiPlugin.runtime = jeiRuntime;
        JEISettings.setJeiLoaded(true);
    }

    public void registerIngredientInfo(IRecipeRegistration registration, ItemLike ingredient) {
        registration.addIngredientInfo(new ItemStack(ingredient.asItem()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei." + MODID + ".item." + ForgeRegistries.ITEMS.getKey(ingredient.asItem()).getPath() + ".description"));
    }

    public void registerIngredientBase(IRecipeRegistration registration, ItemLike ingredient) {
        registration.addIngredientInfo(new ItemStack(ingredient.asItem()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei." + MODID + "." + ForgeRegistries.ITEMS.getKey(ingredient.asItem()).getPath() + ".base_description"));
    }

    public void registerIngredientDescription(IRecipeRegistration registration, ItemLike ingredient) {
        registration.addIngredientInfo(new ItemStack(ingredient.asItem()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei." + MODID + "." + ForgeRegistries.ITEMS.getKey(ingredient.asItem()).getPath() + ".description"));
    }

    public void registerIngredientInteraction(IRecipeRegistration registration, ItemLike ingredient) {
        registration.addIngredientInfo(new ItemStack(ingredient.asItem()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei." + MODID + "." + ForgeRegistries.ITEMS.getKey(ingredient.asItem()).getPath() + ".interaction"));
    }

    public void registerIngredientCrafting(IRecipeRegistration registration, ItemLike ingredient) {
        registration.addIngredientInfo(new ItemStack(ingredient.asItem()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei." + MODID + "." + ForgeRegistries.ITEMS.getKey(ingredient.asItem()).getPath() + ".crafting"));
    }

}
