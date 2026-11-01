package com.mowmaster.bibliomania.Blocks.Lectern;

import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.paging.PageArrows;
import com.mowmaster.mowlib.tint.IColorableBlock;
import com.mowmaster.mowlib.util.CBPixelBox;
import com.mowmaster.mowlib.workcard.IWorkCard;
import com.mowmaster.mowlib.workcard.WorkCardType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mowmaster.mowlib.storage.StorageInteractions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// The Reading Lectern: the player's way into the Bookcases a MowLib work card (area or locations) points at.
// - Right-click with an area or locations card: puts it in (swapping out any card it had). Sneak + right-click with
//   an empty hand: takes the card out.
// - Arrows on the top (or MowLib's page keys while looking at it): turn through every page of every book in the
//   linked cases (ReadingLecternBlockEntity). Sneak + an arrow turns the open book's own pages (a Ledger's trades).
// - Right-click with anything else: the open book uses it if it's a book the lectern stands in for (LecternActions:
//   Almanac, Ledger, Books of Fluids and Energy); otherwise it's stored in the linked cases' item books.
// - Left-click (LecternEvents): takes one of the open page's item (sneak: a stack), or harvests an open Almanac.
// - Dyes tint it, like the Bookcase (the first dye swaps to grey textures).
public class ReadingLecternBlock extends Block implements IColorableBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DYED = BooleanProperty.create("dyed");

    // Base, post and top (the slanted top's box: the arrows are clicked on its upper face)
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(5, 2, 5, 11, 11, 11),
            Block.box(1, 9, 1, 15, 14, 15));

    // The model's arrows sit near the high back corners of the slanted top (facing north): click boxes on the top
    // face over them, a little bigger so they're easy to hit
    private static final PageArrows ARROWS = new PageArrows(
            new CBPixelBox(1.5, 0, 8, 5.5, 16, 14),
            new CBPixelBox(10.5, 0, 8, 14.5, 16, 14),
            true);

    public ReadingLecternBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DYED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, DYED);
    }

    // The low edge (the reader's side) towards the player
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(DYED, context.getItemInHand().has(DataComponents.DYED_COLOR));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ReadingLecternBlockEntity(pos, state);
    }

    @Override
    public boolean usesTintBlockEntity()
    {
        return false;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != libBlockEntityRegistry.READING_LECTERN.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((ReadingLecternBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
    }

    // ---- Right-click ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof ReadingLecternBlockEntity lectern)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (turnPage(state, level, pos, player, lectern, hitResult)) return InteractionResult.SUCCESS;

        InteractionResult dyed = tryDyeBlock(stack, state, level, pos, player, hitResult);
        if (dyed == InteractionResult.SUCCESS)
        {
            if (!level.isClientSide() && !state.getValue(DYED)) level.setBlock(pos, state.setValue(DYED, true), Block.UPDATE_ALL);
            return dyed;
        }

        // An area or locations card: in it goes, and any card it had comes back
        if (stack.getItem() instanceof IWorkCard card && WorkCardType.AREA_OR_LOCATIONS.accepts(card.getWorkCardType()))
        {
            if (!level.isClientSide())
            {
                ItemStack old = lectern.swapCard(stack.copyWithCount(1));
                stack.consume(1, player);
                if (!old.isEmpty()) StorageInteractions.giveToPlayer(player, old);
                level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        }

        // The open book uses it, or else it's stored. The client can't tell which (it doesn't know the cases), so it
        // always says SUCCESS and the server decides.
        if (level instanceof ServerLevel serverLevel && !LecternActions.use(serverLevel, player, hand, stack, lectern))
        {
            if (lectern.store(stack) > 0) level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 1f);
            else level.playSound(null, pos, SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.BLOCKS, 0.8f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    // Empty hand: the arrows, or sneak to take the card out
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof ReadingLecternBlockEntity lectern)) return InteractionResult.PASS;
        if (turnPage(state, level, pos, player, lectern, hitResult)) return InteractionResult.SUCCESS;
        if (!player.isShiftKeyDown() || !lectern.hasCard()) return InteractionResult.PASS;
        if (!level.isClientSide())
        {
            StorageInteractions.giveToPlayer(player, lectern.swapCard(ItemStack.EMPTY));
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    // An arrow: the lectern's pages, or with sneak the open book's own (a Ledger's trades)
    private static boolean turnPage(BlockState state, Level level, BlockPos pos, Player player, ReadingLecternBlockEntity lectern, BlockHitResult hitResult)
    {
        int turn = ARROWS.arrowAt(state.getValue(FACING), hitResult);
        if (turn == 0) return false;
        if (level instanceof ServerLevel serverLevel)
        {
            if (!player.isShiftKeyDown() || !LecternActions.turnBookPage(serverLevel, turn, lectern)) lectern.turnPage(turn);
            PageArrows.playTurnSound(level, pos);
        }
        return true;
    }
}
