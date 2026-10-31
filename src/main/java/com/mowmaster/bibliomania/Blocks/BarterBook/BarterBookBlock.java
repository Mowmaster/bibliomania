package com.mowmaster.bibliomania.Blocks.BarterBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

// The Book of Barter placed on the ground: a piglin stands on the pages (holding gold while there's gold stored,
// BarterBookRenderer), and barters every few seconds while it has gold, the items dropping on the book.
// - Right-click with gold ingots or blocks: the whole stack goes in (as far as it fits). Gold can't be taken back.
// - Right-click with a nether star (barters sooner) or a heavy core (one more barter each time, for one more gold)
// - Sneak + right-click with an empty hand: picks the book up, gold and all
public class BarterBookBlock extends BookBlock
{
    public BarterBookBlock(Properties properties)
    {
        super(properties, 1, 1);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new BarterBookBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != libBlockEntityRegistry.BARTER_BOOK.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((BarterBookBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
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

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof BarterBookBlockEntity book)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        BarterContents contents = book.getContents();

        int fits = BarterActions.goldItemsThatFit(contents, stack);
        if (fits > 0)
        {
            if (level instanceof ServerLevel)
            {
                book.setContents(contents.withGold(contents.gold() + fits * BarterActions.goldValue(stack)));
                stack.consume(fits, player);
                level.playSound(null, pos, SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        }
        if (BarterActions.isUpgrade(stack))
        {
            if (level instanceof ServerLevel)
            {
                BarterContents upgraded = BarterActions.applyUpgrade(contents, stack);
                if (upgraded == null)
                {
                    level.playSound(null, pos, SoundEvents.PIGLIN_ANGRY, SoundSource.BLOCKS, 1f, 1f);
                }
                else
                {
                    book.setContents(upgraded);
                    stack.consume(1, player);
                    level.playSound(null, pos, SoundEvents.PIGLIN_CELEBRATE, SoundSource.BLOCKS, 1f, 1f);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof BarterBookBlockEntity book)
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

    // No left-click (gold can't be taken back), never broken by a player
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
