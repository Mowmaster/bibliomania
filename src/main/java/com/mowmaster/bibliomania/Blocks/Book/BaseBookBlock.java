package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.bibliomania.Registry.DeferredBlockEntityTypes;
import com.mowmaster.mowlib.MowLibUtils.MowLibCompoundTagUtils;
import com.mowmaster.mowlib.MowLibUtils.MowLibRedstoneUtils;
import com.mowmaster.mowlib.api.TransportAndStorage.IFilterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class BaseBookBlock extends Block implements SimpleWaterloggedBlock,EntityBlock
{
    public static final IntegerProperty BOOK_COVER = IntegerProperty.create("book_cover", 0, 1);
    public static final IntegerProperty BOOK_THICKNESS = IntegerProperty.create("book_thickness", 0, 5);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected final VoxelShape NORMAL;
    protected final VoxelShape ALTNORMAL;

    public BaseBookBlock(Properties p_152915_) {
        super(p_152915_);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, Boolean.valueOf(false)).setValue(BOOK_COVER, 0).setValue(BOOK_THICKNESS, 0).setValue(FACING, Direction.NORTH));
        this.NORMAL = Shapes.or(Block.box(-5.5D, 0.0D, 1.0D, 22.0D, 1.0D, 15.0D));
        this.ALTNORMAL = Shapes.or(Block.box(1.0D, 0.0D, -6.0D, 15.0D, 1.0D, 21.5D));
    }

    public VoxelShape getShape(BlockState p_152021_, BlockGetter p_152022_, BlockPos p_152023_, CollisionContext p_152024_) {
        Direction direction = p_152021_.getValue(FACING);
        switch(direction) {
            case NORTH:
                return this.NORMAL;
            case SOUTH:
                return this.NORMAL;
            case EAST:
                return this.ALTNORMAL;
            case WEST:
                return this.ALTNORMAL;
            default:
                return this.NORMAL;
        }
    }

    public BlockState updateShape(BlockState p_152036_, Direction p_152037_, BlockState p_152038_, LevelAccessor p_152039_, BlockPos p_152040_, BlockPos p_152041_) {
        if (p_152036_.getValue(WATERLOGGED)) {
            p_152039_.getFluidTicks();
            //.scheduleTick(p_152040_, Fluids.WATER, Fluids.WATER.getTickDelay(p_152039_))
        }

        return p_152037_ == p_152036_.getValue(FACING).getOpposite() && !p_152036_.canSurvive(p_152039_, p_152040_) ? Blocks.AIR.defaultBlockState() : super.updateShape(p_152036_, p_152037_, p_152038_, p_152039_, p_152040_, p_152041_);
    }

    public BlockState rotate(BlockState p_152033_, Rotation p_152034_) {
        return p_152033_.setValue(FACING, p_152034_.rotate(p_152033_.getValue(FACING)));
    }

    public BlockState mirror(BlockState p_152030_, Mirror p_152031_) {
        return p_152030_.rotate(p_152031_.getRotation(p_152030_.getValue(FACING)));
    }

    public FluidState getFluidState(BlockState p_152045_) {
        return p_152045_.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(p_152045_);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_152043_) {
        p_152043_.add(WATERLOGGED, FACING, BOOK_COVER, BOOK_THICKNESS);
    }

    public PushReaction getPistonPushReaction(BlockState p_152733_) {
        return PushReaction.IGNORE;
    }



    @Override
    public void setPlacedBy(Level p_49847_, BlockPos p_49848_, BlockState p_49849_, @Nullable LivingEntity p_49850_, ItemStack p_49851_) {
        //super.setPlacedBy(p_49847_, p_49848_, p_49849_, p_49850_, p_49851_);
        if(p_49850_ instanceof Player)
        {
            Player player = ((Player)p_49850_);

            if(p_49847_.getBlockEntity(p_49848_) instanceof BaseBookBlockEntity bookEntity)
            {
                int thickness = 0;
                int cover = MowLibCompoundTagUtils.readIntegerFromNBT(p_49851_.getOrCreateTag(),MODID + "_book_cover");
                p_49847_.setBlockAndUpdate(p_49848_,p_49849_.setValue(BOOK_THICKNESS,thickness).setValue(BOOK_COVER,cover).setValue(FACING,getHorizontalDirection(player)));

                //bookEntity
                //Need to set the storage somehow on block place
            }
        }
    }

    public Direction getHorizontalDirection(Player player) {
        return player == null ? Direction.NORTH : player.getDirection();
    }

    @Override
    public void attack(BlockState p_60499_, Level p_60500_, BlockPos p_60501_, Player p_60502_) {
        if(!p_60500_.isClientSide()) {
            if (!(p_60502_ instanceof FakePlayer)) {
                BlockEntity blockEntity = p_60500_.getBlockEntity(p_60501_);
                if (blockEntity instanceof BaseBookBlockEntity blockBookEntity) {
                    ItemStack itemInHand = p_60502_.getMainHandItem();
                    ItemStack itemInOffHand = p_60502_.getOffhandItem();

                    if(blockBookEntity.hasFilter() && itemInOffHand.is(com.mowmaster.mowlib.Registry.DeferredRegisterItems.TOOL_FILTERTOOL.get()))
                    {
                        ItemHandlerHelper.giveItemToPlayer(p_60502_,blockBookEntity.removeFilter(null));
                        blockBookEntity.actionOnFilterRemovedFromBlockEntity(1);
                    }
                    else if(blockBookEntity.hasItemFirst())
                    {
                        if(p_60502_.isShiftKeyDown())
                        {
                            ItemHandlerHelper.giveItemToPlayer(p_60502_,blockBookEntity.removeItemFromSlot(blockBookEntity.getCurrentSlot(),false));
                        }
                        else
                        {
                            ItemHandlerHelper.giveItemToPlayer(p_60502_,blockBookEntity.removeItemFromSlotCount(blockBookEntity.getCurrentSlot(),1,false));
                        }
                    }
                }
            }
        }

        super.attack(p_60499_, p_60500_, p_60501_, p_60502_);
    }

    @Override
    public InteractionResult use(BlockState p_60503_, Level p_60504_, BlockPos p_60505_, Player p_60506_, InteractionHand p_60507_, BlockHitResult p_60508_) {
        super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);

        ItemStack itemInHand = p_60506_.getMainHandItem();
        if(!p_60504_.isClientSide())
        {
            BlockEntity blockEntity = p_60504_.getBlockEntity(p_60505_);
            if(blockEntity instanceof BaseBookBlockEntity blockBookEntity)
            {
                ItemStack itemInOffHand = p_60506_.getOffhandItem();
                if(itemInOffHand.getItem() instanceof IFilterItem)
                {
                    if(blockBookEntity.attemptAddFilter(itemInOffHand,null)) {
                        return InteractionResult.SUCCESS;
                    }
                }
                else if(!itemInHand.isEmpty())
                {
                    ItemStack stackNotInsert = blockBookEntity.addItemStackToSlot(blockBookEntity.getCurrentSlot(),itemInHand,true);
                    if(itemInHand.getCount() > stackNotInsert.getCount())
                    {
                        int shrinkAmount = itemInHand.getCount() - blockBookEntity.addItemStackToSlot(blockBookEntity.getCurrentSlot(),itemInHand,false).getCount();
                        itemInHand.shrink(shrinkAmount);
                        return InteractionResult.SUCCESS;
                    }
                    else return InteractionResult.SUCCESS;
                }
                else if(itemInHand.isEmpty() && !p_60506_.isShiftKeyDown())
                {
                    blockBookEntity.iterateCurrentSlot(1);
                }
                else if(itemInHand.isEmpty() && p_60506_.isShiftKeyDown())
                {
                    blockBookEntity.iterateCurrentSlot(-1);
                }
                else return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side) {
        return true;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState p_60457_) {
        //super.hasAnalogOutputSignal(p_60457_);
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState p_60487_, Level p_60488_, BlockPos p_60489_) {
        //super.getAnalogOutputSignal(p_60487_, p_60488_, p_60489_);
        if(p_60488_.getBlockEntity(p_60489_) instanceof BaseBookBlockEntity bookBlockEntity)
        {
            return bookBlockEntity.getCurrentSlot();
        }

        return 0;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, @javax.annotation.Nullable Direction direction) {
        return true;
    }

    @Override
    public void neighborChanged(BlockState p_60509_, Level p_60510_, BlockPos p_60511_, Block p_60512_, BlockPos p_60513_, boolean p_60514_) {
        if(!p_60510_.isClientSide())
        {
            if(p_60510_.getBlockEntity(p_60511_) instanceof BaseBookBlockEntity bookBlockEntity)
            {
                System.out.println("Triggered");
                BlockState changed = p_60510_.getBlockState(p_60513_);
                if (DiodeBlock.isDiode(changed)) {
                    System.out.println("IS DIODE");
                    if(changed.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
                    {
                        System.out.println("HasProp");
                        if(p_60513_.relative(changed.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()).equals(p_60511_))
                        {
                            System.out.println("Prop Matches Direction");
                            bookBlockEntity.triggerNeighborChange();
                        }
                    }
                }
                else
                {
                    bookBlockEntity.triggerNeighborChange();
                }
            }
        }
        super.neighborChanged(p_60509_, p_60510_, p_60511_, p_60512_, p_60513_, p_60514_);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter world, BlockPos pos, Player player) {
        return super.canHarvestBlock(state, world, pos, player);
    }

    @Override
    public void playerDestroy(Level p_49827_, Player p_49828_, BlockPos p_49829_, BlockState p_49830_, @javax.annotation.Nullable BlockEntity p_49831_, ItemStack p_49832_) {
        /*if(!p_49827_.isClientSide())
        {
            if (p_49830_.getBlock() instanceof BasePedestalBlock) {
                if (!p_49827_.isClientSide && !p_49828_.isCreative()) {
                    ItemStack itemstack = new ItemStack(this);
                    int getColor = MowLibColorReference.getColorFromStateInt(p_49830_);
                    ItemStack newStack = MowLibColorReference.addColorToItemStack(itemstack,getColor);
                    newStack.setCount(1);
                    ItemEntity itementity = new ItemEntity(p_49827_, (double)p_49829_.getX() + 0.5D, (double)p_49829_.getY() + 0.5D, (double)p_49829_.getZ() + 0.5D, newStack);
                    itementity.setDefaultPickUpDelay();
                    p_49827_.addFreshEntity(itementity);
                }
            }
        }*/
        super.playerDestroy(p_49827_, p_49828_, p_49829_, p_49830_, p_49831_, p_49832_);
        p_49827_.removeBlock(p_49829_,false);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level world, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {

        if(player instanceof FakePlayer) {
            return false;
        }

        if (player.isCreative()) {
            if (player.getOffhandItem().getItem().equals(com.mowmaster.mowlib.Registry.DeferredRegisterItems.TOOL_DEVTOOL.get()))
                return willHarvest || super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid);
            else
                attack(state, world, pos, player);

            return false;
        }

        return willHarvest || super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid);
    }

    @Override
    public void onRemove(BlockState p_60515_, Level p_60516_, BlockPos p_60517_, BlockState p_60518_, boolean p_60519_) {
        if(p_60515_.getBlock() != p_60518_.getBlock())
        {
            BlockEntity blockEntity = p_60516_.getBlockEntity(p_60517_);
                if(blockEntity instanceof BaseBookBlockEntity bookEntity) {
                p_60516_.updateNeighbourForOutputSignal(p_60517_,p_60518_.getBlock());
            }
            p_60516_.removeBlock(p_60517_,false);
            super.onRemove(p_60515_, p_60516_, p_60517_, p_60518_, p_60519_);
        }
    }

    public RenderShape getRenderShape(BlockState p_50950_) {
        return RenderShape.MODEL;
    }


    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return DeferredBlockEntityTypes.BOOK_STARTER.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : (level0, pos, state0, blockEntity) -> ((BaseBookBlockEntity) blockEntity).tick();
    }
}
