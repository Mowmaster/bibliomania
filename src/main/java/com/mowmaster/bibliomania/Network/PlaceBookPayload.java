package com.mowmaster.bibliomania.Network;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server: "place the book in my hand against this block face" (the "place book" key, ClientSide/BibliomaniaKeys).
// For any Bibliomania book (a BlockItem whose block is a BookBlock): it goes down as a block, skipping whatever the
// book's right-click does (placing a block from a Builder's Book, fluids...). Empty mob books still refuse
// (their place() says no).
// The hit position is sent as offsets inside the clicked block (0 to 1), which is all placing needs.
public record PlaceBookPayload(boolean offhand, BlockPos pos, Direction face, float x, float y, float z) implements CustomPacketPayload
{
    public static final Type<PlaceBookPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "place_book"));

    public static final StreamCodec<ByteBuf, PlaceBookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PlaceBookPayload::offhand,
            BlockPos.STREAM_CODEC, PlaceBookPayload::pos,
            Direction.STREAM_CODEC, PlaceBookPayload::face,
            ByteBufCodecs.FLOAT, PlaceBookPayload::x,
            ByteBufCodecs.FLOAT, PlaceBookPayload::y,
            ByteBufCodecs.FLOAT, PlaceBookPayload::z,
            PlaceBookPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    // Runs on the server's main thread. Checked first: a book in that hand, and a block within the player's reach.
    public static void handle(PlaceBookPayload payload, IPayloadContext context)
    {
        Player player = context.player();
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!isBook(stack) || !player.isWithinBlockInteractionRange(payload.pos(), 1.0)) return;
        Vec3 hit = Vec3.atLowerCornerOf(payload.pos()).add(clamp(payload.x()), clamp(payload.y()), clamp(payload.z()));
        BlockHitResult hitResult = new BlockHitResult(hit, payload.face(), payload.pos(), false);
        BlockItem item = (BlockItem) stack.getItem();
        // 26.3: a swing says how it's swung (the item's right-click animation); read before placing, as placing can
        // use the stack up
        var swing = stack.getInteractAnimation();
        // BlockItem#place: the normal placing of a block item (sound, block entity data, using up the item)
        if (item.place(new BlockPlaceContext(player.level(), player, hand, stack, hitResult)).consumesAction())
        {
            // true: send the swing to clients (the placing player's included, as the key press didn't swing locally)
            player.swing(hand, swing, true);
        }
    }

    public static boolean isBook(ItemStack stack)
    {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BookBlock;
    }

    private static double clamp(float offset)
    {
        return Math.clamp(offset, 0f, 1f);
    }
}
