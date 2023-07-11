package com.mowmaster.bibliomania.Registry;


import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockEntityRender;
import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockItem;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = BibliomaniaReferences.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BibliomainaClientRegistry
{
    @SubscribeEvent
    public static void registerItemColor(RegisterColorHandlersEvent.Item event) {

        /*event.getItemColors().register((stack, color) ->
        {if (color == 1) {return BaseBookBlockItem.getBookPage1Color(stack);}
        else if (color == 2) {return BaseBookBlockItem.getBookPage1Color(stack);}
        else if (color == 3) {return BaseBookBlockItem.getBookPage1Color(stack);}
        else if (color == 4) {return BaseBookBlockItem.getBookPage1Color(stack);}
        else if (color == 5) {return BaseBookBlockItem.getBookPage1Color(stack);}
        else if (color == 6) {return BaseBookBlockItem.getBookPage1Color(stack);}
        else if (color == 7) {return BaseBookBlockItem.getBookPage1Color(stack);}
        else if (color == 8) {return BaseBookBlockItem.getBookPage1Color(stack);}
        else {return -1;}}, DeferredRegisterItems.COLOR_APPLICATOR.get());*/

        BibliomaniaItemModelProperties.bibliomaniaItemModes(DeferredRegisterTileBlocks.TILE_BOOK_STARTER.get().asItem());
        BibliomaniaItemModelProperties.bibliomaniaItemModes(DeferredRegisterItems.STARTER_BOOK_BLOCK.get());






        /*
        *
        * TILE ENTITY BLOCKS HERE
        *
         */

    }

    @SubscribeEvent
    public static void registerBlockColor(RegisterColorHandlersEvent.Block event) {
/*
        event.register((blockstate, blockReader, blockPos, color) ->
        {if (color == 1) {return MowLibColorReference.getColorFromStateInt(blockstate);} else {return -1;}}, DeferredRegisterTileBlocks.BLOCK_PEDESTAL.get());*/
    }

    @SubscribeEvent
    public static void registerLayers(FMLClientSetupEvent event)
    {

    }


    public static void registerBlockEntityRenderers()
    {
        BlockEntityRenderers.register(DeferredBlockEntityTypes.BOOK_STARTER.get(), BaseBookBlockEntityRender::new);
    }
}
