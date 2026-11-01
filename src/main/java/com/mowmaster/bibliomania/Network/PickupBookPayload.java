package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.PickupableBook;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server: "pick up the book at this position" (the "pick up book" key, ClientSide/BibliomaniaKeys): any
// placed Bibliomania book, contents and all, into an empty main hand or else the inventory (PickupableBook#pickUp).
public record PickupBookPayload(BlockPos pos) implements CustomPacketPayload
{
    public static final Type<PickupBookPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "pickup_book"));

    public static final StreamCodec<ByteBuf, PickupBookPayload> STREAM_CODEC = BlockPos.STREAM_CODEC.map(PickupBookPayload::new, PickupBookPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    // Runs on the server's main thread: the block must be within the player's reach (and the area theirs to change)
    public static void handle(PickupBookPayload payload, IPayloadContext context)
    {
        Player player = context.player();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!player.isWithinBlockInteractionRange(payload.pos(), 1.0) || !level.mayInteract(player, payload.pos())) return;
        if (PickupableBook.pickUp(level, payload.pos(), player)) player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), true);
    }
}
