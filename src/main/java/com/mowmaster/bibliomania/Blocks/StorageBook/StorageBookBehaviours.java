package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.mowlib.transport.TransportItemInteractions;
import com.mowmaster.mowlib.transport.ItemTransportStorage;
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

    // (Reaching items in slots and moving between handlers: MowLib's StorageInteractions.withSlotAccess / cursorAccess /
    // move; the generic clicks: MowLib's TransportItemInteractions.)

    // ---- The generic behaviour ----

    // Items that hold the book's type (found through TransportType#getItemHandler, e.g. a battery for energy):
    //   placed book: right-click with one fills the book from it; sneak + right-click empties the book into it
    //   inventory: left-click (either way round) fills the book from the item; right-click fills the item
    private static class Generic implements StorageBookBehaviour
    {
        @Override
        public InteractionResult useItemOnPlaced(ItemStack held, StorageBookBlockEntity book, Level level, BlockPos pos, Player player, InteractionHand hand)
        {
            return useItemOnStorage(book.type(), book.storage(), held, level, pos, player, hand);
        }

        // A container (battery...) against any storage of the type: into the storage, or with sneak out of it
        @Override
        public InteractionResult useItemOnStorage(TransportType<?> type, ResourceHandler<?> storage, ItemStack held, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand)
        {
            return useWith(type, storage, StorageInteractions.heldAccess(player, hand), player.isShiftKeyDown(), level.isClientSide())
                    ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        public boolean clickedWith(ItemStack book, ItemStack cursor, Slot slot, ClickAction action, Player player)
        {
            return TransportItemInteractions.otherStackedOnMe(book, cursor, slot, action, player);
        }

        @Override
        public boolean clickedOnto(ItemStack book, Slot slot, ClickAction action, Player player)
        {
            return TransportItemInteractions.stackedOnOther(book, slot, action, player);
        }

        // Whether the item holds this type; on the server also moves: into the item (toItem) or into the book
        private static boolean useWith(TransportType<?> type, ResourceHandler<?> book, ItemAccess item, boolean toItem, boolean clientSide)
        {
            return TransportItemInteractions.useContainer(type, book, item, toItem, clientSide);
        }
    }
}
