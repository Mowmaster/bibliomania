package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BiblioReferences;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemRegistry {

    public static final DeferredRegister.Items CBITEMS = DeferredRegister.createItems(BiblioReferences.MODID);


    public static void register(IEventBus eventBus)
    {
        CBITEMS.register(eventBus);
    }
}
