package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server: "toggle activation on the storage book in this slot of my open container" (slot 0 or more), or
// "...in my main hand" (MAIN_HAND) / "...in my offhand" (OFF_HAND). Sent by ClientSide/BibliomaniaKeys' handler.
// A payload is a record holding the data, a Type (its id) and a StreamCodec (how to write and read it), like
// MowLib's SelectPagePayload.
public record ToggleBookPayload(int slot) implements CustomPacketPayload
{
    public static final int MAIN_HAND = -1;
    public static final int OFF_HAND = -2;

    public static final Type<ToggleBookPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "toggle_book"));

    // The slot as a variable-length int (small numbers take one byte)
    public static final StreamCodec<ByteBuf, ToggleBookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ToggleBookPayload::slot,
            ToggleBookPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    // Runs on the server's main thread. The client can send anything, so the slot is checked first;
    // StorageBookItem.toggle ignores anything that isn't a book with activation.
    public static void handle(ToggleBookPayload payload, IPayloadContext context)
    {
        Player player = context.player();
        ItemStack stack;
        if (payload.slot() == MAIN_HAND) stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        else if (payload.slot() == OFF_HAND) stack = player.getItemInHand(InteractionHand.OFF_HAND);
        else
        {
            AbstractContainerMenu menu = player.containerMenu;
            if (payload.slot() < 0 || payload.slot() >= menu.slots.size()) return;
            stack = menu.slots.get(payload.slot()).getItem();
        }
        if (stack.getCount() == 1) StorageBookItem.toggle(stack, player);
    }
}
