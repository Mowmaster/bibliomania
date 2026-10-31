package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.mowlib.items.CBColorPickerItem;
import com.mowmaster.mowlib.paging.PageArrows;
import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.tint.IColorableBlock;
import com.mowmaster.mowlib.tint.IDyeSource;
import com.mowmaster.mowlib.util.CBPixelBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// A book lying open on the ground that stores items, one kind per page.
// - Right-click the arrow in a top corner: turn the page back (left arrow) or forward (right arrow)
// - Right-click with an item: stores as much as fits on the open page (MowLib's Color Palette dyes a dyeable cover
//   instead, and its colour picker reads the cover colour)
// - Left-click: takes 1 from the open page; sneak + left-click: a full stack
// - Sneak + right-click with an empty hand: picks the book up, contents and all
// Mining never breaks a book (it only takes items out); anything else that removes it drops it with its contents.
// Subclasses: TintableBookBlock (wool, leather: dyeable cover) and DeathBookBlock.
public class BookBlock extends Block implements EntityBlock, SimpleWaterloggedBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    // How full the book looks, 0-5 (more pages stacked up)
    public static final IntegerProperty THICKNESS = IntegerProperty.create("thickness", 0, 5);

    // Fullness needed for each thickness above 0 (same steps as 1.20)
    private static final float[] THICKNESS_STEPS = {0.1f, 0.2f, 0.4f, 0.6f, 0.8f};

    // The page-turn arrows in the open book's far corners (MowLib's PageArrows: back and forward boxes as modelled
    // facing north, top face only)
    private static final PageArrows ARROWS = PageArrows.BOOK_CORNERS;

    private static final int SHIFT_EXTRACT = 64;

    private final int pageCapacity;
    private final int defaultPages;
    // One shape per facing per thickness, built once
    private final Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);

    // pageCapacity = items per page; defaultPages = pages when the item doesn't say (recipes set more)
    public BookBlock(Properties properties, int pageCapacity, int defaultPages)
    {
        super(properties);
        this.pageCapacity = pageCapacity;
        this.defaultPages = defaultPages;
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false)
                .setValue(THICKNESS, 0));

        for (Direction facing : Direction.Plane.HORIZONTAL)
        {
            VoxelShape[] byThickness = new VoxelShape[6];
            for (int thickness = 0; thickness < 6; thickness++)
            {
                int turns = CBPixelBox.quarterTurns(facing);
                byThickness[thickness] = CBPixelBox.union(northBoxes(thickness).stream().map(box -> box.rotateY(turns)).toList());
            }
            shapes.put(facing, byThickness);
        }
    }

    // The covers and the stack of pages, as modelled facing north. The covers stick out past the block's sides.
    private static List<CBPixelBox> northBoxes(int thickness)
    {
        double coverTop = thickness == 0 ? 0.25 : 1;
        double pagesTop = thickness == 0 ? 0.5 : (thickness == 1 ? 1.5 : thickness);
        return List.of(
                new CBPixelBox(-6.25, 0, 1, 21.25, coverTop, 15),
                new CBPixelBox(-3.25, coverTop, 1.25, 18.25, pagesTop, 14.75));
    }

    // ---- What it stores ----

    public int pageCapacity()
    {
        return pageCapacity;
    }

    public int defaultPages()
    {
        return defaultPages;
    }

    // Anything except other books
    public boolean accepts(ItemResource resource)
    {
        return !(resource.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BookBlock);
    }

    // Whether players can put items in by hand (the death book is take-out only)
    protected boolean allowsManualInsert()
    {
        return true;
    }

    // Number of dyeable regions; 0 here, TintableBookBlock has 1 (the cover)
    public int getTintRegionCount()
    {
        return 0;
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new BookBlockEntity(pos, state);
    }

    // ---- States ----

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, WATERLOGGED, THICKNESS);
    }

    // Faces the way the player is looking (as in 1.20), so the arrows are at the far corners from them
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(WATERLOGGED, water);
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return shapes.get(state.getValue(FACING))[state.getValue(THICKNESS)];
    }

    @Override
    protected @NotNull BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
                                              BlockPos pos, Direction direction, BlockPos neighborPos,
                                              BlockState neighborState, RandomSource random)
    {
        if (state.getValue(WATERLOGGED))
        {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected @NotNull FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected @NotNull BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // Called by the block entity whenever the contents change
    public void updateThickness(Level level, BlockPos pos, BlockState state, float fullness)
    {
        int thickness = 0;
        while (thickness < THICKNESS_STEPS.length && fullness >= THICKNESS_STEPS[thickness]) thickness++;
        if (state.getValue(THICKNESS) != thickness)
        {
            level.setBlock(pos, state.setValue(THICKNESS, thickness), Block.UPDATE_ALL);
        }
    }

    // ---- Right-click: turn pages, put items in ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof BookBlockEntity book)) return InteractionResult.TRY_WITH_EMPTY_HAND;

        // The arrows work whatever is in your hand
        int turn = ARROWS.arrowAt(state.getValue(FACING), hitResult);
        if (turn != 0)
        {
            if (!level.isClientSide())
            {
                book.turnPage(turn);
                PageArrows.playTurnSound(level, pos);
            }
            // SUCCESS on both sides so the client swings the arm
            return InteractionResult.SUCCESS;
        }

        // MowLib's colour tools work on the cover instead of being stored:
        // - the colour picker reads the cover colour: PASS lets the picker's own right-click (useOn) run
        // - the Color Palette (any IDyeSource) dyes a dyeable cover; tryDyeBlock mixes like leather armour
        if (stack.getItem() instanceof CBColorPickerItem) return InteractionResult.PASS;
        if (stack.getItem() instanceof IDyeSource && this instanceof IColorableBlock colorable)
        {
            return colorable.tryDyeBlock(stack, state, level, pos, player, hitResult);
        }

        // Empty hand, or nothing to store: fall through to useWithoutItem (pickup), or let the item act normally
        if (stack.isEmpty() || !allowsManualInsert() || !accepts(ItemResource.of(stack))) return InteractionResult.TRY_WITH_EMPTY_HAND;

        if (level instanceof ServerLevel)
        {
            int inserted = book.insertIntoOpenPage(stack);
            if (inserted > 0)
            {
                stack.consume(inserted, player); // creative players keep their stack
                level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Empty hand, not on an arrow: sneak to pick the book up
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof BookBlockEntity book)
        {
            pickUp(serverLevel, pos, state, book, player);
        }
        return InteractionResult.SUCCESS;
    }

    // Into the player's (empty) main hand, contents and all
    protected void pickUp(ServerLevel level, BlockPos pos, BlockState state, BookBlockEntity book, Player player)
    {
        ItemStack item = book.toItem();
        book.markPickedUp();
        level.removeBlock(pos, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, item);

        SoundType sound = state.getSoundType();
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
    }


    // ---- Left-click: take items out ----

    // Survival left-click (each click)
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player)
    {
        if (level instanceof ServerLevel serverLevel) extract(serverLevel, pos, player);
    }

    // Creative players break blocks instantly instead of "attacking" them, so take items out here instead.
    // Books never break this way in survival either (their hardness is -1, like bedrock), so this always says no.
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack,
                                       boolean willHarvest, FluidState fluid)
    {
        if (player.isCreative() && level instanceof ServerLevel serverLevel) extract(serverLevel, pos, player);
        return false;
    }

    private void extract(ServerLevel level, BlockPos pos, Player player)
    {
        if (!(level.getBlockEntity(pos) instanceof BookBlockEntity book)) return;
        ItemStack taken = book.extractFromOpenPage(player.isShiftKeyDown() ? SHIFT_EXTRACT : 1);
        if (taken.isEmpty()) return;

        // Into the inventory, or dropped at the player's feet if it's full (MowLib's shared helper)
        StorageInteractions.giveToPlayer(player, taken);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.3f, 1f);
    }
}
