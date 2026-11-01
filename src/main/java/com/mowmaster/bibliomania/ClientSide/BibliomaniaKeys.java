package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.platform.InputConstants;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.ActivatableBook;
import com.mowmaster.bibliomania.Blocks.BuilderBook.BuilderBookItem;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Network.PickupBookPayload;
import com.mowmaster.bibliomania.Network.PlaceBookPayload;
import com.mowmaster.bibliomania.Network.ToggleBookPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Bibliomania's keys (Options > Controls > Key Binds, under "Bibliomania"), and what they do. Client only.
// - Toggle book activation (default V): over a storage book in an inventory, toggles that book; in the world,
//   toggles the held one (main hand first). The server does the toggling (ToggleBookPayload).
// - Place book (default G): places the held book (any Bibliomania book) where you're looking, for books whose
//   right-click does something else (the Builder's Book places blocks, the Book of Fluids places fluids...)
// - Pick up book (default H): picks up the placed book you're looking at, contents and all (any Bibliomania book)
// Same pattern as MowLib's page keys (MowLibKeyMappings / PageInputHandler): since 1.21.9 a key's category is a
// KeyMapping.Category (an id) that must be registered too; since 26.3 key codes are SDL scancodes, so always the
// InputConstants.KEY_* constants.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID, value = Dist.CLIENT)
public final class BibliomaniaKeys
{
    private BibliomaniaKeys() {}

    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "bibliomania"));

    public static final KeyMapping TOGGLE_BOOK = new KeyMapping(ActivatableBook.TOGGLE_KEY, InputConstants.KEY_V, CATEGORY);

    // Place the held book as a block where you're looking (default G), for books whose right-click does something else
    public static final KeyMapping PLACE_BOOK = new KeyMapping(BuilderBookItem.PLACE_BOOK_KEY, InputConstants.KEY_G, CATEGORY);

    // Pick up the looked-at placed book, contents and all (default H)
    public static final KeyMapping PICKUP_BOOK = new KeyMapping("key." + BibliomaniaReferences.MODID + ".pickup_book", InputConstants.KEY_H, CATEGORY);

    // A mod bus event: @EventBusSubscriber sends it there by its type
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event)
    {
        event.registerCategory(CATEGORY);
        event.register(TOGGLE_BOOK);
        event.register(PLACE_BOOK);
        event.register(PICKUP_BOOK);
    }

    // ---- In inventories: the hovered book ----

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event)
    {
        // 26.x passes keys as a KeyEvent; InputConstants.getKey turns it into the Key a KeyMapping compares with
        if (!TOGGLE_BOOK.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()))) return;
        // <?>: any container screen, whatever its menu type
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Slot slot = screen.getHoveredSlot(); // public getter added by NeoForge
        if (slot == null || !isToggleable(slot.getItem())) return;
        ClientPacketDistributor.sendToServer(new ToggleBookPayload(slot.index));
        event.setCanceled(true); // the key did its job; nothing else should use it
    }

    // ---- In the world: the held book ----

    // Key presses outside screens are counted and read here once a tick (consumeClick is true once per press)
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        while (PLACE_BOOK.consumeClick()) placeHeldBook();
        while (PICKUP_BOOK.consumeClick()) pickUpLookedAtBook();
        while (TOGGLE_BOOK.consumeClick())
        {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;
            if (isToggleable(player.getItemInHand(InteractionHand.MAIN_HAND)))
            {
                ClientPacketDistributor.sendToServer(new ToggleBookPayload(ToggleBookPayload.MAIN_HAND));
            }
            else if (isToggleable(player.getItemInHand(InteractionHand.OFF_HAND)))
            {
                ClientPacketDistributor.sendToServer(new ToggleBookPayload(ToggleBookPayload.OFF_HAND));
            }
        }
    }

    // The "place book" key: if the crosshair is on a block and a hand holds a book, the server is asked to place it
    // there (the main hand first)
    private static void placeHeldBook()
    {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        boolean offhand;
        if (PlaceBookPayload.isBook(player.getItemInHand(InteractionHand.MAIN_HAND))) offhand = false;
        else if (PlaceBookPayload.isBook(player.getItemInHand(InteractionHand.OFF_HAND))) offhand = true;
        else return;
        BlockPos pos = hit.getBlockPos();
        Vec3 at = hit.getLocation();
        ClientPacketDistributor.sendToServer(new PlaceBookPayload(offhand, pos, hit.getDirection(),
                (float) (at.x - pos.getX()), (float) (at.y - pos.getY()), (float) (at.z - pos.getZ())));
    }

    // The "pick up book" key: if the crosshair is on a placed book, the server is asked to pick it up
    private static void pickUpLookedAtBook()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!(minecraft.level.getBlockState(hit.getBlockPos()).getBlock() instanceof BookBlock)) return;
        ClientPacketDistributor.sendToServer(new PickupBookPayload(hit.getBlockPos()));
    }

    // A storage book with an activation mode (Book of Fluids, Book of Experience)
    private static boolean isToggleable(ItemStack stack)
    {
        return ActivatableBook.canToggle(stack);
    }
}
