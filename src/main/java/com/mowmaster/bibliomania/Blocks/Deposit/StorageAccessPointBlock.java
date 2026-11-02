package com.mowmaster.bibliomania.Blocks.Deposit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.mowmaster.mowlib.tint.IColorableBlock;
import org.jetbrains.annotations.NotNull;

// The Storage Access Point: a library's door for automation only. In a library (touching it, or linked with the
// Librarian's Quill), hoppers, pipes, cables and copper golems can put any transport type in (highest priority shelves
// first) and take any out, from every side (StorageAccessPointEvents). Players can only dye it (one colour, MowLib's
// tint: the greyscale texture shows the colour; undyed it's the texture as it is) and seal it.
public class StorageAccessPointBlock extends Block implements IColorableBlock
{
    public StorageAccessPointBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new StorageAccessPointBlockEntity(pos, state);
    }

    // The colour is kept in StorageAccessPointBlockEntity (with its library link), not MowLib's plain tint block entity
    @Override
    public boolean usesTintBlockEntity()
    {
        return false;
    }

    // Dyes (not while sealed); nothing else
    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (level.getBlockEntity(pos) instanceof StorageAccessPointBlockEntity point && point.isLocked()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        return tryDyeBlock(stack, state, level, pos, player, hitResult);
    }
}
