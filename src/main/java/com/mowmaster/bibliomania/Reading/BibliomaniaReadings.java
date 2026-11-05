package com.mowmaster.bibliomania.Reading;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlock;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.Blocks.Bookcase.ShelvedBook;
import com.mowmaster.bibliomania.Blocks.Book.PickupableBook;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlock;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlockEntity;
import com.mowmaster.bibliomania.Blocks.CardCatalog.DrawerEntry;
import com.mowmaster.bibliomania.Blocks.Deposit.StorageAccessPointBlockEntity;
import com.mowmaster.bibliomania.Blocks.Deposit.StorageDepositBlockEntity;
import com.mowmaster.bibliomania.Blocks.Lectern.ReadingLecternBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryControllerBlockEntity;
import com.mowmaster.bibliomania.Blocks.Library.LibraryCosts;
import com.mowmaster.bibliomania.Blocks.Requestor.PatternDispenserBlock;
import com.mowmaster.bibliomania.Blocks.Requestor.PatternHolder;
import com.mowmaster.bibliomania.Blocks.Requestor.RequestPattern;
import com.mowmaster.bibliomania.Blocks.Requestor.StorageDispenserBlockEntity;
import com.mowmaster.mowlib.reading.ReadingContext;
import com.mowmaster.mowlib.reading.ReadingPaper;
import com.mowmaster.mowlib.reading.ReadingPapers;
import com.mowmaster.mowlib.reading.ReadingRegistry;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// What Bibliomania's blocks put on MowLib's reading HUD (reading/: shown while wearing Reading Goggles or Reading
// Glasses, which are in #mowlib:grants_reading). register() adds one block reader to MowLib's ReadingRegistry; it runs
// on the client once a tick for the looked-at block:
// - a placed book: the book as its item (ReadingPapers.item: its tooltip; storage books their page and page grid)
// - a Bookcase: the book in the space looked at, or else the list of its books
// - a Card Catalog drawer, a Patterned Storage Requestor or Storage Dispenser, a Storage Deposit, a Reading Lectern
// - a Storage Access Point or Library Controller: its name, then the server's lines (their block entities are MowLib
//   IServerReadable: libraryTotals / libraryMembers below, run on the server)
// Moved here from the old ClientSide/ReadingHud and Network/BlockInfoPayloads; only common classes, so it loads on
// servers too.
public final class BibliomaniaReadings
{
    private BibliomaniaReadings() {}

    // At most this many lines from the server
    private static final int MAX_LINES = 16;

    // Called from common setup
    public static void register()
    {
        ReadingRegistry.addBlockReader(BibliomaniaReadings::read);
    }

    private static @Nullable ReadingPaper read(ReadingContext context)
    {
        Level level = context.level();
        Player player = context.player();
        BlockState state = context.state();
        BlockEntity blockEntity = context.blockEntity();
        if (blockEntity instanceof CardCatalogBlockEntity catalog && state.getBlock() instanceof CardCatalogBlock catalogBlock)
        {
            DrawerEntry entry = catalog.drawer(catalogBlock.drawerAt(state, context.hit()));
            return entry == null ? null : ReadingPaper.of(readDrawer(entry, level, player));
        }
        if (blockEntity instanceof PickupableBook placed) return ReadingPapers.item(placed.toItem(), level, player);
        if (blockEntity instanceof PatternHolder holder && state.getBlock() instanceof PatternDispenserBlock)
        {
            return ReadingPaper.of(readPattern(state, holder, PatternDispenserBlock.cellAt(state, context.hit(), holder), level, player));
        }
        if (blockEntity instanceof StorageAccessPointBlockEntity || blockEntity instanceof LibraryControllerBlockEntity)
        {
            return ReadingPaper.fromServer(List.of(state.getBlock().getName()));
        }
        if (blockEntity instanceof StorageDepositBlockEntity) return ReadingPaper.of(readDeposit(state));
        if (blockEntity instanceof ReadingLecternBlockEntity lectern) return ReadingPaper.of(readLectern(state, lectern, level, player));
        if (blockEntity instanceof BookcaseBlockEntity shelf && state.getBlock() instanceof BookcaseBlock bookcase)
        {
            int slot = bookcase.slotAt(state, context.hit(), shelf);
            ItemStack shelved = slot >= 0 ? shelf.getBook(slot) : ItemStack.EMPTY;
            return shelved.isEmpty() ? ReadingPaper.of(readShelf(state, shelf)) : ReadingPapers.item(shelved.copy(), level, player);
        }
        return null;
    }

    // ---- Bookcases ----

    // "Bookcase", "Space used: 40%", then each book: "1. Wool Book (35%)" (the % for books that store things)
    private static List<Component> readShelf(BlockState state, BookcaseBlockEntity shelf)
    {
        List<Component> lines = new ArrayList<>();
        lines.add(state.getBlock().getName());
        float used = shelf.fullness();
        if (used >= 0) lines.add(hud("shelf_used", Math.round(used * 100)));
        if (shelf.getPriority() > 0) lines.add(hud("priority", shelf.getPriority()));
        boolean any = false;
        for (int slot = 0; slot < BookcaseBlockEntity.SLOTS; slot++)
        {
            ItemStack shelved = shelf.getBook(slot);
            if (shelved.isEmpty()) continue;
            any = true;
            float fullness = shelved.getItem() instanceof ShelvedBook book ? book.shelfFullness(shelved) : -1f;
            lines.add(fullness >= 0
                    ? hud("shelf_book_full", slot + 1, shelved.getHoverName(), Math.round(fullness * 100))
                    : hud("shelf_book", slot + 1, shelved.getHoverName()));
        }
        if (!any) lines.add(hud("shelf_empty"));
        return lines;
    }

    // ---- Card Catalogs ----

    // The drawer's item (name, enchantments, potion effects...), and for a trade drawer what it's for
    // - an item: its tooltip (enchantments, potion effects...)
    // - a book: "Grandad's Almanac (Farmer's Almanac)", what it holds ("Wheat Seeds", "Cow"), and what opening does
    // - a trade: the Ledger's name, "2 Emerald -> 1 Enchanted Book", and what opening does
    // - a type: "Water" / "Experience" / "Energy", and that it's every such book in the library
    // (Only what the drawer itself knows: the books' own tooltips, "Activated" and the like, are left out)
    private static List<Component> readDrawer(DrawerEntry entry, Level level, Player player)
    {
        List<Component> lines = new ArrayList<>();
        switch (entry.kind())
        {
            case ITEM -> lines.addAll(tooltip(entry.icon(), level, player));
            case BOOK ->
            {
                entry.title().ifPresent(lines::add);
                entry.subject().ifPresent(lines::add);
                lines.add(hud("drawer_book"));
            }
            case TRADE ->
            {
                entry.title().ifPresent(lines::add);
                lines.add(hud("drawer_trade_line", entry.badge().getCount(), entry.badge().getHoverName(),
                        entry.icon().getCount(), entry.icon().getHoverName()));
                lines.add(hud("drawer_trade"));
            }
            case TYPE ->
            {
                entry.title().ifPresent(lines::add);
                Component typeName = entry.subject().orElse(entry.title().orElse(Component.empty()));
                lines.add(hud("drawer_type", typeName));
            }
        }
        return lines;
    }

    // ---- Storage Deposits ----

    // "Storage Deposit", "Accepts: Items, Fluids, Energy, Experience" (every transport type, by MowLib's names for them)
    private static List<Component> readDeposit(BlockState state)
    {
        List<Component> lines = new ArrayList<>();
        lines.add(state.getBlock().getName());
        MutableComponent types = Component.empty();
        boolean first = true;
        for (TransportType<?> type : TransportTypes.REGISTRY)
        {
            if (!first) types.append(", ");
            types.append(type.getName());
            first = false;
        }
        lines.add(hud("deposit_accepts", types));
        return lines;
    }

    // ---- Patterned Storage Requestors and Storage Dispensers ----

    // "Patterned Storage Requestor", the item in use (a Storage Dispenser's worn shears: its tooltip shows the wear),
    // then the pattern: just the looked-at cell "32 Cobblestone", or else every set slot
    private static List<Component> readPattern(BlockState state, PatternHolder holder, int cell, Level level, Player player)
    {
        List<Component> lines = new ArrayList<>();
        lines.add(state.getBlock().getName());
        RequestPattern pattern = holder.pattern();
        if (!holder.inUse().isEmpty())
        {
            lines.add(hud("pattern_in_use", holder.inUse().getHoverName()));
            if (holder.inUse().isDamaged())
            {
                lines.add(hud("pattern_durability", holder.inUse().getMaxDamage() - holder.inUse().getDamageValue(), holder.inUse().getMaxDamage()));
            }
        }
        if (pattern.isEmpty())
        {
            lines.add(hud("pattern_empty"));
            return lines;
        }
        // A Storage Dispenser set to a Book of Fluids: what it does instead of using an item
        if (pattern.size() == 1 && StorageDispenserBlockEntity.isFluidBook(pattern.item(0)))
        {
            FluidResource fluid = StorageDispenserBlockEntity.bookFluid(pattern.item(0));
            lines.add(fluid.isEmpty() ? hud("dispenser_pump") : hud("dispenser_place", fluid.getHoverName()));
            return lines;
        }
        lines.add(hud(pattern.exact() ? "pattern_match_exact" : "pattern_match_fuzzy"));
        if (!pattern.item(cell).isEmpty() && pattern.size() > 1)
        {
            lines.add(hud("page_item", pattern.count(cell), pattern.item(cell).getHoverName()));
            return lines;
        }
        for (int slot = 0; slot < pattern.size(); slot++)
        {
            if (!pattern.item(slot).isEmpty()) lines.add(hud("page_item", pattern.count(slot), pattern.item(slot).getHoverName()));
        }
        return lines;
    }

    // ---- Reading Lecterns ----

    // "Reading Lectern (Page 12/340)", then the open page: "24 Cobblestone" (and its tooltip if it's enchanted or a
    // potion) with "In: Wool Book", or a whole book's own tooltip; or why there's nothing to show
    private static List<Component> readLectern(BlockState state, ReadingLecternBlockEntity lectern, Level level, Player player)
    {
        List<Component> lines = new ArrayList<>();
        Component name = state.getBlock().getName();
        // Not in a library and no work card of its own
        if (!lectern.hasCard() && lectern.getLibrary() == null)
        {
            lines.add(name);
            lines.add(hud("lectern_no_card"));
            return lines;
        }
        if (lectern.getPageCount() <= 0)
        {
            lines.add(name);
            lines.add(hud("lectern_no_cases"));
            return lines;
        }
        lines.add(Component.translatable("hud." + BibliomaniaReferences.MODID + ".page", name, lectern.getPage() + 1, lectern.getPageCount()));
        ItemStack book = lectern.getShownBook();
        // A card catalog item: "128 Iron Ingot", from the whole library
        // A type drawer: "Water", "2000 / 16000" for the whole library
        if (lectern.showsType())
        {
            lines.add(lectern.getShown().getHoverName());
            lines.add(hud("lectern_type_amount", lectern.getShownAmount(), lectern.getShownCapacity()));
            lines.add(hud("lectern_whole_library"));
            return lines;
        }
        if (lectern.getShownBookPage() == ReadingLecternBlockEntity.WHOLE_LIBRARY)
        {
            ItemStack item = lectern.getShown();
            lines.add(hud("page_item", lectern.getShownAmount(), item.getHoverName()));
            lines.add(hud(lectern.isExact() ? "pattern_match_exact" : "pattern_match_fuzzy"));
            if (worthReading(item))
            {
                lines.add(Component.empty());
                lines.addAll(tooltip(item, level, player));
            }
            lines.add(hud("lectern_whole_library"));
            return lines;
        }
        if (lectern.showsWholeBook())
        {
            // A whole book: the lectern stands in for it
            lines.addAll(tooltip(book, level, player));
            return lines;
        }
        ItemStack shown = lectern.getShown();
        if (shown.isEmpty()) lines.add(hud("empty_page"));
        else
        {
            lines.add(hud("page_item", lectern.getShownAmount(), shown.getHoverName()));
            if (worthReading(shown))
            {
                lines.add(Component.empty());
                lines.addAll(tooltip(shown, level, player));
            }
        }
        if (!book.isEmpty()) lines.add(hud("lectern_in", book.getHoverName(), lectern.getShownBookPage() + 1));
        return lines;
    }

    // ---- Server: Storage Access Points and Library Controllers (their IServerReadable) ----

    // A Storage Access Point or Library Controller: its library's storage, per transport type with everything of it
    // together: "Items: 12,345 / 40,960" (typeTotals: what the type's books hold and have room for), and under it
    // "  12 / 40 slots free, 69% space left"
    public static List<Component> libraryTotals(@Nullable LibraryControllerBlockEntity library)
    {
        List<Component> lines = new ArrayList<>();
        if (library == null)
        {
            lines.add(hud("no_library"));
            return lines;
        }
        for (TransportType<?> type : TransportTypes.REGISTRY)
        {
            long[] totals = library.typeTotals(type, null);
            if (totals[1] <= 0 && totals[0] <= 0) continue;
            lines.add(hud("library_type_total", type.getName(), String.format("%,d", totals[0]), String.format("%,d", totals[1])).withStyle(type.getColor()));
            // Under it: free slots (room for that many more kinds) and how much room is left
            int left = totals[1] <= 0 ? 0 : (int) Math.floor(100.0 * Math.max(0, totals[1] - totals[0]) / totals[1]);
            lines.add(hud("library_type_free", String.format("%,d", totals[3]), String.format("%,d", totals[2]), left).withStyle(ChatFormatting.GRAY));
            if (lines.size() >= MAX_LINES) break;
        }
        if (lines.isEmpty()) lines.add(hud("library_no_storage"));
        return lines;
    }

    // A Library Controller: its Knowledge Points (with costs on), its storage (libraryTotals), then every kind of block
    // in the library with how many: "12 Bookcase", most first
    public static List<Component> libraryMembers(ServerLevel level, LibraryControllerBlockEntity controller)
    {
        // LinkedHashMap: keeps the order kinds were first met; merge adds one to a kind already counted
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (BlockPos member : controller.members())
        {
            if (level.isLoaded(member)) counts.merge(level.getBlockState(member).getBlock(), 1, Integer::sum);
        }
        List<Component> lines = new ArrayList<>();
        // With costs on (LibraryCosts): the Knowledge Point balance, or that it's run out
        if (LibraryCosts.enabled())
        {
            lines.add(controller.isFueled() ? hud("library_knowledge", String.format("%,d", controller.getKnowledge())).withStyle(ChatFormatting.AQUA)
                    : hud("library_no_knowledge").withStyle(ChatFormatting.RED));
        }
        // The storage (as an Access Point shows it: totals, free slots, space left), then the blocks
        List<Component> storage = libraryTotals(controller);
        lines.addAll(storage);
        lines.add(hud("library_members", controller.members().size()));
        counts.entrySet().stream()
                .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                .forEach(entry -> lines.add(hud("library_member", entry.getValue(), entry.getKey().getName())));
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    static MutableComponent hud(String key, Object... args)
    {
        return Component.translatable("hud." + BibliomaniaReferences.MODID + "." + key, args);
    }

    private static List<Component> tooltip(ItemStack stack, Level level, Player player)
    {
        return ReadingPapers.tooltip(stack, level, player);
    }

    private static boolean worthReading(ItemStack stack)
    {
        return ReadingPapers.worthReading(stack);
    }
}
