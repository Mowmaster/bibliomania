package com.mowmaster.bibliomania.Blocks.Library;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlock;
import com.mowmaster.bibliomania.Blocks.Deposit.StorageAccessPointBlock;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.tint.IColorableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

// The Library Controller: the core of a library (see LibraryControllerBlockEntity). Bookcases and Reading Lecterns
// touching it, or touching another block of the library, join it by themselves; the Librarian's Quill links blocks
// further away (or takes touching ones out), the Librarian's Seal locks it.
// - A half-slab base facing any of the six ways (FACING: the way its top points, like an end rod: the side clicked
//   when placed), with one of the mod's books standing on it, upright as on a Bookcase (LibraryControllerRenderer).
//   A random book at first; right-click with any Bibliomania book to show that one instead (the book stays in hand).
// - Right-click with an empty hand: says how many bookcases and lecterns the library has.
// - Dyes colour the base (MowLib's tint, kept in its block entity); it gives off full light.
public class LibraryControllerBlock extends Block implements IColorableBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    // The base: the half of the block on the side opposite FACING
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static
    {
        SHAPES.put(Direction.UP, Block.box(0, 0, 0, 16, 8, 16));
        SHAPES.put(Direction.DOWN, Block.box(0, 8, 0, 16, 16, 16));
        SHAPES.put(Direction.NORTH, Block.box(0, 0, 8, 16, 16, 16));
        SHAPES.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 8));
        SHAPES.put(Direction.EAST, Block.box(0, 0, 0, 8, 16, 16));
        SHAPES.put(Direction.WEST, Block.box(8, 0, 0, 16, 16, 16));
    }

    public LibraryControllerBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    // Its top faces out of the side it was placed against (on a floor: up; on a wall: out of the wall)
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new LibraryControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != libBlockEntityRegistry.LIBRARY_CONTROLLER.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((LibraryControllerBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
    }

    // ---- Colour: the base (one region, the block model's tintindex 0) ----

    // The colour is kept in LibraryControllerBlockEntity, not MowLib's plain tint block entity
    @Override
    public boolean usesTintBlockEntity()
    {
        return false;
    }

    // Undyed: the access point's wood colour
    @Override
    public int getDefaultColor(int region)
    {
        return StorageAccessPointBlock.DEFAULT_COLOR;
    }

    // ---- Right-click ----

    // A dye: colours it (not while sealed). A Bibliomania book: shown on it (the book isn't used up).
    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof LibraryControllerBlockEntity controller)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (IColorableBlock.dyeColorOf(stack) != null)
        {
            if (controller.isLocked()) return InteractionResult.SUCCESS;
            return tryDyeBlock(stack, state, level, pos, player, hitResult);
        }
        // Anything that stands on a Bookcase (the books and the Atlas)
        if (BookcaseBlock.isShelfBook(stack)
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(BibliomaniaReferences.MODID))
        {
            if (!level.isClientSide())
            {
                controller.setDisplayBook(stack);
                level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof LibraryControllerBlockEntity controller)) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel)
        {
            controller.scan(serverLevel);
            player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".library.summary",
                    controller.getBookcaseCount(), controller.getLecternCount()));
        }
        return InteractionResult.SUCCESS;
    }
}
