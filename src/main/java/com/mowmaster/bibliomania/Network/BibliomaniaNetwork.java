package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.Atlas.AtlasPayloads;
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

    private static final String VERSION = "9"; // ... 7: atlas; 8: atlas layers; 9: keys and the reading HUD moved to MowLib

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar(VERSION);
        // The Atlas: tiles to the client, requests and marks from it
        registrar.playToClient(AtlasPayloads.Tiles.TYPE, AtlasPayloads.Tiles.STREAM_CODEC, AtlasPayloads::handleTiles);
        registrar.playToServer(AtlasPayloads.Request.TYPE, AtlasPayloads.Request.STREAM_CODEC, AtlasPayloads::handleRequest);
    }
}
