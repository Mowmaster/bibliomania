package com.mowmaster.bibliomania.Atlas;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.mowlib.paging.IPageable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

// The Atlas: a book of maps. While one is anywhere in your inventory, a map of the ground round you shows in the top
// right of the screen (ClientSide/AtlasHud), uncovered only where you've been (32 blocks round you as you walk:
// AtlasExplorer), with the Atlas's markers, and the nearest lodestone marker tracked under it.
// - The page keys (or scrolling over it) zoom: 1, 2, 4 or 8 blocks per map pixel (it's MowLib IPageable)
// - Sneak + right-click a block, or the activation key on one: marks it as a point of interest (its item as the icon);
//   again to unmark
// - Right-click a banner: marks it (its colour and name), like a vanilla map; a lodestone: marks it with an X
// Each Atlas keeps its own explored map (by its bibliomania:book_id, given the first time it's carried) and markers.
// It stands on Bookcases and Chiseled Bookshelves like a book, but holds no storage.
public class AtlasItem extends Item implements IPageable
{
    public AtlasItem(Properties properties)
    {
        super(properties);
    }

    public static AtlasData data(ItemStack stack)
    {
        AtlasData data = stack.get(DataComponentRegistry.ATLAS.get());
        return data == null ? AtlasData.EMPTY : data;
    }

    // The Atlas's id (server: given one if it has none yet)
    public static UUID id(ItemStack atlas)
    {
        UUID id = atlas.get(DataComponentRegistry.BOOK_ID.get());
        if (id == null)
        {
            id = UUID.randomUUID();
            atlas.set(DataComponentRegistry.BOOK_ID.get(), id);
        }
        return id;
    }

    // The first Atlas in a player's inventory (EMPTY if none): the one the map shows
    public static ItemStack carried(Player player)
    {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof AtlasItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    // Whether the player carries the Atlas with this id
    public static boolean carries(Player player, UUID id)
    {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof AtlasItem && id.equals(stack.get(DataComponentRegistry.BOOK_ID.get()))) return true;
        }
        return false;
    }

    // ---- Marking ----

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        boolean sneaking = player != null && player.isShiftKeyDown();
        boolean banner = state.getBlock() instanceof AbstractBannerBlock;
        boolean lodestone = state.is(Blocks.LODESTONE);
        if (!sneaking && !banner && !lodestone) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel)
        {
            AtlasData.Marker marker = sneaking ? point(serverLevel, pos, state) : banner ? banner(serverLevel, pos, state) : lodestone(serverLevel, pos);
            toggle(serverLevel, context.getItemInHand(), marker, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    // The activation key on a block while holding an Atlas (the main hand first): marks it as a point of interest
    public static void markHeld(ServerLevel level, Player player, BlockPos pos)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack held = player.getItemInHand(hand);
            if (!(held.getItem() instanceof AtlasItem)) continue;
            toggle(level, held, point(level, pos, level.getBlockState(pos)), pos, player);
            return;
        }
    }

    private static void toggle(ServerLevel level, ItemStack atlas, AtlasData.Marker marker, BlockPos pos, Player player)
    {
        AtlasData before = data(atlas);
        AtlasData after = before.toggled(marker);
        atlas.set(DataComponentRegistry.ATLAS.get(), after);
        boolean added = after.markers().size() > before.markers().size();
        if (player != null)
        {
            player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".atlas." + (added ? "marked" : "unmarked")));
        }
        level.playSound(null, pos, added ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 1f, 1.1f);
    }

    // A point of interest: the block's item as its icon (or a map if it has none, like fire)
    private static AtlasData.Marker point(ServerLevel level, BlockPos pos, BlockState state)
    {
        Item icon = state.getBlock().asItem();
        if (icon == Items.AIR) icon = Items.MAP;
        return new AtlasData.Marker(AtlasData.Kind.POINT, level.dimension(), pos.immutable(), icon, 0, Optional.empty());
    }

    // A banner: its colour, and its name if it has one (as a vanilla map shows)
    private static AtlasData.Marker banner(ServerLevel level, BlockPos pos, BlockState state)
    {
        int color = ((AbstractBannerBlock) state.getBlock()).getColor().getTextureDiffuseColor();
        Optional<Component> name = level.getBlockEntity(pos) instanceof BannerBlockEntity banner
                ? Optional.ofNullable(banner.getCustomName()) : Optional.empty();
        return new AtlasData.Marker(AtlasData.Kind.BANNER, level.dimension(), pos.immutable(), state.getBlock().asItem(), color, name);
    }

    private static AtlasData.Marker lodestone(ServerLevel level, BlockPos pos)
    {
        return new AtlasData.Marker(AtlasData.Kind.LODESTONE, level.dimension(), pos.immutable(), Items.LODESTONE, 0xFF3030, Optional.empty());
    }

    // ---- Zoom: the pages (MowLib IPageable) ----

    @Override
    public int getPageCount(ItemStack stack)
    {
        return AtlasData.ZOOM_LEVELS;
    }

    @Override
    public int getPage(ItemStack stack)
    {
        return data(stack).zoom();
    }

    @Override
    public void setPage(ItemStack stack, int page)
    {
        stack.set(DataComponentRegistry.ATLAS.get(), data(stack).withZoom(page));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        AtlasData data = data(stack);
        String key = "tooltip." + BibliomaniaReferences.MODID + ".atlas.";
        tooltip.accept(Component.translatable(key + "zoom", data.scale()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(key + "markers", data.markers().size()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(key + "use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
