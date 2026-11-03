package com.mowmaster.bibliomania.Blocks.Library;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mowmaster.bibliomania.Blocks.Deposit.StorageAccessPointBlock;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.mowlib.tint.IColorableBlock;
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
// - It looks like an end crystal: two turning frames round one of the mod's books (a random one at first; right-click
//   with any Bibliomania book to show that one instead, the book stays in hand), over a base (LibraryControllerRenderer).
// - Dyes colour the frames and the base (MowLib's tint, kept in its block entity); it gives off full light.
public class LibraryControllerBlock extends Block implements IColorableBlock
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

    // The colour is kept in LibraryControllerBlockEntity, not MowLib's plain tint block entity
    @Override
    public boolean usesTintBlockEntity()
    {
        return false;
    }

    // Undyed, the base shows the access point's wood colour (the frames stay as they're drawn)
    @Override
    public int getDefaultColor(int region)
    {
        return StorageAccessPointBlock.DEFAULT_COLOR;
    }

    // A dye: colours it (not while sealed). A Bibliomania book: shown in the middle (the book isn't used up).
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
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BookBlock
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
