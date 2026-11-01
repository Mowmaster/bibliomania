package com.mowmaster.bibliomania.Blocks.Library;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.jetbrains.annotations.Nullable;

// Library priority: which shelves (and which books on a shelf) a library fills first. Higher goes first; equal
// priorities keep their usual order (the walk order for shelves, slot order for books).
// - Shelves: Bookcases (0 to MAX_CASE, default 0) and Chiseled Bookshelves (default CHISELED_DEFAULT). A chiseled
//   shelf is a vanilla block, so its priority is kept in its block entity's NeoForge persistent data (saved with it).
// - Books: placed storage books (item books, Books of Fluids/Experience/Energy; 0 to MAX_BOOK, default 0). A book's
//   priority only orders it among the books on its own Bookcase; it's kept on the item (bibliomania:priority).
// Set with the Priority Scroll (right-click up, left-click down, sneaking by 10; the page keys while holding it).
public final class LibraryPriority
{
    public static final int MAX_CASE = 1000;
    public static final int MAX_BOOK = 100;
    public static final int CHISELED_DEFAULT = 100;
    private static final String CHISELED_KEY = "bibliomania_priority";

    private LibraryPriority() {}

    // A block entity with a priority of its own (Bookcases, placed storage books)
    public interface Holder
    {
        int getPriority();

        void setPriority(int priority);

        int maxPriority();
    }

    // A shelf's priority (0 for anything else)
    public static int of(@Nullable BlockEntity blockEntity)
    {
        if (blockEntity instanceof Holder holder) return holder.getPriority();
        if (blockEntity instanceof ChiseledBookShelfBlockEntity shelf) return shelf.getPersistentData().getIntOr(CHISELED_KEY, CHISELED_DEFAULT);
        return 0;
    }

    // A book item's priority
    public static int ofBook(ItemStack book)
    {
        Integer priority = book.get(DataComponentRegistry.PRIORITY.get());
        return priority == null ? 0 : priority;
    }

    // Whether the block at pos has a priority to change
    public static boolean hasPriority(Level level, BlockPos pos)
    {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Holder || blockEntity instanceof ChiseledBookShelfBlockEntity;
    }

    // Raises (or with a negative delta lowers) the priority of the block at pos, within its range, and tells the
    // player the new one. Returns false if the block has none.
    public static boolean adjust(ServerLevel level, BlockPos pos, int delta, Player player)
    {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!hasPriority(level, pos)) return false;
        if (blockEntity instanceof LibraryLockable lockable && lockable.isLocked())
        {
            player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".library.locked"));
            return true;
        }
        int max;
        int changed;
        if (blockEntity instanceof Holder holder)
        {
            max = holder.maxPriority();
            changed = Mth.clamp(holder.getPriority() + delta, 0, max);
            holder.setPriority(changed);
        }
        else
        {
            ChiseledBookShelfBlockEntity shelf = (ChiseledBookShelfBlockEntity) blockEntity;
            max = MAX_CASE;
            changed = Mth.clamp(of(shelf) + delta, 0, max);
            shelf.getPersistentData().putInt(CHISELED_KEY, changed);
            shelf.setChanged();
        }
        player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".priority", changed, max));
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, delta > 0 ? 1.4f : 0.8f);
        return true;
    }

    // The step for a click or key press: 10 while sneaking, else 1
    public static int step(Player player)
    {
        return player.isShiftKeyDown() ? 10 : 1;
    }
}
