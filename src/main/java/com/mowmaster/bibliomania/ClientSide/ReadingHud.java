package com.mowmaster.bibliomania.ClientSide;

import com.mowmaster.bibliomania.Blocks.Library.LibraryPriority;
import com.mowmaster.bibliomania.Blocks.Book.BookFilter;
import com.mowmaster.bibliomania.Blocks.CardCatalog.DrawerEntry;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlockEntity;
import com.mowmaster.bibliomania.Blocks.CardCatalog.CardCatalogBlock;
import com.mowmaster.bibliomania.Blocks.Lectern.ReadingLecternBlockEntity;
import com.mowmaster.bibliomania.Blocks.Requestor.PatternDispenserBlock;
import com.mowmaster.bibliomania.Blocks.Requestor.PatternHolder;
import com.mowmaster.bibliomania.Blocks.Deposit.StorageDepositBlockEntity;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.bibliomania.Blocks.Requestor.RequestPattern;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.Book.PickupableBook;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlock;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.Blocks.Bookcase.ShelvedBook;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookItem;
import com.mowmaster.bibliomania.ClientConfig;
import com.mowmaster.bibliomania.Items.ReadingGlassesItem;
import com.mowmaster.bibliomania.Network.BlockInfoPayloads;
import com.mowmaster.mowlib.paging.IPagedStorageItem;
import com.mowmaster.mowlib.paging.PagedContents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// What Reading Goggles (or Reading Glasses) show while worn, as a HUD layer:
// - Looking at a placed Bibliomania book: its item tooltip (what you'd see hovering it in an inventory) written on a
//   torn, yellowed piece of paper, by default at the middle of the left edge. Items the book shows that have more to
//   say - enchanted items and books, potions, tipped arrows (a Ledger's trade, a storage book's open page) - get their
//   own tooltips underneath. Storage books (MowLib paged items) also say which page is open ("Wool Book (Page 3/16)")
//   and what's on it, with a grid of every page's item at the bottom, no wider than a third of the screen.
// - Holding a Bibliomania book (main hand first) and not looking at one: the same paper for the held book, so turning
//   its pages shows on the paper (HeldPageMessages then skips its message above the hotbar).
// - Looking at a Bookcase: the book in the space looked at, like a placed book; between books, the list of its books
//   and how full they are.
// - Looking at any other block or a mob: a small paper label, by default at the top middle, with its icon and name.
//   Blocks add what they store (energy, fluids, experience) and, on a chiseled bookshelf, the looked-at book's name
//   and enchantments; mobs their effects and how long until they grow up or can breed again.
//   Both are asked from the server (Network/BlockInfoPayloads).
// Positions are in ClientConfig. The text is worked out once a tick (here, ClientTickEvent) and only drawn each frame.
// 26.1+ GUI drawing goes through GuiGraphicsExtractor (it was GuiGraphics: drawString -> text, renderItem -> item...).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID, value = Dist.CLIENT)
public final class ReadingHud
{
    private ReadingHud() {}

    private static final Identifier LAYER = Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "reading_goggles");
    // GUI sprites: textures/gui/sprites/<name>.png, stretched by their .png.mcmeta ("nine_slice": the corners and
    // edges keep their size, the middle is tiled)
    private static final Identifier PAPER = Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "reading_paper");
    private static final Identifier LABEL = Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "reading_label");

    // Dark brown ink (0xAARRGGBB); text colours that would be too pale on paper are darkened (see ink)
    private static final int INK_RGB = 0x3B2A1A;
    private static final int INK = 0xFF000000 | INK_RGB;
    private static final int LINE_HEIGHT = 10;
    private static final int PAPER_PADDING = 10;
    private static final int LABEL_PADDING = 6;
    // Ask the server about the looked-at block or mob every 10 ticks (twice a second)
    private static final int ASK_EVERY = 10;

    // The page grid: slot size, at most 9 across, never wider than this part of the screen
    private static final int SLOT = 18;
    private static final int MAX_COLUMNS = 9;
    private static final float GRID_SCREEN_SHARE = 0.33f;
    private static final int GRID_GAP = 4;
    // Slots drawn as a darker patch of paper, the open page ringed in ink
    private static final int SLOT_FILL = 0x40553A1C;
    private static final int SLOT_EDGE = 0x80553A1C;

    // ---- What's shown (worked out each tick) ----

    // The book's lines, already wrapped (empty: no book being looked at), and a storage book's pages (or null)
    private static List<FormattedCharSequence> bookLines = List.of();
    private static int bookWidth;
    private static @Nullable PagedContents bookPages;
    // The label: shown or not, its icon and name, and which block or mob it's about (for the server's answer)
    private static boolean labelShown;
    private static ItemStack labelIcon = ItemStack.EMPTY;
    private static Component labelName = Component.empty();
    private static @Nullable BlockPos labelPos;
    // The chiseled bookshelf slot looked at, or -1
    private static int labelSlot = -1;
    private static int labelEntity = -1;
    private static int ticks;

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event)
    {
        // Under the chat, so chat messages are never covered
        event.registerBelow(VanillaGuiLayers.CHAT, LAYER, ReadingHud::render);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        // The book on the paper (looked at, or else held) and the label (any other block or a mob) are separate:
        // both can show at once. A Bookcase looked at between its books puts its list on the paper instead.
        ItemStack book = ItemStack.EMPTY;
        List<Component> shelfLines = null;
        boolean newLabel = false;
        BlockPos newPos = null;
        int newSlot = -1;
        int newEntity = -1;
        if (player != null && level != null && ReadingGlassesItem.canRead(player))
        {
            // hitResult: what the crosshair is on, worked out each frame (blocks, or mobs within reach)
            HitResult hitResult = minecraft.hitResult;
            boolean lookingAtBook = false;
            if (hitResult instanceof EntityHitResult entityHit && hitResult.getType() == HitResult.Type.ENTITY)
            {
                if (ClientConfig.LABEL_ENABLED.get())
                {
                    Entity entity = entityHit.getEntity();
                    newLabel = true;
                    newEntity = entity.getId();
                    // The mob's spawn egg as its icon, if it has one (26.1+: byId gives an Optional holder of the egg)
                    labelIcon = SpawnEggItem.byId(entity.getType()).map(ItemStack::new).orElse(ItemStack.EMPTY);
                    labelName = entity.getDisplayName();
                    if (newEntity != labelEntity || ticks % ASK_EVERY == 0)
                    {
                        ClientPacketDistributor.sendToServer(new BlockInfoPayloads.EntityRequest(newEntity));
                    }
                }
            }
            else if (hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
            {
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                BlockEntity blockEntity = level.getBlockEntity(pos);
                // A Card Catalog drawer that's set: its item (or book, or trade) on the paper
                DrawerEntry drawerEntry = blockEntity instanceof CardCatalogBlockEntity catalog && state.getBlock() instanceof CardCatalogBlock catalogBlock
                        ? catalog.drawer(catalogBlock.drawerAt(state, hit)) : null;
                if (drawerEntry != null)
                {
                    lookingAtBook = true;
                    if (ClientConfig.BOOK_ENABLED.get()) shelfLines = readDrawer(drawerEntry, level, player);
                }
                else if (blockEntity instanceof PickupableBook placed)
                {
                    lookingAtBook = true;
                    // The placed book as an item with its contents, so its tooltip is the one it has in an inventory
                    if (ClientConfig.BOOK_ENABLED.get()) book = placed.toItem();
                }
                else if (blockEntity instanceof PatternHolder holder && state.getBlock() instanceof PatternDispenserBlock)
                {
                    // A Patterned Storage Requestor or Storage Dispenser: its pattern (the cell looked at first)
                    lookingAtBook = true;
                    if (ClientConfig.BOOK_ENABLED.get())
                    {
                        shelfLines = readPattern(state, holder, PatternDispenserBlock.cellAt(state, hit, holder), level, player);
                    }
                }
                else if (blockEntity instanceof StorageDepositBlockEntity)
                {
                    // A Storage Deposit: what it lets into the library
                    lookingAtBook = true;
                    if (ClientConfig.BOOK_ENABLED.get()) shelfLines = readDeposit(state);
                }
                else if (blockEntity instanceof ReadingLecternBlockEntity lectern)
                {
                    // A Reading Lectern: its open page
                    lookingAtBook = true;
                    if (ClientConfig.BOOK_ENABLED.get()) shelfLines = readLectern(state, lectern, level, player);
                }
                else if (blockEntity instanceof BookcaseBlockEntity shelf && state.getBlock() instanceof BookcaseBlock bookcase)
                {
                    // A Bookcase: the book in the space looked at, or else the list of its books
                    lookingAtBook = true;
                    if (ClientConfig.BOOK_ENABLED.get())
                    {
                        int slot = bookcase.slotAt(state, hit, shelf);
                        ItemStack shelved = slot >= 0 ? shelf.getBook(slot) : ItemStack.EMPTY;
                        if (!shelved.isEmpty()) book = shelved.copy();
                        else shelfLines = readShelf(state, shelf);
                    }
                }
                else if (!state.isAir() && ClientConfig.LABEL_ENABLED.get())
                {
                    newLabel = true;
                    newPos = pos;
                    newSlot = BlockInfoPayloads.bookshelfSlot(state, hit);
                    labelIcon = new ItemStack(state.getBlock());
                    labelName = state.getBlock().getName();
                    // Asked straight away when the block (or bookshelf slot) changes, then twice a second
                    boolean ask = ClientConfig.LABEL_AMOUNTS.get() || newSlot >= 0;
                    if (ask && (!pos.equals(labelPos) || newSlot != labelSlot || ticks % ASK_EVERY == 0))
                    {
                        ClientPacketDistributor.sendToServer(new BlockInfoPayloads.Request(pos, newSlot));
                    }
                }
            }
            if (!lookingAtBook) book = heldBook(player);
        }

        PagedContents newPages = null;
        List<FormattedCharSequence> newBook = List.of();
        if (shelfLines != null)
        {
            newBook = wrap(minecraft.font, shelfLines, ClientConfig.BOOK_MAX_WIDTH.get());
        }
        else if (!book.isEmpty() && level != null)
        {
            // Storage books: their pages (the item holds them as MowLib's PagedContents); a whitelist-filtered book
            // holding only what its filter allows: its items with their totals instead (no page grid)
            List<ResourceStack<ItemResource>> summary = null;
            if (book.getItem() instanceof IPagedStorageItem paged)
            {
                PagedContents contents = paged.contents(book);
                summary = BookFilter.summary(book, contents);
                if (summary == null) newPages = contents;
            }
            List<Component> lines = summary != null ? readFilteredBook(book, summary, level, player) : readBook(book, newPages, level, player);
            newBook = wrap(minecraft.font, lines, ClientConfig.BOOK_MAX_WIDTH.get());
        }
        bookLines = newBook;
        bookWidth = width(minecraft.font, newBook);
        bookPages = newPages;
        labelShown = newLabel;
        labelPos = newPos;
        labelSlot = newSlot;
        labelEntity = newEntity;
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

    private static MutableComponent hud(String key, Object... args)
    {
        return Component.translatable("hud." + BibliomaniaReferences.MODID + "." + key, args);
    }

    // ---- Held books ----

    // Whether the paper shows this held item: a Bibliomania book (its block is a BookBlock), one on its own, while the
    // player can read and held books are turned on
    public static boolean showsHeld(Player player, ItemStack stack)
    {
        return stack.getCount() == 1 && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof BookBlock
                && ClientConfig.BOOK_ENABLED.get() && ClientConfig.BOOK_HELD.get() && ReadingGlassesItem.canRead(player);
    }

    // The held book to show (main hand first), or EMPTY
    private static ItemStack heldBook(Player player)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack stack = player.getItemInHand(hand);
            if (showsHeld(player, stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    // ---- Reading a book ----

    private static List<Component> readBook(ItemStack item, @Nullable PagedContents pages, Level level, Player player)
    {
        List<Component> lines = new ArrayList<>(tooltip(item, level, player));
        // A filter that isn't shown as a summary (a blacklist, or the book holds things it doesn't allow): named
        ItemStack filter = BookFilter.get(item);
        if (!filter.isEmpty() && !lines.isEmpty()) lines.add(1, hud("book_filter", filter.getHoverName()));
        if (pages != null && !lines.isEmpty())
        {
            // "Wool Book (Page 3/16)", then "24 Cobblestone" (or "Empty page")
            int page = pages.selectedPage();
            lines.set(0, Component.translatable("hud." + BibliomaniaReferences.MODID + ".page", lines.get(0), page + 1, pages.pages()));
            ResourceStack<ItemResource> stack = pages.stack(page);
            lines.add(1, stack.isEmpty()
                    ? Component.translatable("hud." + BibliomaniaReferences.MODID + ".empty_page")
                    : Component.translatable("hud." + BibliomaniaReferences.MODID + ".page_item", stack.amount(), stack.resource().getHoverName()));
        }
        for (ItemStack shown : shownItems(item, pages))
        {
            if (!worthReading(shown)) continue;
            lines.add(Component.empty());
            lines.addAll(tooltip(shown, level, player));
        }
        return lines;
    }

    // "Wool Book", "Filter: Item Filter", then each item it holds (and the filter's others): "1,240 Cobblestone"
    private static List<Component> readFilteredBook(ItemStack book, List<ResourceStack<ItemResource>> summary, Level level, Player player)
    {
        List<Component> lines = new ArrayList<>();
        lines.add(book.getHoverName());
        lines.add(hud("book_filter", BookFilter.get(book).getHoverName()));
        int priority = LibraryPriority.ofBook(book);
        if (priority > 0) lines.add(hud("priority", priority));
        for (ResourceStack<ItemResource> stack : summary)
        {
            lines.add(hud("page_item", stack.amount(), stack.resource().toStack(1).getHoverName()));
        }
        return lines;
    }

    private static List<Component> tooltip(ItemStack stack, Level level, Player player)
    {
        // The same lines an inventory tooltip has (name first), not the advanced (F3+H) ones
        return stack.getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL);
    }

    // The items a book has on show: a Ledger's trade (what it gives and costs), a storage book's open page
    private static List<ItemStack> shownItems(ItemStack book, @Nullable PagedContents pages)
    {
        List<ItemStack> items = new ArrayList<>();
        if (book.getItem() instanceof VillagerBookItem)
        {
            MerchantOffer offer = VillagerBookItem.contents(book).currentOffer();
            if (offer != null)
            {
                items.add(offer.getResult());
                items.add(offer.getCostA());
                items.add(offer.getCostB());
            }
        }
        else if (pages != null)
        {
            ResourceStack<ItemResource> open = pages.stack(pages.selectedPage());
            if (!open.isEmpty()) items.add(open.resource().toStack(1));
        }
        return items;
    }

    // Enchanted items, enchanted books (stored enchantments), potions and tipped arrows
    private static boolean worthReading(ItemStack stack)
    {
        return !stack.isEmpty() && (stack.isEnchanted() || stack.has(DataComponents.STORED_ENCHANTMENTS)
                || stack.has(DataComponents.POTION_CONTENTS));
    }

    // ---- Text on paper ----

    // Each line in ink, wrapped to the width; FormattedCharSequence is text already laid out for drawing
    private static List<FormattedCharSequence> wrap(Font font, List<Component> lines, int maxWidth)
    {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines)
        {
            // An empty line stays as a gap (split would drop it)
            if (line.getString().isEmpty()) wrapped.add(FormattedCharSequence.EMPTY);
            else wrapped.addAll(font.split(ink(line), maxWidth));
        }
        return wrapped;
    }

    // The line rebuilt piece by piece: uncoloured text in ink, pale colours (white, yellow, light grey...) darkened so
    // they show on paper, dark colours kept. visit calls the lambda for each run of text with its style.
    private static Component ink(Component line)
    {
        MutableComponent inked = Component.empty();
        line.visit((style, text) -> {
            TextColor color = style.getColor();
            int rgb = color == null ? INK_RGB : darken(color.getValue());
            inked.append(Component.literal(text).setStyle(style.withColor(rgb)));
            return Optional.empty(); // empty = keep going
        }, Style.EMPTY);
        return inked;
    }

    private static int darken(int rgb)
    {
        int red = (rgb >> 16) & 0xFF, green = (rgb >> 8) & 0xFF, blue = rgb & 0xFF;
        double brightness = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0;
        if (brightness < 0.45) return rgb;
        double factor = 0.45 / brightness;
        return ((int) (red * factor) << 16) | ((int) (green * factor) << 8) | (int) (blue * factor);
    }

    private static int width(Font font, List<FormattedCharSequence> lines)
    {
        int width = 0;
        for (FormattedCharSequence line : lines) width = Math.max(width, font.width(line));
        return width;
    }

    // ---- Drawing (every frame) ----

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)
    {
        Minecraft minecraft = Minecraft.getInstance();
        // F1 hides the HUD
        if (minecraft.gui.hud.isHidden()) return;
        if (!bookLines.isEmpty()) drawPaper(graphics, minecraft.font);
        if (labelShown) drawLabel(graphics, minecraft.font);
    }

    private static void drawPaper(GuiGraphicsExtractor graphics, Font font)
    {
        int screenWidth = graphics.guiWidth(), screenHeight = graphics.guiHeight();
        int room = screenHeight - 2 * PAPER_PADDING - 8;

        // The page grid: as many columns as fit in a third of the screen (at most 9), and the rows that fit under the
        // text, scrolled so the open page's row is in view
        PagedContents pages = bookPages;
        int columns = 0, rows = 0, firstRow = 0;
        if (pages != null && pages.pages() > 0)
        {
            columns = Math.clamp((int) (screenWidth * GRID_SCREEN_SHARE - 2 * PAPER_PADDING) / SLOT, 1, Math.min(MAX_COLUMNS, pages.pages()));
            int totalRows = (pages.pages() + columns - 1) / columns;
            int textRoom = Math.min(bookLines.size(), 4) * LINE_HEIGHT;
            rows = Math.clamp((room - textRoom - GRID_GAP) / SLOT, 1, totalRows);
            int openRow = pages.selectedPage() / columns;
            firstRow = Math.clamp(openRow - rows / 2, 0, totalRows - rows);
        }
        int gridHeight = rows > 0 ? GRID_GAP + rows * SLOT : 0;

        // As many lines as fit above the grid
        int fit = Math.max(1, (room - gridHeight) / LINE_HEIGHT);
        List<FormattedCharSequence> lines = bookLines.size() > fit ? bookLines.subList(0, fit) : bookLines;
        int width = Math.max(bookWidth, columns * SLOT) + 2 * PAPER_PADDING;
        int height = lines.size() * LINE_HEIGHT + gridHeight + 2 * PAPER_PADDING - 2;
        int x = place(ClientConfig.BOOK_ANCHOR.get().x(screenWidth, width) + ClientConfig.BOOK_X.get(), width, screenWidth);
        int y = place(ClientConfig.BOOK_ANCHOR.get().y(screenHeight, height) + ClientConfig.BOOK_Y.get(), height, screenHeight);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PAPER, x, y, width, height);
        for (int index = 0; index < lines.size(); index++)
        {
            // false: no drop shadow (ink doesn't have one)
            graphics.text(font, lines.get(index), x + PAPER_PADDING, y + PAPER_PADDING + index * LINE_HEIGHT, INK, false);
        }
        if (pages != null && rows > 0)
        {
            int gridTop = y + PAPER_PADDING + lines.size() * LINE_HEIGHT + GRID_GAP - 2;
            drawPages(graphics, font, pages, x + PAPER_PADDING, gridTop, columns, firstRow, rows);
        }
    }

    // One slot per page (empty pages too), each page's item with its amount, the open page ringed in ink
    private static void drawPages(GuiGraphicsExtractor graphics, Font font, PagedContents pages, int left, int top,
                                  int columns, int firstRow, int rows)
    {
        int first = firstRow * columns;
        int last = Math.min(pages.pages(), first + rows * columns);
        for (int page = first; page < last; page++)
        {
            int slotX = left + (page % columns) * SLOT;
            int slotY = top + (page / columns - firstRow) * SLOT;
            graphics.fill(slotX + 1, slotY + 1, slotX + SLOT - 1, slotY + SLOT - 1, SLOT_FILL);
            outline(graphics, slotX, slotY, page == pages.selectedPage() ? INK : SLOT_EDGE);
            ResourceStack<ItemResource> stack = pages.stack(page);
            if (stack.isEmpty()) continue;
            // One item drawn, with the page's whole amount as its count (a page can hold more than a stack)
            ItemStack shown = stack.resource().toStack(1);
            graphics.item(shown, slotX + 1, slotY + 1);
            graphics.itemDecorations(font, shown, slotX + 1, slotY + 1, stack.amount() > 1 ? String.valueOf(stack.amount()) : null);
        }
    }

    // A 1-pixel frame round a slot
    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int color)
    {
        graphics.fill(x, y, x + SLOT, y + 1, color);
        graphics.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, color);
        graphics.fill(x, y + 1, x + 1, y + SLOT - 1, color);
        graphics.fill(x + SLOT - 1, y + 1, x + SLOT, y + SLOT - 1, color);
    }

    private static void drawLabel(GuiGraphicsExtractor graphics, Font font)
    {
        // The server's answer, if it's about this block or mob
        List<Component> answer = List.of();
        if (labelEntity >= 0 && labelEntity == BlockInfoPayloads.answeredEntity) answer = BlockInfoPayloads.answeredEntityLines;
        else if (labelPos != null && labelPos.equals(BlockInfoPayloads.answeredPos) && labelSlot == BlockInfoPayloads.answeredSlot)
        {
            answer = BlockInfoPayloads.answeredLines;
        }
        List<FormattedCharSequence> extra = new ArrayList<>();
        for (Component line : answer) extra.add(ink(line).getVisualOrderText());

        FormattedCharSequence name = ink(labelName).getVisualOrderText();
        boolean icon = !labelIcon.isEmpty();
        int textLeft = icon ? 16 + 4 : 0;
        int width = 2 * LABEL_PADDING + Math.max(textLeft + font.width(name), width(font, extra));
        int height = 2 * LABEL_PADDING + 16 + extra.size() * LINE_HEIGHT;
        int x = place(ClientConfig.LABEL_ANCHOR.get().x(graphics.guiWidth(), width) + ClientConfig.LABEL_X.get(), width, graphics.guiWidth());
        int y = place(ClientConfig.LABEL_ANCHOR.get().y(graphics.guiHeight(), height) + ClientConfig.LABEL_Y.get(), height, graphics.guiHeight());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LABEL, x, y, width, height);
        if (icon) graphics.item(labelIcon, x + LABEL_PADDING, y + LABEL_PADDING);
        // The name level with the middle of the icon
        graphics.text(font, name, x + LABEL_PADDING + textLeft, y + LABEL_PADDING + 4, INK, false);
        for (int index = 0; index < extra.size(); index++)
        {
            graphics.text(font, extra.get(index), x + LABEL_PADDING, y + LABEL_PADDING + 18 + index * LINE_HEIGHT, INK, false);
        }
    }

    // Keeps a panel on screen, however far the offsets push it
    private static int place(int at, int size, int screen)
    {
        return Math.clamp(at, 0, Math.max(0, screen - size));
    }
}
