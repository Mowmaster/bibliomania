package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.Blocks.Library.LibraryCosts;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Deposit.StorageAccessPointBlockEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
// Chiseled bookshelves: the request says which of the six slots is looked at (worked out on the client, the same way
// vanilla does), and the answer starts with that book's tooltip (name, enchantments...): clients don't know which
// books a shelf holds, only which slots are full.
// Mobs work the same way (EntityRequest / EntityAnswer, by entity id): their effects (with level and time left), and
// how long until a baby grows up, or until an adult can breed again (both only kept on the server).
public final class BlockInfoPayloads
{
    private BlockInfoPayloads() {}

    // At most this many lines (a block with many tanks shows its first few)
    private static final int MAX_LINES = 16;

    // slot: the chiseled bookshelf slot looked at (0-5), or -1
    public record Request(BlockPos pos, int slot) implements CustomPacketPayload
    {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "block_info_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Request::pos,
                ByteBufCodecs.VAR_INT, Request::slot,
                Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // ComponentSerialization.STREAM_CODEC sends text with its styles; .apply(ByteBufCodecs.list(n)) makes it a list
    // codec of at most n entries
    public record Answer(BlockPos pos, int slot, List<Component> lines) implements CustomPacketPayload
    {
        public static final Type<Answer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "block_info"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Answer::pos,
                ByteBufCodecs.VAR_INT, Answer::slot,
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
        // Effects first: "Speed II 1:30" in blue (good) or red (bad), like a potion's tooltip
        if (entity instanceof LivingEntity living)
        {
            for (MobEffectInstance instance : living.getActiveEffects())
            {
                if (lines.size() >= MAX_LINES - 1) break;
                lines.add(effect(instance));
            }
        }
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

    private static Component effect(MobEffectInstance instance)
    {
        Holder<MobEffect> effect = instance.getEffect();
        MutableComponent line = Component.translatable(effect.value().getDescriptionId());
        // Amplifier 0 is level I: "enchantment.level.2" is vanilla's "II"
        if (instance.getAmplifier() > 0) line.append(" ").append(Component.translatable("enchantment.level." + (instance.getAmplifier() + 1)));
        line.append(" " + (instance.isInfiniteDuration() ? "\u221E" : time(instance.getDuration())));
        // getTooltipFormatting: blue for beneficial, red for harmful, like potion tooltips
        return line.withStyle(effect.value().getCategory().getTooltipFormatting());
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
        context.reply(new Answer(pos, request.slot(), describe(level, pos, request.slot(), player)));
    }

    public static List<Component> describe(ServerLevel level, BlockPos pos, int slot, Player player)
    {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        // Library blocks with a left-side panel (ReadingHud): their own lines
        if (blockEntity instanceof StorageAccessPointBlockEntity point) return libraryTotals(point.library(level, pos));
        if (blockEntity instanceof LibraryControllerBlockEntity controller) return libraryMembers(level, controller);
        List<Component> lines = new ArrayList<>();
        // The looked-at book on a chiseled bookshelf: its tooltip (name, then enchantments, author...)
        if (slot >= 0 && slot < 6 && level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)
        {
            ItemStack book = shelf.getItem(slot);
            if (!book.isEmpty())
            {
                for (Component line : book.getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL))
                {
                    if (lines.size() >= MAX_LINES) break;
                    lines.add(line);
                }
            }
        }
        for (TransportType<?> type : TransportTypes.REGISTRY)
        {
            if (type == TransportTypes.ITEMS.get()) continue;
            describeType(type, level, pos, lines);
            if (lines.size() >= MAX_LINES) break;
        }
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    // A Storage Access Point or Library Controller: its library's storage, per transport type with everything of it
    // together: "Items: 12,345 / 40,960" (typeTotals: what the type's books hold and have room for), and under it
    // "  12 / 40 slots free, 69% space left"
    private static List<Component> libraryTotals(@Nullable LibraryControllerBlockEntity library)
    {
        List<Component> lines = new ArrayList<>();
        if (library == null)
        {
            lines.add(hud("no_library"));
            return lines;
        }
        for (TransportType<?> type : TransportTypes.REGISTRY)
        {
            long[] totals = library.typeTotals(type, null);
            if (totals[1] <= 0 && totals[0] <= 0) continue;
            lines.add(hud("library_type_total", type.getName(), String.format("%,d", totals[0]), String.format("%,d", totals[1])).withStyle(type.getColor()));
            // Under it: free slots (room for that many more kinds) and how much room is left
            int left = totals[1] <= 0 ? 0 : (int) Math.floor(100.0 * Math.max(0, totals[1] - totals[0]) / totals[1]);
            lines.add(hud("library_type_free", String.format("%,d", totals[3]), String.format("%,d", totals[2]), left).withStyle(ChatFormatting.GRAY));
            if (lines.size() >= MAX_LINES) break;
        }
        if (lines.isEmpty()) lines.add(hud("library_no_storage"));
        return lines;
    }

    // A Library Controller: its Knowledge Points (with costs on), its storage (libraryTotals), then every kind of block
    // in the library with how many: "12 Bookcase", most first
    private static List<Component> libraryMembers(ServerLevel level, LibraryControllerBlockEntity controller)
    {
        // LinkedHashMap: keeps the order kinds were first met; merge adds one to a kind already counted
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (BlockPos member : controller.members())
        {
            if (level.isLoaded(member)) counts.merge(level.getBlockState(member).getBlock(), 1, Integer::sum);
        }
        List<Component> lines = new ArrayList<>();
        // With costs on (LibraryCosts): the Knowledge Point balance, or that it's run out
        if (LibraryCosts.enabled())
        {
            lines.add(controller.isFueled() ? hud("library_knowledge", String.format("%,d", controller.getKnowledge())).withStyle(ChatFormatting.AQUA)
                    : hud("library_no_knowledge").withStyle(ChatFormatting.RED));
        }
        // The storage (as an Access Point shows it: totals, free slots, space left), then the blocks
        List<Component> storage = libraryTotals(controller);
        lines.addAll(storage);
        lines.add(hud("library_members", controller.members().size()));
        counts.entrySet().stream()
                .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                .forEach(entry -> lines.add(hud("library_member", entry.getValue(), entry.getKey().getName())));
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    // <R extends Resource>: a generic method, so the handler's resources and the type's describeAmount agree
    private static <R extends Resource> void describeType(TransportType<R> type, ServerLevel level, BlockPos pos, List<Component> lines)
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
    public static int answeredSlot = -1;
    public static List<Component> answeredLines = List.of();

    public static void handleAnswer(Answer answer, IPayloadContext context)
    {
        answeredPos = answer.pos();
        answeredSlot = answer.slot();
        answeredLines = answer.lines();
    }

    // ---- Chiseled bookshelf slots (used on the client) ----

    // Which of a chiseled bookshelf's six slots the crosshair is on (0-2 the top row from the left, 3-5 the bottom), or
    // -1 (not a chiseled bookshelf, or not its front). The same sums as vanilla's ChiseledBookShelfBlock.
    public static int bookshelfSlot(BlockState state, BlockHitResult hit)
    {
        if (!(state.getBlock() instanceof ChiseledBookShelfBlock) || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return -1;
        Direction face = hit.getDirection();
        if (face != state.getValue(BlockStateProperties.HORIZONTAL_FACING)) return -1;
        // Where on the front face it was hit, 0-1 across (from the left, seen from the front) and 0-1 up
        BlockPos pos = hit.getBlockPos();
        Vec3 at = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double across = switch (face)
        {
            case NORTH -> 1.0 - at.x();
            case SOUTH -> at.x();
            case WEST -> at.z();
            case EAST -> 1.0 - at.z();
            default -> -1;
        };
        if (across < 0) return -1;
        int column = across < 0.375 ? 0 : across < 0.6875 ? 1 : 2;
        int row = at.y() >= 0.5 ? 0 : 1;
        return column + row * 3;
    }

    public static int answeredEntity = -1;
    public static List<Component> answeredEntityLines = List.of();

    public static void handleEntityAnswer(EntityAnswer answer, IPayloadContext context)
    {
        answeredEntity = answer.id();
        answeredEntityLines = answer.lines();
    }
}
