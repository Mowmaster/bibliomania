package com.mowmaster.bibliomania.Blocks.EssenceBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// The Book of Essence placed on the ground. Sits like the other books (BookBlock), shows its mob spinning above it
// (MobBookRenderer, growing as the essence fills), and once at 100% essence "spawns" mobs into itself over time.
// - Right-click with a speed item (nether star) or size item (heavy core): boosts the spawning (EssenceActions)
// - Left-click with a sword: kills one stored mob; sneak + left-click: 10% (at least one). Drops and XP on the book.
//   (Left-clicks come through MobBookActions#onLeftClickBlock, like the Bestiary.)
// - Sneak + right-click with an empty hand: picks the book up
public class EssenceBookBlock extends BookBlock
{
    public EssenceBookBlock(Properties properties)
    {
        // BookBlock's page settings aren't used
        super(properties, 1, 1);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new EssenceBookBlockEntity(pos, state);
    }

    // Ticks on the server only, for spawning (see MobBookBlock#getTicker for how this works)
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != libBlockEntityRegistry.ESSENCE_BOOK.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((EssenceBookBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
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

    // ---- Right-click: boosts ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof EssenceBookBlockEntity book)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        EssenceContents contents = book.getContents();
        if (!EssenceActions.isBoost(contents, stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel serverLevel)
        {
            book.setContents(EssenceActions.boost(contents, stack));
            stack.consume(1, player);
            EssenceActions.boostSound(level, pos);
            serverLevel.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    20, 0.3, 0.3, 0.3, 0.5);
        }
        return InteractionResult.SUCCESS;
    }

    // Empty hand: sneak to pick the book up
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof EssenceBookBlockEntity book)
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

    // ---- Left-click: killing (see MobBookBlock for why attack() isn't used) ----

    // Turns off BookBlock's left-click (taking items out)
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player)
    {
    }

    // Never broken by a player (picked up with sneak + right-click instead)
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack,
                                       boolean willHarvest, FluidState fluid)
    {
        return false;
    }

    // Ticks between kills (holding left-click sends a click every tick)
    private static final int KILL_DELAY = 10;

    public static void killAction(ServerLevel level, BlockPos pos, Player player)
    {
        if (!(level.getBlockEntity(pos) instanceof EssenceBookBlockEntity book)) return;
        EssenceContents contents = book.getContents();
        if (!MobBookActions.isKillWeapon(player.getMainHandItem()) || contents.stored() <= 0 || contents.type().isEmpty())
        {
            return;
        }
        long sinceKill = level.getGameTime() - book.getLastKill();
        if (sinceKill >= 0 && sinceKill < KILL_DELAY) return;
        // Sneaking: 10% of the stored mobs, rounded up (so always at least one)
        int count = player.isShiftKeyDown() ? (int) Math.ceil(contents.stored() * 0.1) : 1;
        Vec3 dropAt = Vec3.atCenterOf(pos).add(0, 0.2, 0);
        int killed = MobBookActions.kill(level, contents.type().get(), contents.appearance(), count, player, dropAt, false);
        book.setContents(contents.withKilled(killed));
        book.markKilled();
    }
}
