package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server: "the book in this slot of my open container is now open at this page" (sent when the player
// scrolls over a book). Vanilla bundles send ServerboundSelectBundleItemPacket the same way; that packet only
// knows about bundles, so books need their own.
// A payload is a record holding the data, a Type (its id) and a StreamCodec (how to write and read it).
public record SelectBookPagePayload(int slot, int page) implements CustomPacketPayload
{
    public static final Type<SelectBookPagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "select_book_page"));

    // Writes slot then page as variable-length ints, and reads them back in the same order
    public static final StreamCodec<ByteBuf, SelectBookPagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectBookPagePayload::slot,
            ByteBufCodecs.VAR_INT, SelectBookPagePayload::page,
            SelectBookPagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    // Runs on the server's main thread. The client can send anything, so check the slot and item first.
    public static void handle(SelectBookPagePayload payload, IPayloadContext context)
    {
        Player player = context.player();
        AbstractContainerMenu menu = player.containerMenu;
        if (payload.slot() < 0 || payload.slot() >= menu.slots.size()) return;

        ItemStack stack = menu.slots.get(payload.slot()).getItem();
        if (stack.getItem() instanceof BookItem book) book.setPage(stack, payload.page());
    }
}
