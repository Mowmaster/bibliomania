package com.mowmaster.bibliomania.Blocks.Lectern;

import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockEntity;
import com.mowmaster.bibliomania.Registry.DeferredRegisterTileBlocks;
import com.mowmaster.mowlib.BlockEntities.MowLibBaseFilterableBlock;
import com.mowmaster.mowlib.api.TransportAndStorage.IFilterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;

public class BaseLecternBlock extends MowLibBaseFilterableBlock {
    public BaseLecternBlock(Properties p_152915_) {
        super(p_152915_);
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
                Direction blockDirection = p_60503_.getValue(HorizontalDirectionalBlock.FACING);
                Direction direction = p_60508_.getDirection();
                BlockPos blockpos = p_60508_.getBlockPos().relative(direction);
                Vec3 vec3 = p_60508_.getLocation().subtract((double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ());
                int slotSwap = getHitValue(vec3,blockDirection);
                if(slotSwap != 0)
                {
                    blockBookEntity.iterateCurrentSlot(slotSwap);
                }
                else if(itemInOffHand.getItem() instanceof IFilterItem)
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
                else if(itemInHand.isEmpty() && p_60506_.isShiftKeyDown())
                {
                    putInPlayersHandAndRemove(p_60503_,p_60504_,p_60505_,p_60506_,p_60507_);
                }
                else return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.SUCCESS;
    }

    private static void putInPlayersHandAndRemove(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        //ItemStack backpack = WorldHelper.getBlockEntity(world, pos, BackpackBlockEntity.class).map(te -> te.getBackpackWrapper().getBackpack()).orElse(ItemStack.EMPTY);
        ItemStack book = new ItemStack(DeferredRegisterTileBlocks.TILE_BOOK_STARTER.get().asItem());
        if(level.getBlockEntity(pos) instanceof BaseBookBlockEntity bookBlockEntity)
        {
            CompoundTag bookTag = book.getOrCreateTag();
            //_bookstoragelist
            book.setTag(bookBlockEntity.saveToItem(bookTag));

            player.setItemInHand(hand, book.copy());
            player.getCooldowns().addCooldown(book.getItem(), 5);
            level.removeBlock(pos, false);

            SoundType soundType = state.getSoundType();
            level.playSound(null, pos, soundType.getBreakSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        }
    }

    private static int getHitValue(Vec3 vec3, Direction blockPlacementDirection) {
        /*System.out.println("BlockDirection: " + blockPlacementDirection.getName());
        System.out.println("HitXCord: " + vec3.x);
        System.out.println("HitYCord: " + vec3.y);
        System.out.println("HitZCord: " + vec3.z);*/
        int returner = 0;
        switch (blockPlacementDirection) {
            case NORTH:
                if(vec3.z>= 0.75D && vec3.z < 0.90D)
                {
                    if(vec3.x>= 0.00D && vec3.x < 0.125D)
                    {
                        //System.out.println("NORTH Direction - Back Arrow");System.out.println("Trigger N1");
                        returner = -1;
                    }
                    else if(vec3.x>= 0.85D && vec3.x < 1.00D)
                    {

                        //System.out.println("Trigger N2");System.out.println("NORTH Direction - Forward Arrow");
                        returner = 1;

                    }
                }
                break;
            case SOUTH:
                if(vec3.z>= 0.125D && vec3.z < 0.25D)
                {
                    if(vec3.x>= 0.85D && vec3.x < 1.00D)
                    {
                        //System.out.println("Trigger S1");System.out.println("SOUTH Direction - Back Arrow");
                        returner = -1;
                    }
                    else if(vec3.x>= 0.00D && vec3.x < 0.125D)
                    {
                        //System.out.println("Trigger S2");System.out.println("SOUTH Direction - Forward Arrow");
                        returner = 1;
                    }
                }
                break;
            case WEST:
                if(vec3.x>= 0.75D && vec3.x < 0.90D)
                {
                    if(vec3.z>= 0.85D && vec3.z < 1.00D)
                    {
                        //System.out.println("Trigger W1");System.out.println("West Direction - Back Arrow");
                        returner = -1;
                    }
                    else if(vec3.z>= 0.00D && vec3.z < 0.13D)
                    {
                        //System.out.println("Trigger W2");System.out.println("West Direction - Forward Arrow");
                        returner = 1;
                    }
                }
                break;
            case EAST:
                if(vec3.x>= 0.125D && vec3.x < 0.25D)
                {
                    if(vec3.z>= 0.00D && vec3.z < 0.125D)
                    {
                        //System.out.println("Trigger E1");System.out.println("EAST Direction - Back Arrow");
                        returner = -1;
                    }
                    else if(vec3.z>= 0.85D && vec3.z < 1.0D)
                    {
                        //System.out.println("Trigger E2");System.out.println("EAST Direction - Forward Arrow");
                        returner = 1;
                    }
                }
                break;
            default:
                returner = 0;
                break;
        }

        return returner;
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
                BlockState changed = p_60510_.getBlockState(p_60513_);
                if (DiodeBlock.isDiode(changed)) {
                    if(changed.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
                    {
                        if(p_60513_.relative(changed.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()).equals(p_60511_))
                        {
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
}
