package com.mowmaster.bibliomania.Blocks.Deposit;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.resource.Resource;

// Hoppers, copper golems, pipes and cables can put every transport type (items, fluids, energy, experience...) into
// a Storage Deposit, from every side: it goes into its library's books of that type. Nothing comes out.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class StorageDepositEvents
{
    private StorageDepositEvents() {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        for (TransportType<?> type : TransportTypes.REGISTRY) register(event, type);
    }

    private static <R extends Resource> void register(RegisterCapabilitiesEvent event, TransportType<R> type)
    {
        type.registerBlockCapability(event, libBlockEntityRegistry.STORAGE_DEPOSIT.get(), (deposit, side) -> deposit.inputHandler(type));
    }
}
