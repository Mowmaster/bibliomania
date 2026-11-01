package com.mowmaster.bibliomania.ClientSide;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.PickupableBook;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookBlockEntity;
import com.mowmaster.bibliomania.ClientConfig;
import com.mowmaster.bibliomania.Items.ReadingGlassesItem;
import com.mowmaster.bibliomania.Network.BlockInfoPayloads;
import com.mowmaster.mowlib.display.ISpinningItemHolder;
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
import net.minecraft.world.entity.player.Player;
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
// - Looking at any other block or a mob: a small paper label, by default at the top middle, with its icon and name.
//   Blocks add what they store (energy, fluids, experience); mobs how long until they grow up or can breed again.
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
        List<FormattedCharSequence> newBook = List.of();
        PagedContents newPages = null;
        boolean newLabel = false;
        BlockPos newPos = null;
        int newEntity = -1;
        try
        {
            if (player == null || level == null || !ReadingGlassesItem.canRead(player)) return;
            // hitResult: what the crosshair is on, worked out each frame (blocks, or mobs within reach)
            HitResult hitResult = minecraft.hitResult;

            if (hitResult instanceof EntityHitResult entityHit && hitResult.getType() == HitResult.Type.ENTITY)
            {
                if (!ClientConfig.LABEL_ENABLED.get()) return;
                Entity entity = entityHit.getEntity();
                newLabel = true;
                newEntity = entity.getId();
                // The mob's spawn egg as its icon, if it has one (26.1+: byId gives an Optional holder of the egg item)
                labelIcon = SpawnEggItem.byId(entity.getType()).map(ItemStack::new).orElse(ItemStack.EMPTY);
                labelName = entity.getDisplayName();
                if (newEntity != labelEntity || ticks % ASK_EVERY == 0)
                {
                    ClientPacketDistributor.sendToServer(new BlockInfoPayloads.EntityRequest(newEntity));
                }
                return;
            }

            if (!(hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return;
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (blockEntity instanceof PickupableBook book)
            {
                if (!ClientConfig.BOOK_ENABLED.get()) return;
                ItemStack item = book.toItem();
                // Storage books: their pages (the item holds them as MowLib's PagedContents)
                if (item.getItem() instanceof IPagedStorageItem paged) newPages = paged.contents(item);
                newBook = wrap(minecraft.font, readBook(item, newPages, blockEntity, level, player), ClientConfig.BOOK_MAX_WIDTH.get());
                return;
            }
            if (!ClientConfig.LABEL_ENABLED.get()) return;
            newLabel = true;
            newPos = pos;
            labelIcon = new ItemStack(state.getBlock());
            labelName = state.getBlock().getName();
            if (ClientConfig.LABEL_AMOUNTS.get() && (!pos.equals(labelPos) || ticks % ASK_EVERY == 0))
            {
                ClientPacketDistributor.sendToServer(new BlockInfoPayloads.Request(pos));
            }
        }
        // finally: runs however the try block ends, the early returns included
        finally
        {
            bookLines = newBook;
            bookWidth = width(minecraft.font, newBook);
            bookPages = newPages;
            labelShown = newLabel;
            labelPos = newPos;
            labelEntity = newEntity;
        }
    }

    // ---- Reading a book ----

    private static List<Component> readBook(ItemStack item, @Nullable PagedContents pages, BlockEntity blockEntity, Level level, Player player)
    {
        // The book as an item with its contents, so its tooltip is the one it has in an inventory
        List<Component> lines = new ArrayList<>(tooltip(item, level, player));
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
        for (ItemStack shown : shownItems(blockEntity))
        {
            if (!worthReading(shown)) continue;
            lines.add(Component.empty());
            lines.addAll(tooltip(shown, level, player));
        }
        return lines;
    }

    private static List<Component> tooltip(ItemStack stack, Level level, Player player)
    {
        // The same lines an inventory tooltip has (name first), not the advanced (F3+H) ones
        return stack.getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL);
    }

    // The items a book has on show: a Ledger's trade (what it gives and costs), a storage book's open page
    private static List<ItemStack> shownItems(BlockEntity blockEntity)
    {
        List<ItemStack> items = new ArrayList<>();
        if (blockEntity instanceof VillagerBookBlockEntity ledger)
        {
            MerchantOffer offer = ledger.getContents().currentOffer();
            if (offer != null)
            {
                items.add(offer.getResult());
                items.add(offer.getCostA());
                items.add(offer.getCostB());
            }
        }
        else if (blockEntity instanceof ISpinningItemHolder spinning)
        {
            items.add(spinning.getDisplayedItem());
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
        else if (labelShown) drawLabel(graphics, minecraft.font);
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
        else if (labelPos != null && ClientConfig.LABEL_AMOUNTS.get() && labelPos.equals(BlockInfoPayloads.answeredPos)) answer = BlockInfoPayloads.answeredLines;
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
