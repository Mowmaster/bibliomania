package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.fluids.CauldronFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

// The Book of Fluids. Amounts in mB (1000 = a bucket); a water bottle is 250mB.
// - Placed: right-click with a filled bucket (or tank) stores it, with an empty one takes a bucketful out;
//   right-click with a water bottle / glass bottle stores / takes 250mB of water
// - Held, while activated (sneak + right-click or the activation key; see StorageBookItem): right-click a fluid
//   source picks it up (like a bucket); right-click a block puts a bucketful there; right-click a cauldron: an empty
//   one is filled, a full one emptied into the book, a partly filled one gets a bottle's worth.
//   Not activated, it's placed like any book.
// - Inventory, while activated, either way round: left-click with a filled container stores it, right-click with an
//   empty one fills it
public class FluidBookBehaviour implements StorageBookBehaviour
{
    public static final int BOTTLE = 250;
    public static final int BUCKET = 1000;

    // Hand and inventory actions only while activated (see StorageBookItem)
    @Override
    public boolean usesActivation()
    {
        return true;
    }

    // ---- Placed ----

    @Override
    public InteractionResult useItemOnPlaced(ItemStack held, StorageBookBlockEntity book, Level level, BlockPos pos, Player player, InteractionHand hand)
    {
        ResourceHandler<FluidResource> storage = book.storage(TransportTypes.FLUIDS.get());
        if (isWaterBottle(held) || held.is(Items.GLASS_BOTTLE))
        {
            if (!level.isClientSide())
            {
                ItemStack result = useBottle(held, storage);
                if (result != null)
                {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, result));
                    bottleSound(level, pos, result.is(Items.GLASS_BOTTLE));
                }
            }
            return InteractionResult.SUCCESS;
        }
        // Buckets, tanks: NeoForge's helper fills or empties the held item (whichever it can) and plays the sound
        if (TransportTypes.FLUIDS.get().getItemHandler(ItemAccess.forPlayerInteraction(player, hand)) == null)
        {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) FluidUtil.interactWithFluidHandler(player, hand, pos, storage, null);
        return InteractionResult.SUCCESS;
    }

    // ---- Held ----

    // Active book, right-click on a block: go on to useHeld (like right-clicking the air)
    @Override
    public @Nullable InteractionResult useOnHeld(UseOnContext context)
    {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useHeld(Level level, Player player, InteractionHand hand)
    {
        ItemStack held = player.getItemInHand(hand);
        // 1. A fluid source in reach: pick it up (like a bucket: the ray stops at source blocks)
        BlockHitResult fluidHit = StorageBookItem.rayTrace(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (fluidHit.getType() == HitResult.Type.BLOCK && level.getFluidState(fluidHit.getBlockPos()).isSource()
                && level.mayInteract(player, fluidHit.getBlockPos()))
        {
            if (!level.isClientSide())
            {
                BookStorage storage = BookStorage.open(held);
                if (storage != null) FluidUtil.tryPickupFluid(storage.handler(TransportTypes.FLUIDS.get()), player, level, fluidHit.getBlockPos(), null);
            }
            return InteractionResult.SUCCESS;
        }
        // 2. A block: a cauldron is filled / emptied; anything else gets a bucketful placed against it
        BlockHitResult blockHit = StorageBookItem.rayTrace(level, player, ClipContext.Fluid.NONE);
        if (blockHit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        BlockPos pos = blockHit.getBlockPos();
        if (!level.mayInteract(player, pos)) return InteractionResult.PASS;
        if (!level.isClientSide())
        {
            BookStorage storage = BookStorage.open(held);
            if (storage == null) return InteractionResult.PASS;
            ResourceHandler<FluidResource> fluids = storage.handler(TransportTypes.FLUIDS.get());
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.CAULDRON) || CauldronFluidContent.getForBlock(state.getBlock()) != null)
            {
                useCauldron((ServerLevel) level, pos, state, fluids);
            }
            else
            {
                // Into the clicked block's space if it can be replaced (tall grass...), otherwise the space next to it
                BlockPos target = state.canBeReplaced() ? pos : pos.relative(blockHit.getDirection());
                FluidUtil.tryPlaceFluid(fluids, player, level, target, false, null);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Cauldrons (CauldronFluidContent is NeoForge's table of cauldron block <-> fluid, levels and amount):
    // empty -> filled from the book; full -> emptied into the book; partly filled -> a bottle's worth more
    private static void useCauldron(ServerLevel level, BlockPos pos, BlockState state, ResourceHandler<FluidResource> book)
    {
        FluidResource stored = book.getResource(0);
        CauldronFluidContent content = CauldronFluidContent.getForBlock(state.getBlock());
        if (state.is(Blocks.CAULDRON) || content == null || content.fluid == Fluids.EMPTY)
        {
            if (stored.isEmpty()) return;
            CauldronFluidContent fill = CauldronFluidContent.getForFluid(stored.getFluid());
            if (fill == null || !extractExactly(book, stored, fill.totalAmount)) return;
            BlockState filled = fill.block.defaultBlockState();
            if (fill.levelProperty != null) filled = filled.setValue(fill.levelProperty, fill.maxLevel);
            level.setBlock(pos, filled, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
            return;
        }
        int current = content.currentLevel(state);
        FluidResource inside = FluidResource.of(content.fluid);
        if (current >= content.maxLevel)
        {
            // Full: all of it into the book, if it all fits
            try (Transaction transaction = Transaction.openRoot())
            {
                if (book.insert(inside, content.totalAmount, transaction) < content.totalAmount) return;
                transaction.commit();
            }
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
        }
        else if (content.levelProperty != null && stored.equals(inside) && extractExactly(book, inside, BOTTLE))
        {
            // Partly filled: one more level, a bottle's worth (yes, this can make water; we don't mind)
            level.setBlock(pos, state.setValue(content.levelProperty, current + 1), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    // Takes exactly `amount` out, or nothing
    private static boolean extractExactly(ResourceHandler<FluidResource> handler, FluidResource resource, int amount)
    {
        try (Transaction transaction = Transaction.openRoot())
        {
            if (handler.extract(resource, amount, transaction) < amount) return false;
            transaction.commit();
            return true;
        }
    }

    // ---- Inventory ----

    @Override
    public boolean clickedWith(ItemStack book, ItemStack cursor, Slot slot, ClickAction action, Player player)
    {
        if (cursor.isEmpty()) return false;
        BookStorage storage = BookStorage.open(book);
        if (storage == null) return false;
        ResourceHandler<FluidResource> fluids = storage.handler(TransportTypes.FLUIDS.get());
        boolean store = action == ClickAction.PRIMARY;

        // Bottles: the result goes onto the cursor if the cursor had just the one, otherwise into the inventory
        if (store ? isWaterBottle(cursor) : cursor.is(Items.GLASS_BOTTLE))
        {
            if (!player.level().isClientSide())
            {
                ItemStack result = useBottle(cursor, fluids);
                if (result != null)
                {
                    cursor.shrink(1);
                    if (cursor.isEmpty()) player.containerMenu.setCarried(result);
                    else StorageInteractions.giveToPlayer(player, result);
                    bottleSound(player.level(), player.blockPosition(), result.is(Items.GLASS_BOTTLE));
                }
            }
            return true;
        }
        return moveWithContainer(fluids, StorageBookBehaviours.cursorAccess(player), store, player);
    }

    @Override
    public boolean clickedOnto(ItemStack book, Slot slot, ClickAction action, Player player)
    {
        ItemStack target = slot.getItem();
        if (target.isEmpty()) return false;
        BookStorage storage = BookStorage.open(book);
        if (storage == null) return false;
        ResourceHandler<FluidResource> fluids = storage.handler(TransportTypes.FLUIDS.get());
        boolean store = action == ClickAction.PRIMARY;

        if (store ? isWaterBottle(target) : target.is(Items.GLASS_BOTTLE))
        {
            if (!player.level().isClientSide())
            {
                ItemStack result = useBottle(target, fluids);
                if (result != null)
                {
                    target.shrink(1);
                    if (target.isEmpty()) slot.set(result);
                    else StorageInteractions.giveToPlayer(player, result);
                    slot.setChanged();
                    bottleSound(player.level(), player.blockPosition(), result.is(Items.GLASS_BOTTLE));
                }
            }
            return true;
        }
        if (player.level().isClientSide())
        {
            return TransportTypes.FLUIDS.get().getItemHandler(ItemAccess.forStack(target.copy())) != null;
        }
        return StorageBookBehaviours.withSlotAccess(player, slot, access -> moveWithContainer(fluids, access, store, player));
    }

    // A fluid container (bucket, tank...): store = everything in it into the book; otherwise fill it from the book
    // (a bucket takes exactly 1000). The container is swapped through its ItemAccess (bucket -> empty bucket).
    private static boolean moveWithContainer(ResourceHandler<FluidResource> book, ItemAccess container, boolean store, Player player)
    {
        ResourceHandler<FluidResource> item = TransportTypes.FLUIDS.get().getItemHandler(container);
        if (item == null) return false;
        if (!player.level().isClientSide())
        {
            int moved = store
                    ? StorageBookBehaviours.move(TransportTypes.FLUIDS.get(), item, book, Integer.MAX_VALUE)
                    : StorageBookBehaviours.move(TransportTypes.FLUIDS.get(), book, item, BUCKET);
            if (moved > 0)
            {
                player.level().playSound(null, player.blockPosition(), store ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL,
                        SoundSource.PLAYERS, 0.6f, 1f);
            }
        }
        return true;
    }

    // ---- Bottles (250mB of water) ----

    private static boolean isWaterBottle(ItemStack stack)
    {
        if (!stack.is(Items.POTION)) return false;
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        return potion != null && potion.is(Potions.WATER);
    }

    // A water bottle stores 250mB (returns a glass bottle); a glass bottle takes 250mB of water (returns a water
    // bottle). null if it can't (the book is full, or doesn't hold enough water).
    private static @Nullable ItemStack useBottle(ItemStack bottle, ResourceHandler<FluidResource> book)
    {
        FluidResource water = FluidResource.of(Fluids.WATER);
        try (Transaction transaction = Transaction.openRoot())
        {
            if (isWaterBottle(bottle))
            {
                if (book.insert(water, BOTTLE, transaction) < BOTTLE) return null;
                transaction.commit();
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            if (book.extract(water, BOTTLE, transaction) < BOTTLE) return null;
            transaction.commit();
            return PotionContents.createItemStack(Items.POTION, Potions.WATER);
        }
    }

    private static void bottleSound(Level level, BlockPos pos, boolean emptied)
    {
        level.playSound(null, pos, emptied ? SoundEvents.BOTTLE_EMPTY : SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 1f);
    }
}
