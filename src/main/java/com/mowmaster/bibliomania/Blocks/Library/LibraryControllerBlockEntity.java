package com.mowmaster.bibliomania.Blocks.Library;

import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.Blocks.Lectern.ReadingLecternBlockEntity;
import com.mowmaster.bibliomania.Blocks.libBlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// A Library Controller: the core of a library. Its members are found by walking from the controller through
// touching library members (LibraryMember: Bookcases, Reading Lecterns...), one block to the next, and from each block
// linked with the Librarian's Quill (with whatever touches those). Blocks the Quill removed are skipped (and not walked
// through). A member already in another working library stays in that one.
// - The walk runs on the server every SCAN_TICKS (and straight after a Quill change), at most MAX_MEMBERS blocks.
//   Members are told which library they're in (LibraryMember#setLibrary); ones that left are told they're out.
// - Everything else asks the controller: its bookcases in walk order (the order a lectern pages through them), its
//   lecterns. Only member counts go to clients (for the Reading Goggles), never the lists.
// - Linked and removed positions, and the lock, are saved.
public class LibraryControllerBlockEntity extends BlockEntity implements LibraryLockable
{
    public static final int MAX_MEMBERS = 1024;
    // How far a linked block may be (each way)
    public static final int LINK_RANGE = 64;
    private static final int SCAN_TICKS = 40;

    private static final String TAG_LINKED = "linked";
    private static final String TAG_REMOVED = "removed";
    private static final String TAG_LOCKED = "locked";
    private static final String TAG_BOOKCASES = "bookcases";
    private static final String TAG_LECTERNS = "lecterns";

    // LinkedHashSet: no duplicates, kept in the order they were added
    private final Set<BlockPos> linked = new LinkedHashSet<>();
    private final Set<BlockPos> removed = new LinkedHashSet<>();
    private boolean locked;
    // Server: the members found by the last walk, in walk order
    private List<BlockPos> members = List.of();
    private Set<BlockPos> memberSet = Set.of();
    private long nextScan;
    // Synced: how many of each
    private int bookcaseCount;
    private int lecternCount;

    public LibraryControllerBlockEntity(BlockPos pos, BlockState state)
    {
        super(libBlockEntityRegistry.LIBRARY_CONTROLLER.get(), pos, state);
    }

    // ---- Members ----

    public boolean isMember(BlockPos pos)
    {
        return memberSet.contains(pos);
    }

    public List<BlockPos> members()
    {
        return members;
    }

    // The library's Bookcases, in walk order (those loaded)
    public List<BookcaseBlockEntity> bookcases()
    {
        List<BookcaseBlockEntity> found = new ArrayList<>();
        if (level == null) return found;
        for (BlockPos pos : members)
        {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BookcaseBlockEntity shelf) found.add(shelf);
        }
        return found;
    }

    public List<ReadingLecternBlockEntity> lecterns()
    {
        List<ReadingLecternBlockEntity> found = new ArrayList<>();
        if (level == null) return found;
        for (BlockPos pos : members)
        {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ReadingLecternBlockEntity lectern) found.add(lectern);
        }
        return found;
    }

    public int getBookcaseCount()
    {
        return bookcaseCount;
    }

    public int getLecternCount()
    {
        return lecternCount;
    }

    // ---- The walk (server) ----

    public void serverTick(ServerLevel level)
    {
        if (level.getGameTime() >= nextScan) scan(level);
    }

    public void scan(ServerLevel level)
    {
        nextScan = level.getGameTime() + SCAN_TICKS;
        Set<BlockPos> found = new LinkedHashSet<>();
        // ArrayDeque: a queue; walking breadth-first finds the nearest members first
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) queue.add(worldPosition.relative(direction));
        queue.addAll(linked);
        while (!queue.isEmpty() && found.size() < MAX_MEMBERS)
        {
            BlockPos pos = queue.poll();
            if (pos.equals(worldPosition) || found.contains(pos) || removed.contains(pos) || !level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof LibraryMember member)) continue;
            if (inOtherLibrary(level, member, pos)) continue;
            found.add(pos.immutable());
            for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }

        // Tell the ones that left, then the ones in it
        for (BlockPos old : members)
        {
            if (!found.contains(old) && level.isLoaded(old) && level.getBlockEntity(old) instanceof LibraryMember member
                    && worldPosition.equals(member.getLibrary()))
            {
                member.setLibrary(null);
            }
        }
        int bookcases = 0, lecterns = 0;
        for (BlockPos pos : found)
        {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof LibraryMember member) member.setLibrary(worldPosition);
            if (blockEntity instanceof BookcaseBlockEntity) bookcases++;
            if (blockEntity instanceof ReadingLecternBlockEntity) lecterns++;
        }
        members = List.copyOf(found);
        memberSet = Set.copyOf(found);
        if (bookcases != bookcaseCount || lecterns != lecternCount)
        {
            bookcaseCount = bookcases;
            lecternCount = lecterns;
            sync();
        }
    }

    // In another library whose controller is still there (and still has it)
    private boolean inOtherLibrary(ServerLevel level, LibraryMember member, BlockPos pos)
    {
        BlockPos other = member.getLibrary();
        if (other == null || other.equals(worldPosition) || !level.isLoaded(other)) return false;
        return level.getBlockEntity(other) instanceof LibraryControllerBlockEntity controller && controller.isMember(pos);
    }

    // ---- The Librarian's Quill ----

    public enum LinkChange { LINKED, UNLINKED, REMOVED, RESTORED, TOO_FAR, NOT_A_MEMBER }

    // A Quill click on `pos`: a linked block is unlinked; a touching member is removed; a removed block comes back; any
    // other library block within LINK_RANGE is linked
    public LinkChange toggle(ServerLevel level, BlockPos pos)
    {
        BlockPos target = pos.immutable();
        LinkChange change;
        if (linked.remove(target)) change = LinkChange.UNLINKED;
        else if (removed.remove(target)) change = LinkChange.RESTORED;
        else if (memberSet.contains(target))
        {
            removed.add(target);
            change = LinkChange.REMOVED;
        }
        else if (!(level.getBlockEntity(target) instanceof LibraryMember)) return LinkChange.NOT_A_MEMBER;
        else if (Math.abs(target.getX() - worldPosition.getX()) > LINK_RANGE || Math.abs(target.getY() - worldPosition.getY()) > LINK_RANGE
                || Math.abs(target.getZ() - worldPosition.getZ()) > LINK_RANGE) return LinkChange.TOO_FAR;
        else
        {
            linked.add(target);
            change = LinkChange.LINKED;
        }
        setChanged();
        scan(level);
        return change;
    }

    // ---- Lock ----

    @Override
    public boolean isLocked()
    {
        return locked;
    }

    @Override
    public void setLocked(boolean newLocked)
    {
        locked = newLocked;
        sync();
    }

    // Broken: every member is told it's no longer in a library
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level == null) return;
        for (BlockPos member : members)
        {
            if (level.isLoaded(member) && level.getBlockEntity(member) instanceof LibraryMember libraryMember
                    && pos.equals(libraryMember.getLibrary()))
            {
                libraryMember.setLibrary(null);
            }
        }
    }

    // ---- Saving and syncing ----

    private void sync()
    {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        // listOf(): a codec for a list of positions
        output.store(TAG_LINKED, BlockPos.CODEC.listOf(), List.copyOf(linked));
        output.store(TAG_REMOVED, BlockPos.CODEC.listOf(), List.copyOf(removed));
        output.putBoolean(TAG_LOCKED, locked);
        output.putInt(TAG_BOOKCASES, bookcaseCount);
        output.putInt(TAG_LECTERNS, lecternCount);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        linked.clear();
        linked.addAll(input.read(TAG_LINKED, BlockPos.CODEC.listOf()).orElse(List.of()));
        removed.clear();
        removed.addAll(input.read(TAG_REMOVED, BlockPos.CODEC.listOf()).orElse(List.of()));
        locked = input.getBooleanOr(TAG_LOCKED, false);
        bookcaseCount = input.getIntOr(TAG_BOOKCASES, 0);
        lecternCount = input.getIntOr(TAG_LECTERNS, 0);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
