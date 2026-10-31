package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

// The Bestiary placed on the ground. It looks and sits like the other books (shape, facing, thickness from
// BookBlock), but holds mobs instead of items, so the clicks are different:
// - Right-click with the mobs' breeding food: breeds them (see MobBookContents for the rules)
// - Left-click with a sword: kills one; sneak + left-click: kills 10% (at least one). Drops and XP land on the book.
// - Right-click with a dye: recolours dyeable mobs (sheep); with shears: shears them, drops on the book;
//   (mooshrooms: shearing one kills it, mushrooms + its death drops on the book); with a bucket/bowl: milks them (cows, mooshrooms, goats; see MobBookMilking), filled ones to the player
// - Sneak + right-click with an empty hand: picks the book up, mobs and all
// - Chickens and other egg layers drop their eggs on the book every 7.5 minutes (MobBookDrops)
// Mobs go in with the book item (right-click an animal with it, see MobBookItem).
public class MobBookBlock extends BookBlock
{
    public MobBookBlock(Properties properties)
    {
        // BookBlock's page settings aren't used by the Bestiary
        super(properties, 1, 1);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new MobBookBlockEntity(pos, state);
    }

    // Placed books tick on the server only (for egg laying, see MobBookBlockEntity#serverTick).
    // getTicker is asked once when the block entity loads; returning null means it never ticks.
    // <T extends BlockEntity> is a generic method: T is whatever block entity type the game asks about. We only
    // return a ticker when it's ours, so the cast inside the lambda is safe.
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != libBlockEntityRegistry.MOB_BOOK.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((MobBookBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
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

    // ---- Right-click: breeding, dyeing, shearing ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof MobBookBlockEntity book)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        MobBookContents contents = book.getContents();

        // A dye: recolours all the mobs (sheep...), one dye used
        if (MobBookActions.canDye(contents, stack))
        {
            if (level instanceof ServerLevel)
            {
                book.setContents(contents.withAppearance(MobAppearance.dyed(contents.appearance(), MobBookActions.dyeColor(stack))));
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        }

        // An empty container (bucket, bowl...): milking. One filled per adult, into the player's inventory
        // (at their feet if it's full), like milking a cow. Then the configured wait (none by default).
        MobBookMilking.Milking milking = MobBookMilking.milkingFor(contents, stack);
        if (milking != null && contents.adults() > 0)
        {
            if (level instanceof ServerLevel serverLevel)
            {
                if (contents.milkWaiting(level.getGameTime()))
                {
                    level.playSound(null, pos, SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.BLOCKS, 1f, 1f);
                }
                else
                {
                    int count = MobBookMilking.amount(contents, stack);
                    for (ItemStack filled : MobBookMilking.milk(serverLevel, milking, stack, count, player, pos))
                    {
                        StorageInteractions.giveToPlayer(player, filled);
                    }
                    book.setContents(contents.withMilked(level.getGameTime()));
                }
            }
            return InteractionResult.SUCCESS;
        }

        // Shears: shears every adult, the drops land on the book; then they grow back (ServerConfig.shearRegrowth)
        if (MobBookActions.isShears(stack))
        {
            // Mooshrooms (the shear_kills data map): one is sheared for its mushrooms and killed, drops on the book
            if (MobBookDrops.shearKillFor(contents) != null && contents.adults() > 0)
            {
                if (level instanceof ServerLevel serverLevel)
                {
                    Vec3 at = Vec3.atCenterOf(pos).add(0, 0.2, 0);
                    book.setContents(MobBookActions.shearKill(serverLevel, contents, player, stack, at, false));
                    book.markKilled(); // the red flash
                    stack.hurtAndBreak(1, player, hand);
                }
                return InteractionResult.SUCCESS;
            }
            if (MobBookActions.canShear(level, contents))
            {
                if (level instanceof ServerLevel serverLevel)
                {
                    for (ItemStack drop : MobBookActions.shear(serverLevel, contents, player, stack, Vec3.atCenterOf(pos)))
                    {
                        Block.popResource(serverLevel, pos, drop);
                    }
                    book.setContents(contents.withSheared(level.getGameTime()));
                    stack.hurtAndBreak(1, player, hand); // one durability per use, like shearing one sheep
                }
                return InteractionResult.SUCCESS;
            }
            if (contents.regrowing(level.getGameTime()))
            {
                // Still growing back
                if (level instanceof ServerLevel) level.playSound(null, pos, SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.BLOCKS, 1f, 1f);
                return InteractionResult.SUCCESS;
            }
        }

        if (!MobBookActions.isBreedingFood(level, contents, stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;

        if (level instanceof ServerLevel serverLevel)
        {
            int eaten = Math.min(stack.getCount(), contents.maxFeed());
            if (eaten > 0)
            {
                book.setContents(contents.withFed(eaten, level.getGameTime()));
                stack.consume(eaten, player); // creative players keep their stack
                level.playSound(null, pos, SoundEvents.GENERIC_EAT.value(), SoundSource.BLOCKS, 1f, 1f);
                serverLevel.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        5, 0.3, 0.2, 0.3, 0);
            }
            else
            {
                // Everyone has eaten this cycle, or the book is full
                level.playSound(null, pos, SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.BLOCKS, 1f, 1f);
            }
        }
        // SUCCESS on both sides so the client swings the arm
        return InteractionResult.SUCCESS;
    }

    // Empty hand: sneak to pick the book up
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof MobBookBlockEntity book)
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

    // ---- Left-click: killing ----

    // Left-clicks are caught by MobBookActions#onLeftClickBlock (a NeoForge event) and call killAction. Block#attack
    // isn't used: creative players break blocks instead of "attacking" them, and swords can't break blocks in
    // creative, so neither attack nor onDestroyedByPlayer ever ran for them.

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

    // Ticks between kills: holding left-click sends a new click every tick, and this stops that
    // from emptying the book (10 ticks is also how long the red flash lasts)
    private static final int KILL_DELAY = 10;

    static void killAction(ServerLevel level, BlockPos pos, Player player)
    {
        if (!(level.getBlockEntity(pos) instanceof MobBookBlockEntity book)) return;
        MobBookContents contents = book.getContents();
        if (!MobBookActions.isKillWeapon(player.getMainHandItem()) || contents.adults() <= 0 || contents.type().isEmpty())
        {
            return;
        }
        long sinceKill = level.getGameTime() - book.getLastKill();
        if (sinceKill >= 0 && sinceKill < KILL_DELAY) return;
        // Sneaking: 10% of the adults, rounded up (so always at least one)
        int count = player.isShiftKeyDown() ? (int) Math.ceil(contents.adults() * 0.1) : 1;
        Vec3 dropAt = Vec3.atCenterOf(pos).add(0, 0.2, 0);
        int killed = MobBookActions.kill(level, contents, count, player, dropAt, false);
        book.setContents(contents.withKilled(killed));
        book.markKilled();
    }
}
