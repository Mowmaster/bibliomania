package com.mowmaster.bibliomania.Blocks.CardCatalog;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.tint.IColorableBlock;
import com.mowmaster.mowlib.util.CBPixelBox;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

// A Card Catalog: 1 to 16 drawers (one block per size, card_catalog_1 to _16; the stonecutter turns any size into any
// other). It works inside a library (Library Controller): each drawer is set to an item, a book or a Ledger trade, and
// opening it makes the library's Reading Lecterns show that (see DrawerEntry, ReadingLecternBlockEntity).
// - Right-click an empty drawer with an item, an item book (its open page's item), a Ledger (its picked trade) or any
//   other Bibliomania book: the drawer is set to it. Sneak + right-click a set drawer with one of those: set to that
//   instead; with an empty hand: cleared.
// - Right-click a set drawer: opens it (closing any other open drawer in the whole library), or closes it. A drawer
//   whose item or book the library doesn't have won't open (the locked chest sound), and shows a cross.
// - Dyes: the case, or one drawer (whichever is clicked); Barter (gold) and trade (emerald) drawers keep their colour.
// - The Librarian's Seal locks the drawer settings and colours.
// Open drawers are the OPEN state (0: none, n: drawer n); the model opens to the north at facing=north.
public class CardCatalogBlock extends Block implements IColorableBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    // One property for every size (a property can't depend on the block); values above the size are never set
    public static final IntegerProperty OPEN = IntegerProperty.create("open", 0, CardCatalogBlockEntity.MAX_DRAWERS);

    private final int size;
    // Per facing, per open drawer (0 = none): the back-half case, plus the open drawer pulled out
    private final Map<Direction, VoxelShape[]> shapes = new EnumMap<Direction, VoxelShape[]>(Direction.class);

    public CardCatalogBlock(Properties properties, int size)
    {
        super(properties);
        this.size = size;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, 0));
        CBPixelBox caseBox = new CBPixelBox(0, 0, 8, 16, 16, 16);
        for (Direction facing : Direction.Plane.HORIZONTAL)
        {
            int turns = CBPixelBox.quarterTurns(facing);
            VoxelShape[] byOpen = new VoxelShape[size + 1];
            VoxelShape closed = caseBox.rotateY(turns).toShape();
            byOpen[0] = closed;
            float[][] layout = CardCatalogLayout.drawers(size);
            for (int drawer = 0; drawer < size; drawer++)
            {
                float[] r = layout[drawer];
                CBPixelBox pulled = new CBPixelBox(r[0], r[1], CardCatalogLayout.OPEN_Z, r[2], r[3], CardCatalogLayout.CLOSED_Z);
                byOpen[drawer + 1] = Shapes.or(closed, pulled.rotateY(turns).toShape());
            }
            shapes.put(facing, byOpen);
        }
    }

    public int size()
    {
        return size;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, OPEN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        VoxelShape[] byOpen = shapes.get(state.getValue(FACING));
        int open = state.getValue(OPEN);
        return byOpen[open <= size ? open : 0];
    }

    // ---- Drawers ----

    // Which drawer (0-based) a click or the crosshair is on (its front), or -1
    public int drawerAt(BlockState state, BlockHitResult hit)
    {
        Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) return -1;
        BlockPos pos = hit.getBlockPos();
        Vec3 at = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        // Across the model (its x) whichever way it's turned (see BookcaseBlock#slotAt)
        double modelX = switch (facing)
        {
            case EAST -> at.z();
            case SOUTH -> 1.0 - at.x();
            case WEST -> 1.0 - at.z();
            default -> at.x();
        } * 16.0;
        double y = at.y() * 16.0;
        float[][] layout = CardCatalogLayout.drawers(size);
        for (int drawer = 0; drawer < size; drawer++)
        {
            float[] r = layout[drawer];
            if (modelX >= r[0] && modelX <= r[2] && y >= r[1] && y <= r[3]) return drawer;
        }
        return -1;
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CardCatalogBlockEntity(pos, state);
    }

    @Override
    public boolean usesTintBlockEntity()
    {
        return false;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != libBlockEntityRegistry.CARD_CATALOG.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((CardCatalogBlockEntity) blockEntity).serverTick((ServerLevel) tickLevel);
    }

    // ---- Colours: region 0 the case, region n drawer n (model tintindex n) ----

    @Override
    public int getTintRegionCount()
    {
        return size + 1;
    }

    @Override
    public int getTintRegion(BlockState state, BlockHitResult hit)
    {
        return drawerAt(state, hit) + 1;
    }

    // Undyed: the grey textures tinted back to their wood colours
    @Override
    public int getDefaultColor(int region)
    {
        return region == 0 ? CardCatalogLayout.CASE_COLOR : CardCatalogLayout.DRAWER_COLOR;
    }

    // ---- Right-click ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof CardCatalogBlockEntity catalog)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        int drawer = drawerAt(state, hitResult);
        DrawerEntry entry = catalog.drawer(drawer);

        // Dyes (not on a drawer with a fixed colour: Barter, trades; not while locked)
        if (IColorableBlock.dyeColorOf(stack) != null)
        {
            if (catalog.isLocked() || (entry != null && entry.hasTint())) return InteractionResult.SUCCESS;
            return tryDyeBlock(stack, state, level, pos, player, hitResult);
        }
        if (drawer < 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        // An empty drawer: set it from what's held
        if (entry == null)
        {
            if (catalog.isLocked()) return locked(level, player);
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            DrawerEntry set = DrawerEntry.from(stack);
            if (set == null) return InteractionResult.SUCCESS;
            catalog.setDrawer(drawer, set);
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
            return InteractionResult.SUCCESS;
        }
        // Sneak with an item on a set drawer: set it to that instead (closing it first if it's open)
        if (player.isShiftKeyDown())
        {
            if (catalog.isLocked()) return locked(level, player);
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
            DrawerEntry set = DrawerEntry.from(stack);
            if (set == null) return InteractionResult.SUCCESS;
            if (state.getValue(OPEN) == drawer + 1) close(serverLevel, pos, state, catalog);
            catalog.setDrawer(drawer, set);
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
            return InteractionResult.SUCCESS;
        }
        // A set drawer: opened or closed, whatever's held
        return toggle(state, level, pos, catalog, drawer);
    }

    // Sneaking with an item makes vanilla skip a block's own right-click (the item gets it instead): on a catalog's
    // drawer front the catalog must get it, to set the drawer to that item (CardCatalogEvents)
    public static boolean wantsSneakClick(BlockState state, BlockHitResult hit)
    {
        return state.getBlock() instanceof CardCatalogBlock catalog && catalog.drawerAt(state, hit) >= 0;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (!(level.getBlockEntity(pos) instanceof CardCatalogBlockEntity catalog)) return InteractionResult.PASS;
        int drawer = drawerAt(state, hitResult);
        if (catalog.drawer(drawer) == null) return InteractionResult.PASS;
        // Sneak: clear the drawer (closing it if it's open)
        if (player.isShiftKeyDown())
        {
            if (catalog.isLocked()) return locked(level, player);
            if (level instanceof ServerLevel serverLevel)
            {
                if (state.getValue(OPEN) == drawer + 1) close(serverLevel, pos, state, catalog);
                catalog.setDrawer(drawer, null);
                level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        }
        return toggle(state, level, pos, catalog, drawer);
    }

    private static InteractionResult locked(Level level, Player player)
    {
        if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".library.locked"));
        return InteractionResult.SUCCESS;
    }

    // Open the drawer (closing whichever drawer was open in the library), or close it if it's the open one
    private static InteractionResult toggle(BlockState state, Level level, BlockPos pos, CardCatalogBlockEntity catalog, int drawer)
    {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        if (state.getValue(OPEN) == drawer + 1)
        {
            close(serverLevel, pos, state, catalog);
            return InteractionResult.SUCCESS;
        }
        LibraryControllerBlockEntity library = catalog.library(level, pos);
        DrawerEntry entry = catalog.drawer(drawer);
        catalog.check(serverLevel);
        if (library == null || entry == null || catalog.isMissing(drawer))
        {
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1f, 1f);
            return InteractionResult.SUCCESS;
        }
        // Only one drawer open in the whole library: the others close (with their sound)
        library.closeDrawers(serverLevel, pos);
        BlockState current = level.getBlockState(pos);
        if (current.getValue(OPEN) != 0) level.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.8f, 1f);
        level.setBlock(pos, current.setValue(OPEN, drawer + 1), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8f, 1f);
        library.select(serverLevel, entry);
        return InteractionResult.SUCCESS;
    }

    // Closes this catalog's open drawer; the library's lecterns go back to their pages
    private static void close(ServerLevel level, BlockPos pos, BlockState state, CardCatalogBlockEntity catalog)
    {
        level.setBlock(pos, state.setValue(OPEN, 0), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.8f, 1f);
        LibraryControllerBlockEntity library = catalog.library(level, pos);
        if (library != null) library.clearSelection(level);
    }
}
