package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.ReadingGlassesItem;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// The Reading Goggles' block label asks the server what a block holds: a client often doesn't know (most mods keep
// energy and tank amounts on the server only).
// - Request (client -> server): "what's in the block at pos?", sent twice a second while looking at it
// - Answer (server -> client): one line per stored thing, e.g. "Energy: 4000 / 10000", "Water: 2000mB / 16000"
// Every MowLib TransportType is asked for the block's handler (energy, fluids, experience, and any a mod adds), so
// this works for any mod's blocks that offer those capabilities. Items are left out (a chest would list every slot).
public final class BlockInfoPayloads
{
    private BlockInfoPayloads() {}

    // At most this many lines (a block with many tanks shows its first few)
    private static final int MAX_LINES = 6;

    public record Request(BlockPos pos) implements CustomPacketPayload
    {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "block_info_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = BlockPos.STREAM_CODEC.map(Request::new, Request::pos);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // ComponentSerialization.STREAM_CODEC sends text with its styles; .apply(ByteBufCodecs.list(n)) makes it a list
    // codec of at most n entries
    public record Answer(BlockPos pos, List<Component> lines) implements CustomPacketPayload
    {
        public static final Type<Answer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "block_info"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Answer::pos,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), Answer::lines,
                Answer::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // ---- Server ----

    // Only for players wearing Reading Goggles, about loaded blocks they could see (within 64 blocks)
    public static void handleRequest(Request request, IPayloadContext context)
    {
        Player player = context.player();
        if (!(player.level() instanceof ServerLevel level) || !ReadingGlassesItem.canRead(player)) return;
        BlockPos pos = request.pos();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getCenter()) > 64 * 64) return;
        context.reply(new Answer(pos, describe(level, pos)));
    }

    public static List<Component> describe(ServerLevel level, BlockPos pos)
    {
        List<Component> lines = new ArrayList<>();
        for (TransportType<?> type : TransportTypes.REGISTRY)
        {
            if (type == TransportTypes.ITEMS.get()) continue;
            describe(type, level, pos, lines);
            if (lines.size() >= MAX_LINES) break;
        }
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    // <R extends Resource>: a generic method, so the handler's resources and the type's describeAmount agree
    private static <R extends Resource> void describe(TransportType<R> type, ServerLevel level, BlockPos pos, List<Component> lines)
    {
        ResourceHandler<R> handler = type.getBlockHandler(level, pos, null);
        if (handler == null) return;
        for (int index = 0; index < handler.size() && lines.size() < MAX_LINES; index++)
        {
            R resource = handler.getResource(index);
            long amount = handler.getAmountAsLong(index);
            long capacity = handler.getCapacityAsLong(index, resource);
            if (capacity <= 0 && amount <= 0) continue;
            // "Water: 2000mB / 16000", or for an empty slot "Fluids: 0 / 16000"
            Component what = resource.isEmpty()
                    ? Component.empty().append(type.getName()).append(": 0")
                    : type.describeAmount(resource, (int) Math.min(Integer.MAX_VALUE, amount));
            lines.add(Component.empty().append(what).append(" / " + capacity).withStyle(type.getColor()));
        }
    }

    // ---- Client ----

    // The last answer, read by ClientSide/ReadingHud. Plain fields (no client classes here, so this class also loads
    // on dedicated servers). Handlers run on the main thread, so there's no threading to worry about.
    public static @Nullable BlockPos answeredPos;
    public static List<Component> answeredLines = List.of();

    public static void handleAnswer(Answer answer, IPayloadContext context)
    {
        answeredPos = answer.pos();
        answeredLines = answer.lines();
    }
}
