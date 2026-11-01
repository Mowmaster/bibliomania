package com.mowmaster.bibliomania.Items;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLockable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

// The Bookbinder's Bone Folder: turns Bibliomania blocks. Right-click a face: the block now faces out of that face
// (sneaking: into it, the opposite way). Blocks that face all six ways (the Patterned Storage Requestor, the Storage
// Dispenser) take any face; blocks that only face sideways (Bookcases, Lecterns, Card Catalogs, the Storage Deposit)
// take the side faces, and the top or bottom turns them towards the player. Not while the block is sealed.
// Its use goes before the block's own right-click (LibraryToolEvents), so it doesn't set a requestor's pattern.
public class BoneFolderItem extends Item
{
    public BoneFolderItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(BibliomaniaReferences.MODID)) return InteractionResult.PASS;
        Player player = context.getPlayer();
        boolean sneaking = player != null && player.isShiftKeyDown();
        Direction face = sneaking ? context.getClickedFace().getOpposite() : context.getClickedFace();

        BlockState turned;
        if (state.hasProperty(BlockStateProperties.FACING)) turned = state.setValue(BlockStateProperties.FACING, face);
        else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
        {
            // getHorizontalDirection: the way the player is looking; its opposite faces them
            Direction side = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
            turned = state.setValue(BlockStateProperties.HORIZONTAL_FACING, side);
        }
        else return InteractionResult.PASS;
        if (turned == state) return InteractionResult.SUCCESS;

        if (!level.isClientSide())
        {
            if (level.getBlockEntity(pos) instanceof LibraryLockable lockable && lockable.isLocked())
            {
                if (player != null) LibraryQuillItem.message(player, "locked");
                return InteractionResult.SUCCESS;
            }
            level.setBlock(pos, turned, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".bone_folder.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
