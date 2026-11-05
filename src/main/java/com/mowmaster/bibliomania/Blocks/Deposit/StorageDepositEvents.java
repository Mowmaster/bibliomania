package com.mowmaster.bibliomania.Blocks.Deposit;

import com.mowmaster.mowlib.transport.EnergyTransportType;
import com.mowmaster.bibliomania.Blocks.Library.LibraryDeposit;
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
// A Storage Access Point is the same, but things can be taken out too.
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
        // Energy: the library's energy books as one pool (LibraryDeposit.EnergyPool), since the energy capability
        // only ever looks at one slot
        if (type == TransportTypes.ENERGY.get())
        {
            EnergyTransportType energy = TransportTypes.ENERGY.get();
            energy.registerBlockCapability(event, libBlockEntityRegistry.STORAGE_DEPOSIT.get(), (deposit, side) -> new LibraryDeposit.EnergyPool(deposit.inputHandler(energy)));
            energy.registerBlockCapability(event, libBlockEntityRegistry.STORAGE_ACCESS_POINT.get(), (point, side) -> new LibraryDeposit.EnergyPool(point.accessHandler(energy)));
            return;
        }
        type.registerBlockCapability(event, libBlockEntityRegistry.STORAGE_DEPOSIT.get(), (deposit, side) -> deposit.inputHandler(type));
        // The Storage Access Point: in and out
        type.registerBlockCapability(event, libBlockEntityRegistry.STORAGE_ACCESS_POINT.get(), (point, side) -> point.accessHandler(type));
    }
}
