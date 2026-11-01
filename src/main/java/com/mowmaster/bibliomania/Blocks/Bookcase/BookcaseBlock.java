package com.mowmaster.bibliomania.Blocks.Bookcase;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.tint.IColorableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// A Bookcase: two shelves of three Bibliomania books each (slots 0-2 the top shelf from the left, 3-5 the bottom, as
// seen from the front). The books keep working while shelved (ShelvedBook#shelfTick), and hoppers, pipes and copper
// golems reach all their storage as one block: items into the item books, fluids into Books of Fluids, and so on,
// the first book first (BookcaseBlockEntity). Types it has no book for aren't offered at all.
// - Right-click a shelf space with a book: puts it there. With an empty hand: takes that book out.
//   (Items themselves only go in and out through automation, never by hand.)
// - Right-click with a dye (or the Color Palette): dyes it (MowLib's tinting, mixed like leather armour). The first
//   dye swaps the models to grey textures (the "dyed" state), which the colour then tints.
// The model opens to the north at facing=north; the blockstate turns it to face the player who placed it.
public class BookcaseBlock extends Block implements IColorableBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DYED = BooleanProperty.create("dyed");

    // The shelf spaces, in model pixels (facing north): 14 wide (x 1-15) split three ways; two shelves
    private static final float SHELF_LEFT = 1f;
    private static final float SHELF_WIDTH = 14f;

    public BookcaseBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DYED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, DYED);
    }

    // Open side towards the player; already dyed if the item carries a colour
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

    // ---- Shelf geometry (also used by the renderer and the Reading Goggles) ----

    // Where the shelf spaces start, front to back, in model pixels (facing north, open side at z 0)
    public float shelfFront(BlockState state)
    {
        return 0f;
    }

    // The floor of each shelf, in model pixels: the top shelf's, then the bottom one's
    public float shelfFloor(int row)
    {
        return row == 0 ? 8.5f : 1f;
    }

    // Which slot (0-5) a click or the crosshair is on, or -1. Only the open side counts.
    public int slotAt(BlockState state, BlockHitResult hit)
    {
        Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) return -1;
        BlockPos pos = hit.getBlockPos();
        Vec3 at = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        // Across the model (its x, 0-1) whichever way it's turned: the blockstate turns the model's east side to
        // face south at facing=east, west at facing=south, north at facing=west
        double modelX = switch (facing)
        {
            case EAST -> at.z();
            case SOUTH -> 1.0 - at.x();
            case WEST -> 1.0 - at.z();
            default -> at.x();
        };
        double pixels = modelX * 16.0 - SHELF_LEFT;
        if (pixels < 0 || pixels >= SHELF_WIDTH) return -1;
        // Model column 0 is at the low x, which is the viewer's right: the viewer's left column is model column 2
        int modelColumn = Math.min(2, (int) (pixels / (SHELF_WIDTH / 3)));
        int row = at.y() >= 0.5 ? 0 : 1;
        return (2 - modelColumn) + row * 3;
    }

    // The left edge of a slot's space, in model pixels
    public static float slotLeft(int slot)
    {
        int modelColumn = 2 - slot % 3;
        return SHELF_LEFT + modelColumn * SHELF_WIDTH / 3;
    }

    // Any Bibliomania book (its block is a BookBlock) can stand on a shelf
    public static boolean isShelfBook(ItemStack stack)
    {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof BookBlock;
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new BookcaseBlockEntity(pos, state);
    }

    // The colours are kept in BookcaseBlockEntity, not MowLib's plain tint block entity
    @Override
    public boolean usesTintBlockEntity()
    {
        return false;
    }

    // Server ticks: the books' own ticking, and syncing (see MobBookBlock#getTicker for how this works)
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != libBlockEntityRegistry.BOOKCASE.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((BookcaseBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
    }

    // ---- Right-click ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        // A dye: tint it, and swap to the grey textures the first time
        InteractionResult dyed = tryDyeBlock(stack, state, level, pos, player, hitResult);
        if (dyed == InteractionResult.SUCCESS)
        {
            if (!level.isClientSide() && !state.getValue(DYED)) level.setBlock(pos, state.setValue(DYED, true), Block.UPDATE_ALL);
            return dyed;
        }
        if (!isShelfBook(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        int slot = slotAt(state, hitResult);
        if (slot < 0 || !(level.getBlockEntity(pos) instanceof BookcaseBlockEntity shelf) || !shelf.getBook(slot).isEmpty())
        {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide())
        {
            shelf.setBook(slot, stack.copyWithCount(1));
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    // Empty hand: take the book from the shelf space looked at
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        int slot = slotAt(state, hitResult);
        if (slot < 0 || !(level.getBlockEntity(pos) instanceof BookcaseBlockEntity shelf) || shelf.getBook(slot).isEmpty())
        {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide())
        {
            ItemStack book = shelf.getBook(slot).copy();
            shelf.setBook(slot, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, book);
            level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Comparators: how full its storage books are ----

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state)
    {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction)
    {
        if (!(level.getBlockEntity(pos) instanceof BookcaseBlockEntity shelf)) return 0;
        float fullness = shelf.fullness();
        return fullness < 0 ? 0 : (int) Math.ceil(fullness * 15);
    }
}
