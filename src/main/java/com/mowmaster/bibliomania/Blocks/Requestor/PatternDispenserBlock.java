package com.mowmaster.bibliomania.Blocks.Requestor;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

// What the Patterned Storage Requestor and the Storage Dispenser share. Both are DispenserBlocks underneath, which
// gives them the dispenser's own workings: FACING (all six ways, the front towards the player placing it), the
// redstone pulse (a rising signal sets TRIGGERED and schedules a tick 4 ticks later, which calls dispenseFrom; each
// block overrides dispenseFrom with its own job), and turning with structure rotation.
// Their grid face (RequestorGrid) holds the pattern:
// - right-click a cell with an item: the cell is set to it, its stack size the count (the dispenser: always 1)
// - right-click a set cell with an empty hand: cleared
// - the page keys while looking at a cell: one more or fewer (sneaking: 8); Requestor only, see its block entity
// The Librarian's Seal locks the pattern. The Bookbinder's Bone Folder turns the block to face another way.
public abstract class PatternDispenserBlock extends DispenserBlock
{
    protected PatternDispenserBlock(Properties properties)
    {
        super(properties);
    }

    // The pattern's block entity at pos, or null
    private static PatternHolder holder(Level level, BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof PatternHolder holder ? holder : null;
    }

    public static int cellAt(BlockState state, BlockHitResult hit, PatternHolder holder)
    {
        Direction facing = state.getValue(FACING);
        return RequestorGrid.cellAt(facing, hit, holder.pattern().size());
    }

    // ---- Setting the pattern ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        PatternHolder holder = holder(level, pos);
        if (stack.isEmpty() || holder == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        int cell = cellAt(state, hitResult, holder);
        if (cell < 0) return InteractionResult.PASS;
        if (holder instanceof LibraryMember member && member.isLocked()) return locked(level, player);
        if (!level.isClientSide())
        {
            holder.pattern().set(cell, stack);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    // Never opens the dispenser's screen (DispenserBlock would): an empty hand clears the cell clicked
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        PatternHolder holder = holder(level, pos);
        if (holder == null || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        int cell = cellAt(state, hitResult, holder);
        if (holder.pattern().item(cell).isEmpty()) return InteractionResult.PASS;
        if (holder instanceof LibraryMember member && member.isLocked()) return locked(level, player);
        if (!level.isClientSide())
        {
            holder.pattern().clear(cell);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult locked(Level level, Player player)
    {
        if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".library.locked"));
        return InteractionResult.SUCCESS;
    }
}
