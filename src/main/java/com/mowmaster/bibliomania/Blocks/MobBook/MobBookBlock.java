package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;

// The Bestiary placed on the ground. It looks and sits like the other books (shape, facing, thickness from
// BookBlock), but holds mobs instead of items, so the clicks are different:
// - Right-click with the mobs' breeding food: breeds them (see MobBookContents for the rules)
// - Left-click with a sword: kills one; sneak + left-click: kills 10% (at least one). Drops and XP land on the book.
// - Sneak + right-click with an empty hand: picks the book up, mobs and all
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

    // ---- Right-click: breeding ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof MobBookBlockEntity book)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        MobBookContents contents = book.getContents();
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
        int killed = MobBookActions.kill(level, contents.type().get(), count, player, dropAt, false);
        book.setContents(contents.withKilled(killed));
        book.markKilled();
    }
}
