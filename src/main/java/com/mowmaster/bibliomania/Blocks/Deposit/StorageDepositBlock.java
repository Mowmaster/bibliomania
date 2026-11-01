package com.mowmaster.bibliomania.Blocks.Deposit;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryDeposit;
import com.mowmaster.mowlib.util.CBPixelBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

// The Storage Deposit (storage_deposit, the modelled half block; storage_deposit_full, a full block; they work the
// same): a library's way in. In a library (touching it, or linked with the Librarian's Quill):
// - right-click with an item: as much of the held stack as fits goes into the library's item books
// - right-click with a storage book, bundle, shulker box or other container: its contents go in, the container stays
//   in hand (LibraryDeposit#emptyHeld)
// - right-click with a bucket of fluid: the fluid goes into the library's fluid books and the empty bucket stays in
//   hand (if there's no room for the fluid, the full bucket is stored as an item instead); an empty bucket is just an
//   item
// - hoppers, pipes and cables can put items, fluids, energy and experience in (never take anything out): see
//   StorageDepositEvents
// The model's front (the slot) is its north face at facing=north.
public class StorageDepositBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public StorageDepositBlock(Properties properties, boolean full)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        // The half block: its back half and the slanted lid over the middle, as one box
        CBPixelBox box = full ? new CBPixelBox(0, 0, 0, 16, 16, 16) : new CBPixelBox(0, 0, 4, 16, 15, 16);
        for (Direction facing : Direction.Plane.HORIZONTAL)
        {
            shapes.put(facing, box.rotateY(CBPixelBox.quarterTurns(facing)).toShape());
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    // The front towards the player placing it
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new StorageDepositBlockEntity(pos, state);
    }

    // ---- Right-click with something ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof StorageDepositBlockEntity deposit)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        LibraryControllerBlockEntity library = deposit.library(serverLevel, pos);
        if (library == null)
        {
            message(player, "no_library");
            return InteractionResult.SUCCESS;
        }

        // A storage book, bundle or other container: its contents in (as much as fits), the container kept in hand
        int emptied = LibraryDeposit.emptyHeld(library, player, hand);
        if (emptied >= 0)
        {
            if (emptied > 0) level.playSound(null, pos, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 1f, 1f);
            else message(player, "full");
            return InteractionResult.SUCCESS;
        }

        // A bucket of fluid: the fluid in, the empty bucket left in hand
        if (LibraryDeposit.isFluidBucket(stack))
        {
            try (Transaction transaction = Transaction.openRoot())
            {
                // forPlayerInteraction: the held stack, as the bucket's own handler sees it (it swaps the full bucket
                // for an empty one in the hand; in creative the hand keeps its bucket); oneByOne: one bucket of a stack
                if (LibraryDeposit.emptyFluid(library, ItemAccess.forPlayerInteraction(player, hand).oneByOne(), transaction))
                {
                    transaction.commit();
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        // Anything else (or a bucket whose fluid didn't fit): as items
        int inserted;
        try (Transaction transaction = Transaction.openRoot())
        {
            inserted = LibraryDeposit.insertItems(library, stack, transaction);
            transaction.commit();
        }
        if (inserted <= 0)
        {
            message(player, "full");
            return InteractionResult.SUCCESS;
        }
        if (!player.hasInfiniteMaterials()) stack.shrink(inserted);
        level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    private static void message(Player player, String key)
    {
        player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".deposit." + key));
    }
}
