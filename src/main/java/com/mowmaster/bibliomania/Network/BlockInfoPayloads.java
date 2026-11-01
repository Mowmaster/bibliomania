package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.ReadingGlassesItem;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
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
// Mobs work the same way (EntityRequest / EntityAnswer, by entity id): how long until a baby grows up, or until an
// adult can breed again (the age counter is only kept on the server).
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

    public record EntityRequest(int id) implements CustomPacketPayload
    {
        public static final Type<EntityRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "entity_info_request"));
        public static final StreamCodec<ByteBuf, EntityRequest> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(EntityRequest::new, EntityRequest::id);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record EntityAnswer(int id, List<Component> lines) implements CustomPacketPayload
    {
        public static final Type<EntityAnswer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "entity_info"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EntityAnswer> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EntityAnswer::id,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), EntityAnswer::lines,
                EntityAnswer::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // ---- Server ----

    public static void handleEntityRequest(EntityRequest request, IPayloadContext context)
    {
        Player player = context.player();
        if (!(player.level() instanceof ServerLevel level) || !ReadingGlassesItem.canRead(player)) return;
        Entity entity = level.getEntity(request.id());
        if (entity == null || player.distanceToSqr(entity) > 64 * 64) return;
        context.reply(new EntityAnswer(request.id(), describe(entity)));
    }

    // Babies: "Grows up in: 12:30". Adults that bred: "Can breed again in: 4:10"; otherwise "In love" or "Ready to
    // breed" (animals only). Nothing for mobs that don't grow up.
    public static List<Component> describe(Entity entity)
    {
        List<Component> lines = new ArrayList<>();
        if (!(entity instanceof AgeableMob mob)) return lines;
        // getAge: negative while a baby (ticks left to grow up), positive after breeding (ticks left to wait), 0 ready
        int age = mob.getAge();
        if (age < 0) lines.add(hud("grows_up", time(-age)).withStyle(ChatFormatting.DARK_GREEN));
        else if (age > 0) lines.add(hud("breed_cooldown", time(age)).withStyle(ChatFormatting.GOLD));
        else if (mob instanceof Animal animal)
        {
            lines.add(animal.isInLove() ? hud("in_love").withStyle(ChatFormatting.LIGHT_PURPLE) : hud("can_breed").withStyle(ChatFormatting.DARK_GREEN));
        }
        return lines;
    }

    // Ticks as minutes:seconds
    private static String time(int ticks)
    {
        int seconds = (ticks + 19) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static MutableComponent hud(String key, Object... args)
    {
        return Component.translatable("hud." + BibliomaniaReferences.MODID + "." + key, args);
    }

    // Only for players wearing Reading Goggles, about loaded blocks they could see (within 64 blocks)
    public static void handleRequest(Request request, IPayloadContext context)
    {
        Player player = context.player();
        if (!(player.level() instanceof ServerLevel level) || !ReadingGlassesItem.canRead(player)) return;
        BlockPos pos = request.pos();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return;
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

    public static int answeredEntity = -1;
    public static List<Component> answeredEntityLines = List.of();

    public static void handleEntityAnswer(EntityAnswer answer, IPayloadContext context)
    {
        answeredEntity = answer.id();
        answeredEntityLines = answer.lines();
    }
}
