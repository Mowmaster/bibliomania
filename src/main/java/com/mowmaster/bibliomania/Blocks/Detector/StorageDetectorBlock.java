package com.mowmaster.bibliomania.Blocks.Detector;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

// The Storage Detector: a library member (touching the library, or linked with the Librarian's Quill) that watches
// for items running out. Each of its six faces works on its own:
// - right-click a face with an item: that face watches for it (the item is drawn in the face's window)
// - while the library holds none of it, the face lights up and gives a redstone signal of 15 out of that face
// - sneak + right-click a face with an empty hand: the item is taken off (back to the plain texture)
// The Librarian's Seal locks its faces. The faces' looks are blockstate properties (DetectorFace), so the
// blockstate file picks a texture per face (a multipart: one single-faced model per face, turned to that side).
public class StorageDetectorBlock extends Block implements EntityBlock
{
    // One property per face, named after the side ("north": none/off/on)
    public static final Map<Direction, EnumProperty<DetectorFace>> FACES = new EnumMap<>(Direction.class);
    static
    {
        for (Direction direction : Direction.values())
        {
            FACES.put(direction, EnumProperty.create(direction.getSerializedName(), DetectorFace.class));
        }
    }

    public StorageDetectorBlock(Properties properties)
    {
        super(properties);
        BlockState state = stateDefinition.any();
        for (EnumProperty<DetectorFace> face : FACES.values()) state = state.setValue(face, DetectorFace.NONE);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        // Direction.values() order, so the properties are added the same way every time
        for (Direction direction : Direction.values()) builder.add(FACES.get(direction));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new StorageDetectorBlockEntity(pos, state);
    }

    // Server ticks: the stock check (once a second, in the block entity)
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != libBlockEntityRegistry.STORAGE_DETECTOR.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((StorageDetectorBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
    }

    // ---- Setting and clearing faces ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof StorageDetectorBlockEntity detector)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (detector.isLocked()) return locked(level, player);
        if (!level.isClientSide())
        {
            detector.setItem(hitResult.getDirection(), stack);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    // Sneak + empty hand: the clicked face's item comes off
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof StorageDetectorBlockEntity detector)) return InteractionResult.PASS;
        Direction face = hitResult.getDirection();
        if (detector.item(face).isEmpty()) return InteractionResult.PASS;
        if (detector.isLocked()) return locked(level, player);
        if (!level.isClientSide())
        {
            detector.setItem(face, ItemStack.EMPTY);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult locked(Level level, Player player)
    {
        if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".library.locked"));
        return InteractionResult.SUCCESS;
    }

    // Puts new face looks into the blockstate. Only changes it if something changed; the block next to each face that
    // changed is told, and so are the blocks round that one (a strong signal powers through a solid block, like a
    // lever's or a redstone block's).
    public static void setFaces(ServerLevel level, BlockPos pos, BlockState state, DetectorFace[] faces)
    {
        BlockState changed = state;
        for (Direction direction : Direction.values()) changed = changed.setValue(FACES.get(direction), faces[direction.get3DDataValue()]);
        if (changed == state) return;
        level.setBlock(pos, changed, Block.UPDATE_ALL);
        for (Direction direction : Direction.values())
        {
            EnumProperty<DetectorFace> face = FACES.get(direction);
            if (state.getValue(face) != changed.getValue(face)) level.updateNeighborsAt(pos.relative(direction), changed.getBlock());
        }
    }

    // ---- Redstone ----

    @Override
    protected boolean isSignalSource(BlockState state)
    {
        return true;
    }

    // `direction` points from the block asking towards this one, so the face it touches is the opposite one
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return state.getValue(FACES.get(direction.getOpposite())) == DetectorFace.ON ? 15 : 0;
    }

    // Strong too: the signal also powers through the block the face touches (as a lever does)
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return getSignal(state, level, pos, direction);
    }
}
