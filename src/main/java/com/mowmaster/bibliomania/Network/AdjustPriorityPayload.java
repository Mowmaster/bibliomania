package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryPriority;
import com.mowmaster.bibliomania.Items.PriorityScrollItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server: "change the priority of the block at pos by delta" (the page keys while holding a Priority
// Scroll, ClientSide/PriorityKeys)
public record AdjustPriorityPayload(BlockPos pos, int delta) implements CustomPacketPayload
{
    public static final Type<AdjustPriorityPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "adjust_priority"));

    // composite: a codec for a record made of the codecs of its parts, in order, then the constructor
    public static final StreamCodec<ByteBuf, AdjustPriorityPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AdjustPriorityPayload::pos,
            ByteBufCodecs.VAR_INT, AdjustPriorityPayload::delta,
            AdjustPriorityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    // Runs on the server's main thread. Never trust the client: it must hold the scroll, be in reach, and the step is
    // at most 10 either way.
    public static void handle(AdjustPriorityPayload payload, IPayloadContext context)
    {
        Player player = context.player();
        if (!(player.level() instanceof ServerLevel level)) return;
        boolean holding = false;
        for (InteractionHand hand : InteractionHand.values()) holding |= player.getItemInHand(hand).getItem() instanceof PriorityScrollItem;
        if (!holding || !player.isWithinBlockInteractionRange(payload.pos(), 1.0) || !level.mayInteract(player, payload.pos())) return;
        LibraryPriority.adjust(level, payload.pos(), Mth.clamp(payload.delta(), -10, 10), player);
    }
}
