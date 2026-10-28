package com.mowmaster.bibliomania;

import com.mowmaster.bibliomania.Blocks.BlockRegistry;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.bibliomania.ClientSide.BookFullnessProperty;
import com.mowmaster.bibliomania.ClientSide.BookRenderer;
import com.mowmaster.bibliomania.ClientSide.libBlockEntityTintSource;
import com.mowmaster.bibliomania.TintHandler.IColorableBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.resources.Identifier;
import com.mowmaster.bibliomania.Blocks.Book.BookTooltip;
import com.mowmaster.bibliomania.ClientSide.ClientBookTooltip;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.ArrayList;
import java.util.List;

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

    // Every colourable block gets one tint source per tint region (region N = model tintindex N)
    @SubscribeEvent
    public static void registerTintBlocks(RegisterColorHandlersEvent.BlockTintSources event)
    {
        for (var holder : BlockRegistry.BCBLOCKS.getEntries())
        {
            Block block = holder.get();
            if (block instanceof IColorableBlock colorable)
            {
                List<BlockTintSource> sources = new ArrayList<>();
                for (int region = 0; region < colorable.getTintRegionCount(); region++)
                {
                    sources.add(new libBlockEntityTintSource(region));
                }
                event.register(sources, block);
            }
        }
    }

    // The spinning item above an open book
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(libBlockEntityRegistry.BOOK.get(), BookRenderer::new);
    }

    // A book's tooltip data -> the class that draws it
    @SubscribeEvent
    public static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event)
    {
        event.register(BookTooltip.class, ClientBookTooltip::new);
    }

    // "bibliomania:book_fullness", used by the book item models to get thicker as they fill
    @SubscribeEvent
    public static void registerRangeProperties(RegisterRangeSelectItemModelPropertyEvent event)
    {
        event.register(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "book_fullness"), BookFullnessProperty.MAP_CODEC);
    }
}
