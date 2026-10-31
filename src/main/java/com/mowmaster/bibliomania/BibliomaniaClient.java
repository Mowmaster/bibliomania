package com.mowmaster.bibliomania;

import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookTooltip;
import com.mowmaster.bibliomania.ClientSide.ClientMobBookTooltip;
import com.mowmaster.bibliomania.ClientSide.MobBookRenderer;
import com.mowmaster.mowlib.client.SpinningItemRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Bibliomania.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Bibliomania.MODID, value = Dist.CLIENT)
public class BibliomaniaClient {
    public BibliomaniaClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        Bibliomania.LOGGER.info("HELLO FROM CLIENT SETUP");
        Bibliomania.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    // MowLib registers the tint sources (for every IColorableBlock), the page tooltip, the paged_fullness item
    // model property and the page keys. Books only need MowLib's renderer for their type.

    // The spinning item above an open book (BookBlockEntity is an ISpinningItemHolder)
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(libBlockEntityRegistry.BOOK.get(), SpinningItemRenderer::new);
        // The Bestiary's spinning mob
        event.registerBlockEntityRenderer(libBlockEntityRegistry.MOB_BOOK.get(), MobBookRenderer::new);
        // The Book of Essence's mob (the same renderer: both block entities are IMobBookDisplay)
        event.registerBlockEntityRenderer(libBlockEntityRegistry.ESSENCE_BOOK.get(), MobBookRenderer::new);
    }

    // The Bestiary's and Book of Essence's tooltip data -> the class that draws it
    @SubscribeEvent
    public static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event)
    {
        event.register(MobBookTooltip.class, ClientMobBookTooltip::new);
    }
}
