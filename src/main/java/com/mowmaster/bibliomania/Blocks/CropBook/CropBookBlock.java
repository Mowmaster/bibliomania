package com.mowmaster.bibliomania.Blocks.CropBook;

import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;

// The Farmer's Almanac placed on the ground. Shows its soil (a thin top layer) and the crop at its growth stage on
// the pages (CropBookRenderer). Grows on random ticks, like a real crop (see CropBookActions#tryGrow).
// - Right-click with soil (first), then a seed: plants it (one item used)
// - Right-click with bone meal: the next stage straight away
// - Left-click with the hand or any tool once fully grown: harvests, drops on the book, back to the first stage
//   (CropBookEvents#onLeftClickBlock, like the mob books' left-clicks)
// - Sneak + right-click with an empty hand: picks the book up, crop and all
public class CropBookBlock extends BookBlock
{
    public CropBookBlock(Properties properties)
    {
        // BookBlock's page settings aren't used
        super(properties, 1, 1);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CropBookBlockEntity(pos, state);
    }

    // No items go in the usual way
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

    // ---- Growing on random ticks ----

    // Blocks only get random ticks if they ask for them (crops, leaves, ice...)
    @Override
    protected boolean isRandomlyTicking(BlockState state)
    {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!(level.getBlockEntity(pos) instanceof CropBookBlockEntity book)) return;
        CropContents grown = CropBookActions.tryGrow(book.getContents(), random);
        if (grown != null) book.setContents(grown);
    }

    // ---- Right-click: soil, seeds, bone meal ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof CropBookBlockEntity book)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        CropContents contents = book.getContents();

        if (CropBookActions.isBoneMeal(stack))
        {
            CropContents grown = CropBookActions.boneMeal(contents);
            if (grown == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (level instanceof ServerLevel)
            {
                book.setContents(grown);
                stack.consume(1, player);
                // The green sparkles and sound of bone meal working
                level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
            }
            return InteractionResult.SUCCESS;
        }

        CropContents planted = CropBookActions.insert(contents, stack);
        if (planted == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel)
        {
            book.setContents(planted);
            stack.consume(1, player);
            level.playSound(null, pos, contents.soil().isEmpty() ? SoundEvents.ROOTED_DIRT_PLACE : SoundEvents.CROP_PLANTED,
                    SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    // Empty hand: sneak to pick the book up
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof CropBookBlockEntity book)
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

    // ---- Left-click: harvesting (through CropBookEvents) ----

    // Turns off BookBlock's left-click (taking items out)
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

    // A full harvest with whatever the player holds (Fortune applies), dropped on the book; back to the first stage
    public static void harvest(ServerLevel level, BlockPos pos, Player player)
    {
        if (!(level.getBlockEntity(pos) instanceof CropBookBlockEntity book) || !book.getContents().grown()) return;
        for (ItemStack drop : CropBookActions.harvest(level, book.getContents(), pos, player.getMainHandItem(), player))
        {
            // popResource drops it in the block space with a little spread, like a broken block's drops
            Block.popResource(level, pos, drop);
        }
        book.setContents(book.getContents().withStage(0));
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
    }

}
