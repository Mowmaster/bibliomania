package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemRegistry {

    public static final DeferredRegister.Items CBITEMS = DeferredRegister.createItems(BibliomaniaReferences.MODID);


    public static void register(IEventBus eventBus)
    {
        CBITEMS.register(eventBus);
    }
}
