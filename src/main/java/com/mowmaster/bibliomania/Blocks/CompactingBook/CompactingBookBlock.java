package com.mowmaster.bibliomania.Blocks.CompactingBook;

import com.mowmaster.mowlib.filter.HeldFilters;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.Book.PickupableBook;
import com.mowmaster.mowlib.paging.PageArrows;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;

// The Compacting Book: one item family (gold nuggets, ingots and blocks) in one pool, up to 1024 of its top tier
// (CompactingContents). Placed, it works like the other storage books, with its tiers as pages:
// - the corner arrows (or the page keys) turn through the tiers; the open tier spins above the book
// - right-click with a tier of its family (or, empty, with anything that has a chain): stores it
// - left-click: takes 1 of the open tier, sneak + left-click a stack (made from the pool, whatever went in)
// - sneak + right-click with an empty hand: picks it up, contents and all
// - a MowLib filter in the offhand + right-click: the filter goes in; the Filter Tool in the offhand + left-click
//   takes it out. Once emptied, the book only starts chains the filter allows (CompactingFilter#allowsChain), so a
//   Bookcase can't fill it with some other family.
// It shares BookBlock's shape, thickness, waterlogging and placement; its block entity is its own.
public class CompactingBookBlock extends BookBlock
{
    private static final PageArrows ARROWS = PageArrows.BOOK_CORNERS;
    private static final int SHIFT_EXTRACT = 64;

    public CompactingBookBlock(Properties properties)
    {
        // BookBlock's page settings aren't used
        super(properties, 1, 1);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CompactingBookBlockEntity(pos, state);
    }

    // BookBlock's paged storage isn't used: these keep its own clicks from doing anything here
    @Override
    public boolean accepts(ItemResource resource)
    {
        return false;
    }

    @Override
    protected boolean allowsManualInsert()
    {
        return false;
    }

    // ---- Right-click: arrows, put in ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof CompactingBookBlockEntity book)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        int turn = ARROWS.arrowAt(state.getValue(FACING), hitResult);
        if (turn != 0)
        {
            if (!level.isClientSide())
            {
                book.turnPage(turn);
                PageArrows.playTurnSound(level, pos);
            }
            return InteractionResult.SUCCESS;
        }
        // A filter in the offhand goes into the book (CompactingFilter#allowsChain: which chains it may start). Main hand
        // only, so the offhand's own turn doesn't try to store the filter.
        if (hand == InteractionHand.MAIN_HAND && HeldFilters.isFilter(player.getOffhandItem()))
        {
            if (!level.isClientSide() && book.insertFilter(player.getOffhandItem()))
            {
                level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel)
        {
            int inserted = book.insert(stack);
            if (inserted > 0)
            {
                stack.consume(inserted, player); // creative players keep their stack
                level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Empty hand: sneak to pick it up
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel) PickupableBook.pickUp(serverLevel, pos, player);
        return InteractionResult.SUCCESS;
    }

    // ---- Left-click: take out (survival attack; creative breaks instantly, so onDestroyedByPlayer, as BookBlock) ----

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player)
    {
        if (level instanceof ServerLevel serverLevel) extract(serverLevel, pos, player);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack,
                                       boolean willHarvest, FluidState fluid)
    {
        if (player.isCreative() && level instanceof ServerLevel serverLevel) extract(serverLevel, pos, player);
        return false;
    }

    private void extract(ServerLevel level, BlockPos pos, Player player)
    {
        if (!(level.getBlockEntity(pos) instanceof CompactingBookBlockEntity book)) return;
        // MowLib's Filter Tool in the offhand: takes the filter out instead (or says there isn't one)
        if (StorageInteractions.removeFilter(player, book)) return;
        ItemStack taken = book.extractOpenTier(player.isShiftKeyDown() ? SHIFT_EXTRACT : 1);
        if (taken.isEmpty()) return;
        StorageInteractions.giveToPlayer(player, taken);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.3f, 1f);
    }
}
