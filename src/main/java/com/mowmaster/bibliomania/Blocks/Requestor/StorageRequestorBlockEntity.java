package com.mowmaster.bibliomania.Blocks.Requestor;

import com.mowmaster.mowlib.blockentity.CBSyncedBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryDeposit;
import com.mowmaster.bibliomania.Blocks.Library.LibraryLink;
import com.mowmaster.bibliomania.Blocks.Library.LibraryMember;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import com.mowmaster.mowlib.paging.IPageHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// A Patterned Storage Requestor: its 9-slot pattern, its library link and lock. It holds no items.
public class StorageRequestorBlockEntity extends CBSyncedBlockEntity implements LibraryMember, PatternHolder, IPageHolder
{
    public static final int SLOTS = 9;
    // The page keys change a count by this much, sneaking by SNEAK_STEP
    private static final int SNEAK_STEP = 8;

    private final RequestPattern pattern = new RequestPattern(SLOTS, RequestPattern.MAX_COUNT, this::sync);
    private final LibraryLink libraryLink = new LibraryLink(this::sync);

    public StorageRequestorBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.STORAGE_REQUESTOR.get(), pos, state);
    }

    @Override
    public RequestPattern pattern()
    {
        return pattern;
    }

    // ---- The pulse ----

    // Takes the whole pattern from the library and puts it into the container in front (its item handler, from the
    // side facing this block); with no container there, it's dropped out of the front like a Dropper does. All of it
    // or nothing: one transaction, only committed if every slot's items were found and (with a container) fit.
    // Returns whether it did.
    public boolean request(ServerLevel level, BlockState state)
    {
        LibraryControllerBlockEntity library = library(level, worldPosition);
        if (library == null || pattern.isEmpty()) return false;
        Direction facing = state.getValue(DispenserBlock.FACING);
        ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(facing), facing.getOpposite());
        List<ItemStack> dropped = new ArrayList<>();
        try (Transaction transaction = Transaction.openRoot())
        {
            for (int slot = 0; slot < SLOTS; slot++)
            {
                ItemStack wanted = pattern.item(slot);
                int count = pattern.count(slot);
                if (wanted.isEmpty()) continue;
                // The real items taken (fuzzy matching can find several, worn differently)
                List<ItemStack> got = LibraryDeposit.take(library, wanted, count, pattern.exact(), worldPosition, transaction);
                if (LibraryDeposit.total(got) < count) return false;
                for (ItemStack stack : got)
                {
                    if (target == null) dropped.add(stack);
                    else if (ResourceHandlerUtil.insertStacking(target, ItemResource.of(stack), stack.getCount(), transaction) < stack.getCount()) return false;
                }
            }
            transaction.commit();
        }
        for (ItemStack stack : dropped) drop(level, stack, facing);
        return true;
    }

    // Out of the front like a Dropper's items (in stacks no bigger than the item stacks to)
    private void drop(ServerLevel level, ItemStack stack, Direction facing)
    {
        Vec3 front = new Vec3(worldPosition.getX() + 0.5 + 0.7 * facing.getStepX(),
                worldPosition.getY() + 0.5 + 0.7 * facing.getStepY(),
                worldPosition.getZ() + 0.5 + 0.7 * facing.getStepZ());
        while (!stack.isEmpty())
        {
            DefaultDispenseItemBehavior.spawnItem(level, stack.split(stack.getMaxStackSize()), 6, facing, front);
        }
    }

    // ---- The page keys: the count of the cell looked at ----

    // MowLib's page keys call this without saying who pressed them: the server works out who's looking at which cell
    @Override
    public void turnPage(int direction)
    {
        if (isLocked() || !(level instanceof ServerLevel serverLevel)) return;
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        for (Player player : serverLevel.players())
        {
            if (player.distanceToSqr(centre) > 64) continue;
            // pick: what the player's crosshair is on, worked out on the server (blocks only: false = no fluids)
            if (!(player.pick(player.blockInteractionRange(), 1f, false) instanceof BlockHitResult hit) || !hit.getBlockPos().equals(worldPosition)) continue;
            int cell = PatternDispenserBlock.cellAt(getBlockState(), hit, this);
            if (cell >= 0 && pattern.adjust(cell, direction * (player.isShiftKeyDown() ? SNEAK_STEP : 1))) return;
        }
    }

    // Not really paged: the keys only change counts (turnPage)
    @Override public int getPageCount() { return 1; }
    @Override public int getPage() { return 0; }
    @Override public void setPage(int page) {}

    // ---- Library (LibraryMember) ----

    @Override public @Nullable BlockPos getLibrary() { return libraryLink.library(); }
    @Override public void setLibrary(@Nullable BlockPos controller) { libraryLink.setLibrary(controller); }
    @Override public boolean isLocked() { return libraryLink.locked(); }
    @Override public void setLocked(boolean locked) { libraryLink.setLocked(locked); }

    // ---- Saving and syncing ----

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        pattern.save(output);
        libraryLink.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        pattern.load(input);
        libraryLink.load(input);
    }

}
