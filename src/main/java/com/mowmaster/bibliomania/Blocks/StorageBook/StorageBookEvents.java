package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.transfer.resource.Resource;

// Storage book events. @EventBusSubscriber puts each handler on the right bus by its event type
// (RegisterCapabilitiesEvent is a mod bus event, the interact events are game bus events).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class StorageBookEvents
{
    private StorageBookEvents() {}

    // Pipes, cables and hoppers from any mod can reach a placed book: for every transport type, its capability
    // (fluids, energy, mowlib:experience_handler...) is registered for the storage book block entity type, answered
    // by the book's storage when the book holds that type
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        for (TransportType<?> type : TransportTypes.REGISTRY) register(event, type);
    }

    // A generic method so the type's <R> lines up between the type and the storage
    private static <R extends Resource> void register(RegisterCapabilitiesEvent event, TransportType<R> type)
    {
        type.registerBlockCapability(event, libBlockEntityRegistry.STORAGE_BOOK.get(), (book, side) -> book.storageFor(type));
    }

    // Left-clicking a placed book (see MobBookActions#onLeftClickBlock for why this is an event): the behaviour
    // decides; if it handles the click, the click is cancelled on both sides so nothing is mined
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Level level = event.getLevel();
        Block block = level.getBlockState(event.getPos()).getBlock();
        if (!(block instanceof StorageBookBlock book)) return;
        if (!(level.getBlockEntity(event.getPos()) instanceof StorageBookBlockEntity blockEntity)) return;
        if (book.behaviour().leftClickPlaced(blockEntity, level, event.getPos(), event.getEntity())) event.setCanceled(true);
    }

    // A right-click with a storage book in the main hand: remembered for a tick, so the arm swing that follows
    // isn't taken for a left-click (ExperienceBookBehaviour#swingHeld)
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        rememberRightClick(event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        rememberRightClick(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        rememberRightClick(event.getEntity());
    }

    private static void rememberRightClick(Player player)
    {
        if (player.level().isClientSide() || !(player.getMainHandItem().getItem() instanceof StorageBookItem)) return;
        ExperienceBookBehaviour.LAST_RIGHT_CLICK.put(player.getUUID(), player.level().getGameTime());
    }
}
