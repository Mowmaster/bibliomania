package com.mowmaster.bibliomania.Registry;

import com.mowmaster.bibliomania.Items.BaseFiberItem;
import com.mowmaster.bibliomania.Items.BaseThreadItem;
import com.mowmaster.bibliomania.Items.BaseWovenFiberItem;
import net.minecraft.world.item.Item;
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

    public static final RegistryObject<Item> THREAD_TWISTED_GRASS = ITEMS.register("thread_twisted_grass",
            () -> new BaseThreadItem(new Item.Properties(),0));
    public static final RegistryObject<Item> THREAD_SPUN_GRASS = ITEMS.register("thread_spun_grass",
            () -> new BaseThreadItem(new Item.Properties(),1));

    public static final RegistryObject<Item> WEAVE_GRASS = ITEMS.register("weave_grass",
            () -> new BaseWovenFiberItem(new Item.Properties(),0));
    public static final RegistryObject<Item> WOVEN_GRASS = ITEMS.register("woven_grass",
            () -> new BaseWovenFiberItem(new Item.Properties(),1));


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
