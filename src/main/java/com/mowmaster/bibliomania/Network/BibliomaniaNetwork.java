package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Registers Bibliomania's network payloads (like MowLib's MowLibNetwork). The version must match between client and
// server: bump it when a payload is added or changed. Handlers run on the main thread by default.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class BibliomaniaNetwork
{
    private BibliomaniaNetwork() {}

    private static final String VERSION = "4"; // 2: added place_book; 3: added pickup_book; 4: block_info

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar(VERSION);
        // Client -> server: the storage book activation key
        registrar.playToServer(ToggleBookPayload.TYPE, ToggleBookPayload.STREAM_CODEC, ToggleBookPayload::handle);
        // Client -> server: the "place book" key
        registrar.playToServer(PlaceBookPayload.TYPE, PlaceBookPayload.STREAM_CODEC, PlaceBookPayload::handle);
        // Client -> server: the "pick up book" key
        registrar.playToServer(PickupBookPayload.TYPE, PickupBookPayload.STREAM_CODEC, PickupBookPayload::handle);
        // Reading Goggles' block label: client asks what a block holds, the server answers
        registrar.playToServer(BlockInfoPayloads.Request.TYPE, BlockInfoPayloads.Request.STREAM_CODEC, BlockInfoPayloads::handleRequest);
        registrar.playToClient(BlockInfoPayloads.Answer.TYPE, BlockInfoPayloads.Answer.STREAM_CODEC, BlockInfoPayloads::handleAnswer);
    }
}
