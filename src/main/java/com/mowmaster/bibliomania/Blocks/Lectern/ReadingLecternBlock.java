package com.mowmaster.bibliomania.Blocks.Lectern;

import com.mowmaster.bibliomania.Blocks.Library.LibraryCosts;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.network.chat.Component;
import com.mowmaster.mowlib.items.tools.FilterToolItem;
import java.util.Map;
import java.util.List;
import java.util.EnumMap;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mowmaster.mowlib.storage.StorageInteractions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// The Reading Lectern: the player's way into the Bookcases a MowLib work card (area or locations) points at.
// - Right-click with an area or locations card in the offhand: puts it in (swapping out any card it had). Sneak +
//   right-click with an empty hand: takes the card out.
// - Arrows on the top (or MowLib's page keys while looking at it): turn through every page of every book in the
//   linked cases (ReadingLecternBlockEntity). Sneak + an arrow turns the open book's own pages (a Ledger's trades).
// - Right-click with anything else: on a page of items it's stored in the linked cases' item books; with a whole book
//   open the lectern is that book (LecternActions), and only the book's own action happens (nothing if it can't use
//   the item, which stays in the hand). An empty hand on a whole book too (a Book of Experience stores your levels).
// - Left-click (LecternEvents): takes one of the open page's item (sneak: a stack), or harvests an open Almanac.
// - Dyes tint it, like the Bookcase (the first dye swaps to grey textures).
public class ReadingLecternBlock extends Block implements IColorableBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DYED = BooleanProperty.create("dyed");
    // Sealed (Librarian's Seal): it stays on its page, card catalogs don't move it, and the model has no arrows
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    // Base, post, and the slanted top as three steps rising away from the reader (facing north: low at the north), so
    // a click on the top lands about where the model's surface is. One shape for every facing: the steps are turned
    // to match in SHAPES.
    // Where the slanted desk starts (9 px up): clicks below it are on the pillar or base
    private static final double DESK_BOTTOM = 9.0 / 16.0;

    private static final List<CBPixelBox> BOXES = List.of(
            new CBPixelBox(3, 0, 3, 13, 2, 13),
            new CBPixelBox(5, 2, 5, 11, 11, 11),
            new CBPixelBox(1, 9, 1, 15, 10.5, 5),
            new CBPixelBox(1, 9, 5, 15, 12, 9),
            new CBPixelBox(1, 9, 9, 15, 13.7, 13));
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<Direction, VoxelShape>(Direction.class);
    static
    {
        for (Direction facing : Direction.Plane.HORIZONTAL)
        {
            int turns = CBPixelBox.quarterTurns(facing);
            SHAPES.put(facing, CBPixelBox.union(BOXES.stream().map(box -> box.rotateY(turns)).toList()));
        }
    }

    // The model's arrows sit at the low (reader's) end of the slanted top, near its corners (facing north): click
    // boxes over the lowest step's top, a little bigger so they're easy to hit
    private static final PageArrows ARROWS = new PageArrows(
            new CBPixelBox(1.5, 0, 0.5, 5.5, 16, 5.5),
            new CBPixelBox(10.5, 0, 0.5, 14.5, 16, 5.5),
            true);

    public ReadingLecternBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DYED, false).setValue(LOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, DYED, LOCKED);
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
        return SHAPES.get(state.getValue(FACING));
    }

    // Which arrow a click is on: -1 back, +1 forward, 0 neither (LecternEvents asks, to let sneak-clicks through)
    public static int arrowAt(BlockState state, BlockHitResult hit)
    {
        return ARROWS.arrowAt(state.getValue(FACING), hit);
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
        // A library out of knowledge (LibraryCosts): its lecterns can't be used
        if (LibraryCosts.blocked(level, lectern, pos, player)) return InteractionResult.SUCCESS;
        if (turnPage(state, level, pos, player, lectern, hitResult)) return InteractionResult.SUCCESS;

        // MowLib's Filter Tool: card catalog items matched fuzzy (at any damage) or exact. A setting: not while sealed.
        if (stack.getItem() instanceof FilterToolItem)
        {
            if (!level.isClientSide())
            {
                if (lectern.isLocked()) player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".library.locked"));
                else
                {
                    boolean exact = lectern.toggleExact();
                    player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".requestor." + (exact ? "match_exact" : "match_fuzzy")));
                    level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6f, exact ? 0.55f : 0.5f);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // Dyeing is a setting: not while locked (Librarian's Seal). With a whole book open the dye is the book's (a
        // Bestiary's sheep); sneak to dye the lectern then (LecternEvents lets that click through). showsWholeBook is
        // synced, so both sides agree.
        boolean bookTakesDye = lectern.showsWholeBook() && !player.isShiftKeyDown();
        InteractionResult dyed = lectern.isLocked() || bookTakesDye ? InteractionResult.TRY_WITH_EMPTY_HAND : tryDyeBlock(stack, state, level, pos, player, hitResult);
        if (dyed == InteractionResult.SUCCESS)
        {
            if (!level.isClientSide() && !state.getValue(DYED)) level.setBlock(pos, state.setValue(DYED, true), Block.UPDATE_ALL);
            return dyed;
        }

        if (insertCard(level, pos, player, lectern)) return InteractionResult.SUCCESS;

        // A whole book open: only the book uses it (if it can't, nothing happens and the item stays in the hand).
        // A page of items: it's stored. The client can't tell for sure (it doesn't know the cases), so it always says
        // SUCCESS and the server decides.
        if (level instanceof ServerLevel serverLevel)
        {
            // The pillar and base (below the desk) always store what's held, whatever's open on top
            boolean onDesk = hitResult.getLocation().y - pos.getY() >= DESK_BOTTOM;
            if (onDesk && lectern.typeSelection() != null) LecternActions.useType(serverLevel, player, hand, stack, lectern);
            else if (onDesk && lectern.isBookOpen(serverLevel)) LecternActions.use(serverLevel, player, hand, stack, lectern);
            else if (lectern.store(stack) > 0) level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 1f);
            else level.playSound(null, pos, SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.BLOCKS, 0.8f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    // Empty hand: the arrows, or sneak to take the card out
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof ReadingLecternBlockEntity lectern)) return InteractionResult.PASS;
        if (LibraryCosts.blocked(level, lectern, pos, player)) return InteractionResult.SUCCESS;
        if (turnPage(state, level, pos, player, lectern, hitResult)) return InteractionResult.SUCCESS;
        if (insertCard(level, pos, player, lectern)) return InteractionResult.SUCCESS;
        // A whole book open: an empty hand on it (a Book of Experience gives its levels...)
        // A library-wide type drawer open: an empty hand on it (experience is stored...)
        if (!player.isShiftKeyDown() && lectern.showsType())
        {
            if (level instanceof ServerLevel serverLevel) LecternActions.useType(serverLevel, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, lectern);
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown() && lectern.showsWholeBook() && !lectern.getShownBook().isEmpty())
        {
            if (level instanceof ServerLevel serverLevel) LecternActions.use(serverLevel, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, lectern);
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown() || !lectern.hasCard() || lectern.isLocked()) return InteractionResult.PASS;
        if (!level.isClientSide())
        {
            StorageInteractions.giveToPlayer(player, lectern.swapCard(ItemStack.EMPTY));
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    // An area or locations card in the offhand: in it goes (whatever the main hand holds), and any card the lectern had
    // comes back. Checked first on every right-click, so the main hand's item isn't stored instead.
    private static boolean insertCard(Level level, BlockPos pos, Player player, ReadingLecternBlockEntity lectern)
    {
        ItemStack offhand = player.getOffhandItem();
        if (!(offhand.getItem() instanceof IWorkCard card) || !WorkCardType.AREA_OR_LOCATIONS.accepts(card.getWorkCardType())) return false;
        // The card is a setting: not while locked (the click is still used, so the card isn't stored instead)
        if (lectern.isLocked()) return true;
        if (!level.isClientSide())
        {
            ItemStack old = lectern.swapCard(offhand.copyWithCount(1));
            offhand.consume(1, player);
            if (!old.isEmpty()) StorageInteractions.giveToPlayer(player, old);
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
        }
        return true;
    }

    // An arrow: the lectern's pages, or with sneak the open book's own (a Ledger's trades)
    private static boolean turnPage(BlockState state, Level level, BlockPos pos, Player player, ReadingLecternBlockEntity lectern, BlockHitResult hitResult)
    {
        // Sealed: no arrows (and the pages don't turn)
        if (state.getValue(LOCKED)) return false;
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
