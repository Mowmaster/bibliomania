package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Registers our network payloads. RegisterPayloadHandlersEvent is a mod bus event; @EventBusSubscriber picks the
// right bus from the event type. Handlers run on the main thread by default.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public class NetworkRegistry
{
    // Bump this when a payload's format changes, so mismatched client and server versions refuse to connect
    private static final String VERSION = "1";

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(SelectBookPagePayload.TYPE, SelectBookPagePayload.STREAM_CODEC, SelectBookPagePayload::handle);
    }
}
