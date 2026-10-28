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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

// Replaces 1.20's mowlib "dual handed crafting" for the knife parts: hold flint in both hands and right-click
//   - any stone (#c:stones)       -> a stone knife blade
//   - any oak log (#minecraft:oak_logs) -> an oak knife handle
// Each knap uses up the offhand flint; the mainhand flint is the tool.
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
            offhand.consume(1, player); // creative players keep it
            player.getInventory().add(new ItemStack(result));
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
