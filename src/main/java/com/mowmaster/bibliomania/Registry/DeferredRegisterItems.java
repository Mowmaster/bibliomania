package com.mowmaster.bibliomania.Registry;

import com.mowmaster.bibliomania.Items.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;


public class DeferredRegisterItems
{
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Item> FIBERS_GRASS = ITEMS.register("fibers_grass",
            () -> new BaseFiberItem(new Item.Properties()));
    public static final RegistryObject<Item> FIBERS_WOOL = ITEMS.register("fibers_wool",
        () -> new BaseFiberItem(new Item.Properties()));


    public static final RegistryObject<Item> THREAD_TWISTED_GRASS = ITEMS.register("thread_twisted_grass",
            () -> new BaseThreadItem(new Item.Properties(),0));
    public static final RegistryObject<Item> THREAD_TWISTED_WOOL = ITEMS.register("thread_twisted_wool",
            () -> new BaseThreadItem(new Item.Properties(),1));


    public static final RegistryObject<Item> THREAD_SPUN_GRASS = ITEMS.register("thread_spun_grass",
            () -> new BaseThreadItem(new Item.Properties(),1));

    public static final RegistryObject<Item> WEAVE_GRASS = ITEMS.register("weave_grass",
            () -> new BaseWovenFiberItem(new Item.Properties(),0));
    public static final RegistryObject<Item> WEAVE_WOOL = ITEMS.register("weave_wool",
            () -> new BaseWovenFiberItem(new Item.Properties(),1));


    public static final RegistryObject<Item> WOVEN_GRASS = ITEMS.register("woven_grass",
            () -> new BaseWovenFiberItem(new Item.Properties(),1));

    public static final RegistryObject<Item> STARTER_BOOK_BLOCK = ITEMS.register("bookblock_starter",
            () -> new BasePaperStack(new Item.Properties()));

    public static final RegistryObject<Item> KNIFEPART_BLADE_STONE = ITEMS.register("knifepart_blade_stone",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> KNIFEPART_HANDLE_OAK = ITEMS.register("knifepart_handle_oak",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> KNIFE_STONE = ITEMS.register("knife_stone",
            () -> new BaseKnifeItem(0.5F, -2.0F, Tiers.STONE, new Item.Properties()));
    /*public static final RegistryObject<Item> KNIFE_COPPER = ITEMS.register("knife_copper",
            () -> new BaseKnifeItem(0.5F, -2.0F, BibliomaniaMaterialsRegistry.COPPER, new Item.Properties()));
    public static final RegistryObject<Item> KNIFE_IRON = ITEMS.register("knife_iron",
            () -> new BaseKnifeItem(0.5F, -2.0F, Tiers.IRON, new Item.Properties()));
    public static final RegistryObject<Item> KNIFE_GOLD = ITEMS.register("knife_gold",
            () -> new BaseKnifeItem(0.5F, -2.0F, Tiers.GOLD, new Item.Properties()));
    public static final RegistryObject<Item> KNIFE_OBSIDIAN = ITEMS.register("knife_obsidian",
            () -> new BaseKnifeItem(0.5F, -2.0F, BibliomaniaMaterialsRegistry.OBSIDIAN, new Item.Properties()));
    public static final RegistryObject<Item> KNIFE_NETHERITE = ITEMS.register("knife_netherite",
            () -> new BaseKnifeItem(0.5F, -2.0F, Tiers.NETHERITE, new Item.Properties()));*/


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
