package com.mowmaster.bibliomania.Blocks.VillagerBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.bibliomania.ServerConfig;
import com.mowmaster.mowlib.paging.PageArrows;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

// The Trader's Ledger placed on the ground: its villager stands on the pages facing you, its workstation in front,
// the trade on show over the workstation (VillagerBookRenderer).
// - The page arrows (or MowLib's page keys while looking at it) change the trade on show
// - Right-click with one of the trade's prices: makes the trade; both prices come out of your inventory, the item
//   bought drops on the book
// - Right-click with a workstation block: the villager takes that profession (back to level 1, new trades); one used
// - Sneak + right-click with an empty hand: picks the book up, villager and all
public class VillagerBookBlock extends BookBlock
{
    // The same arrows as the storage books (the open book's far corners)
    private static final PageArrows ARROWS = PageArrows.BOOK_CORNERS;

    public VillagerBookBlock(Properties properties)
    {
        super(properties, 1, 1);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VillagerBookBlockEntity(pos, state);
    }

    // Server ticks for restocking (see MobBookBlock#getTicker for how this works)
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != libBlockEntityRegistry.VILLAGER_BOOK.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((VillagerBookBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
    }

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

    // ---- Right-click ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof VillagerBookBlockEntity book)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (turnPage(state, level, pos, book, hitResult)) return InteractionResult.SUCCESS;
        VillagerContents contents = book.getContents();
        if (!contents.hasVillager()) return InteractionResult.TRY_WITH_EMPTY_HAND;

        // A workstation: new profession
        if (stack.getItem() instanceof BlockItem blockItem)
        {
            Optional<Holder<VillagerProfession>> profession = VillagerBookActions.professionFor(blockItem.getBlock().defaultBlockState());
            if (profession.isPresent())
            {
                if (level instanceof ServerLevel serverLevel)
                {
                    VillagerContents changed = VillagerBookActions.setProfession(serverLevel, contents, profession.get(), blockItem.getBlock());
                    if (changed != null)
                    {
                        book.setContents(changed);
                        stack.consume(1, player);
                        level.playSound(null, pos, SoundEvents.VILLAGER_WORK_LIBRARIAN, SoundSource.BLOCKS, 1f, 1f);
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        // One of the prices: trade
        if (VillagerBookActions.isPrice(contents, stack))
        {
            if (level instanceof ServerLevel serverLevel)
            {
                VillagerBookActions.Trade trade = VillagerBookActions.trade(serverLevel, player, contents, ServerConfig.villagerRestockInterval());
                if (trade == null)
                {
                    level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1f, 1f);
                }
                else
                {
                    book.setContents(trade.contents());
                    Block.popResource(serverLevel, pos, trade.result());
                    level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 1f, 1f);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof VillagerBookBlockEntity book)) return InteractionResult.PASS;
        if (turnPage(state, level, pos, book, hitResult)) return InteractionResult.SUCCESS;
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel)
        {
            ItemStack item = book.toItem();
            book.markPickedUp();
            serverLevel.removeBlock(pos, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            SoundType sound = state.getSoundType();
            level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    // A click on a page arrow turns to the next / previous trade (MowLib's PageArrows)
    private static boolean turnPage(BlockState state, Level level, BlockPos pos, VillagerBookBlockEntity book, BlockHitResult hitResult)
    {
        int turn = ARROWS.arrowAt(state.getValue(FACING), hitResult);
        if (turn == 0) return false;
        if (!level.isClientSide())
        {
            book.turnPage(turn);
            PageArrows.playTurnSound(level, pos);
        }
        return true;
    }

    // ---- No left-click actions, never broken by a player ----

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player)
    {
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack,
                                       boolean willHarvest, FluidState fluid)
    {
        return false;
    }
}
