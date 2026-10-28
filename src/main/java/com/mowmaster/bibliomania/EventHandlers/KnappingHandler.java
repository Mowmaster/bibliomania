package com.mowmaster.bibliomania.EventHandlers;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.ItemRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

// Replaces 1.20's mowlib "dual handed crafting" for the knife parts: hold flint in both hands and right-click
//   - any stone (#minecraft:base_stone_overworld) -> a stone knife blade
//   - any log (#minecraft:logs)                    -> an oak knife handle
// Each knap uses up one flint from each hand.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public class KnappingHandler
{
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        // The event fires once per hand; only act on the main hand's turn
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        ItemStack offhand = player.getOffhandItem();
        if (!player.getMainHandItem().is(Items.FLINT) || !offhand.is(Items.FLINT)) return;

        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        Item result = resultFor(state);
        if (result == null) return;

        if (level instanceof ServerLevel)
        {
            // Knapping uses up both flints (2 per part, as in 1.20). consume() leaves creative players' stacks alone,
            // like vanilla does; use shrink(1) instead if you want creative to use them up too.
            player.getMainHandItem().consume(1, player);
            offhand.consume(1, player);
            ItemStack part = new ItemStack(result);
            // add() returns false (and keeps what didn't fit) when the inventory is full: drop the rest on the block
            if (!player.getInventory().add(part)) Block.popResource(level, event.getPos().above(), part);
            SoundType sound = state.getSoundType();
            level.playSound(null, event.getPos(), sound.getHitSound(), SoundSource.BLOCKS, 1f, 0.8f);
        }
        // Cancel on both sides so nothing else happens with the click; SUCCESS swings the arm
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @Nullable
    private static Item resultFor(BlockState state)
    {
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) return ItemRegistry.KNIFEPART_BLADE_STONE.get();
        if (state.is(BlockTags.LOGS)) return ItemRegistry.KNIFEPART_HANDLE_OAK.get();
        return null;
    }
}
