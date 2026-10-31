package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// The behaviour for each transport type (by the type's id, e.g. "mowlib:fluids"). Bibliomania's are registered
// below; another mod registers its own with register(...) during common setup.
public final class StorageBookBehaviours
{
    private StorageBookBehaviours() {}

    private static final Map<Identifier, StorageBookBehaviour> BEHAVIOURS = new HashMap<>();

    // For any type without its own behaviour: fill from / empty into items that hold that type
    public static final StorageBookBehaviour GENERIC = new Generic();

    static
    {
        register(TransportTypes.FLUIDS.getId(), new FluidBookBehaviour());
        register(TransportTypes.EXPERIENCE.getId(), new ExperienceBookBehaviour());
        register(TransportTypes.ENERGY.getId(), new EnergyBookBehaviour());
    }

    public static void register(Identifier typeId, StorageBookBehaviour behaviour)
    {
        BEHAVIOURS.put(typeId, behaviour);
    }

    public static StorageBookBehaviour get(TransportType<?> type)
    {
        return BEHAVIOURS.getOrDefault(type.getId(), GENERIC);
    }

    // ---- Reaching items in slots ----

    // Runs `action` on the item in `slot` as an ItemAccess (what NeoForge's item capabilities need, so a bucket can
    // become an empty bucket). For the player's own inventory this is the real slot (anything that doesn't fit goes
    // to the rest of the inventory). For other containers the item is copied into a 2-slot holder, the action runs
    // there, and the result goes back into the slot (the second slot catches anything extra, given to the player).
    public static <T> T withSlotAccess(Player player, Slot slot, Function<ItemAccess, T> action)
    {
        if (slot.container == player.getInventory())
        {
            return action.apply(ItemAccess.forPlayerSlot(player, slot.getContainerSlot()));
        }
        NonNullList<ItemStack> copy = NonNullList.withSize(2, ItemStack.EMPTY);
        copy.set(0, slot.getItem().copy());
        ItemStacksResourceHandler holder = new ItemStacksResourceHandler(copy);
        T result = action.apply(ItemAccess.forHandlerIndex(holder, 0));
        slot.set(holder.getResource(0).toStack(holder.getAmountAsInt(0)));
        StorageInteractions.giveToPlayer(player, holder.getResource(1).toStack(holder.getAmountAsInt(1)));
        return result;
    }

    // The cursor's item as an ItemAccess
    public static ItemAccess cursorAccess(Player player)
    {
        return ItemAccess.forPlayerCursor(player, player.containerMenu);
    }

    // Moves up to `amount` between two handlers of one type, committed. Returns how much moved.
    @SuppressWarnings("unchecked")
    public static <R extends Resource> int move(TransportType<R> type, ResourceHandler<?> from, ResourceHandler<?> to, int amount)
    {
        try (Transaction transaction = Transaction.openRoot())
        {
            int moved = type.transfer((ResourceHandler<R>) from, (ResourceHandler<R>) to, amount, transaction);
            if (moved > 0) transaction.commit();
            return moved;
        }
    }

    // ---- The generic behaviour ----

    // Items that hold the book's type (found through TransportType#getItemHandler, e.g. a battery for energy):
    //   placed book: right-click with one fills the book from it; sneak + right-click empties the book into it
    //   inventory: left-click (either way round) fills the book from the item; right-click fills the item
    private static class Generic implements StorageBookBehaviour
    {
        @Override
        public InteractionResult useItemOnPlaced(ItemStack held, StorageBookBlockEntity book, Level level, BlockPos pos, Player player, InteractionHand hand)
        {
            return useWith(book.type(), book.storage(), StorageInteractions.heldAccess(player, hand), player.isShiftKeyDown(), level.isClientSide())
                    ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        public boolean clickedWith(ItemStack book, ItemStack cursor, Slot slot, ClickAction action, Player player)
        {
            if (cursor.isEmpty()) return false;
            BookStorage storage = BookStorage.open(book);
            return storage != null && useWith(storage.type(), storage.storage(), cursorAccess(player),
                    action == ClickAction.SECONDARY, player.level().isClientSide());
        }

        @Override
        public boolean clickedOnto(ItemStack book, Slot slot, ClickAction action, Player player)
        {
            BookStorage storage = BookStorage.open(book);
            if (storage == null || slot.getItem().isEmpty()) return false;
            if (player.level().isClientSide()) return storage.type().getItemHandler(ItemAccess.forStack(slot.getItem().copy())) != null;
            return withSlotAccess(player, slot, access -> useWith(storage.type(), storage.storage(), access,
                    action == ClickAction.SECONDARY, false));
        }

        // Whether the item holds this type; on the server also moves: into the item (toItem) or into the book
        private static boolean useWith(TransportType<?> type, ResourceHandler<?> book, ItemAccess item, boolean toItem, boolean clientSide)
        {
            ResourceHandler<?> itemHandler = type.getItemHandler(item);
            if (itemHandler == null) return false;
            if (!clientSide)
            {
                if (toItem) move(type, book, itemHandler, Integer.MAX_VALUE);
                else move(type, itemHandler, book, Integer.MAX_VALUE);
            }
            return true;
        }
    }
}
