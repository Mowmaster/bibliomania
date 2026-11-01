package com.mowmaster.bibliomania.Blocks.Bookcase;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.resource.Resource;

// Hoppers, copper golems, pipes and cables reach a Bookcase's books: every transport type's capability (items,
// fluids, energy, mowlib:experience_handler...) is registered for the bookcase, answered by its books of that type
// together (BookcaseBlockEntity#handler), or not at all when it has none. The same from every side.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class BookcaseEvents
{
    private BookcaseEvents() {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        for (TransportType<?> type : TransportTypes.REGISTRY) register(event, type);
    }

    // A generic method so the type's <R> lines up between the type and the handler
    private static <R extends Resource> void register(RegisterCapabilitiesEvent event, TransportType<R> type)
    {
        type.registerBlockCapability(event, libBlockEntityRegistry.BOOKCASE.get(), (shelf, side) -> shelf.handler(type));
    }
}
