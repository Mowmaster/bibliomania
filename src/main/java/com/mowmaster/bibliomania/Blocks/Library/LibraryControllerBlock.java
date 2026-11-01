package com.mowmaster.bibliomania.Blocks.Library;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// The Library Controller: the core of a library (see LibraryControllerBlockEntity). Bookcases and Reading Lecterns
// touching it, or touching another block of the library, join it by themselves; the Librarian's Quill links blocks
// further away (or takes touching ones out), the Librarian's Seal locks it.
// - Right-click with an empty hand: says how many bookcases and lecterns the library has.
public class LibraryControllerBlock extends Block implements EntityBlock
{
    public LibraryControllerBlock(Properties properties)
    {
        super(properties);
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
