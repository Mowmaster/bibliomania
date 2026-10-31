package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.transport.TransportType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

// A book that stores one MowLib transport type (fluids, experience, energy, or another mod's type). The type and its
// capacity are given when the block is made; what clicks do comes from the type's StorageBookBehaviour.
// A sphere above the book shows how full it is (StorageBookRenderer). Sneak + right-click with an empty hand picks
// it up with its contents; it can't be mined.
// The type is a Supplier (resolved when first used): blocks are registered before MowLib's transport types.
public class StorageBookBlock extends BookBlock
{
    private final Supplier<? extends TransportType<?>> type;
    private final IntSupplier capacity;

    public StorageBookBlock(Properties properties, Supplier<? extends TransportType<?>> type, IntSupplier capacity)
    {
        // BookBlock's page settings aren't used
        super(properties, 1, 1);
        this.type = type;
        this.capacity = capacity;
    }

    public TransportType<?> type()
    {
        return type.get();
    }

    // Read every time, so config changes apply straight away
    public int capacity()
    {
        return Math.max(1, capacity.getAsInt());
    }

    public StorageBookBehaviour behaviour()
    {
        return StorageBookBehaviours.get(type());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new StorageBookBlockEntity(pos, state);
    }

    // Server ticks: the behaviour's (experience orbs) and syncing the fill level (see MobBookBlock#getTicker)
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType)
    {
        if (level.isClientSide() || blockEntityType != libBlockEntityRegistry.STORAGE_BOOK.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((StorageBookBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
    }

    // No items go in
    @Override
    public boolean accepts(ItemResource resource)
    {
        return false;
    }

    @Override
    protected boolean allowsManualInsert()
    {
        return false;
    }

    // ---- Clicks: the behaviour's ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof StorageBookBlockEntity book)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        return behaviour().useItemOnPlaced(stack, book, level, pos, player, hand);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof StorageBookBlockEntity book)) return InteractionResult.PASS;
        if (player.isShiftKeyDown())
        {
            // Pick up, contents and all
            if (level instanceof ServerLevel serverLevel)
            {
                ItemStack item = book.toItem();
                book.markPickedUp();
                serverLevel.removeBlock(pos, false);
                player.setItemInHand(InteractionHand.MAIN_HAND, item);
                SoundType sound = state.getSoundType();
                level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
            }
            return InteractionResult.SUCCESS;
        }
        return behaviour().useWithoutItemPlaced(book, level, pos, player);
    }

    // Left-clicks go through StorageBookEvents#onLeftClickBlock (see MobBookBlock for why); BookBlock's is off
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player)
    {
    }

    // Never broken by a player
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack,
                                       boolean willHarvest, FluidState fluid)
    {
        return false;
    }
}
