package com.mowmaster.bibliomania.Blocks.CardCatalog;

import com.mowmaster.mowlib.transport.ItemTransportStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.BarterBook.BarterBookItem;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookItem;
import com.mowmaster.bibliomania.Blocks.EssenceBook.EssenceBookItem;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookItem;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookItem;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookItem;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerContents;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.Items.ItemRegistry;
import com.mowmaster.mowlib.paging.IPagedStorageItem;
import com.mowmaster.mowlib.paging.PagedContents;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// What a card catalog drawer is set to, and how it looks:
// - ITEM:  an item (icon = the item, components and all): opening the drawer has the library's lecterns show that
//          item from the whole library (ReadingLecternBlockEntity#selectItem)
// - BOOK:  one particular book (Almanac, Bestiary, Book of Essence, Book of Barter...): opening it turns the lecterns to
//          that book. Books are told apart by a bibliomania:book_id given to the book when the drawer is set.
// - TRADE: one trade of one Trader's Ledger (book id + trade number): the lecterns turn to that Ledger at that trade
// - TYPE:  every storage book of one transport type in the library (Fluids, Experience, Energy), as one: the lecterns
//          show the library's total and use the books' own actions on all of them (typeId; fluid: which fluid to show)
// Display, worked out when the drawer is set (so the catalog never needs the book itself):
// - icon:    the big icon. Almanac: its crop's item; Bestiary / Essence: the mob's head (or spawn egg); Barter: a piglin
//            head; a trade: what it gives; Fluids: that fluid's bucket; Experience: a bottle o' enchanting; Energy: a
//            red lightning bolt
// - badge:   a small icon in the big one's bottom left corner: a trade's first price; the book itself for a type, an
//            Almanac, a Bestiary or a Book of Essence; or EMPTY
// - title / subject: the Reading Goggles' lines ("Grandad's Almanac (Farmer's Almanac)", "Wheat Seeds")
// - tint:    a fixed drawer colour (Barter: gold block, trades: emerald block; can't be dyed), or -1
public record DrawerEntry(Kind kind, ItemStack icon, ItemStack badge, Optional<UUID> book, int trade,
                          Optional<Component> title, Optional<Component> subject, int tint,
                          Optional<Identifier> typeId, Optional<Identifier> fluid)
{
    public enum Kind { ITEM, BOOK, TRADE, TYPE }

    // Map colours of a gold block and an emerald block (what they look like on a map: their plain colour)
    public static final int GOLD_BLOCK = 0xFAEE4D;
    public static final int EMERALD_BLOCK = 0x00D93A;

    // Kind saved by name ("ITEM"...); xmap turns a codec of one type into one of another, both ways
    private static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(Kind::valueOf, Kind::name);

    public static final Codec<DrawerEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    KIND_CODEC.fieldOf("kind").forGetter(DrawerEntry::kind),
                    ItemStack.CODEC.fieldOf("icon").forGetter(DrawerEntry::icon),
                    // OPTIONAL_CODEC allows an empty stack
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("badge", ItemStack.EMPTY).forGetter(DrawerEntry::badge),
                    UUIDUtil.CODEC.optionalFieldOf("book").forGetter(DrawerEntry::book),
                    Codec.INT.optionalFieldOf("trade", 0).forGetter(DrawerEntry::trade),
                    ComponentSerialization.CODEC.optionalFieldOf("title").forGetter(DrawerEntry::title),
                    ComponentSerialization.CODEC.optionalFieldOf("subject").forGetter(DrawerEntry::subject),
                    Codec.INT.optionalFieldOf("tint", -1).forGetter(DrawerEntry::tint),
                    Identifier.CODEC.optionalFieldOf("type").forGetter(DrawerEntry::typeId),
                    Identifier.CODEC.optionalFieldOf("fluid").forGetter(DrawerEntry::fluid))
            .apply(instance, DrawerEntry::new));

    // A drawer number with its entry, for saving the drawers that are set
    public record Slot(int drawer, DrawerEntry entry)
    {
        public static final Codec<Slot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("drawer").forGetter(Slot::drawer),
                        DrawerEntry.CODEC.fieldOf("entry").forGetter(Slot::entry))
                .apply(instance, Slot::new));
    }

    private static DrawerEntry item(ItemStack icon)
    {
        return new DrawerEntry(Kind.ITEM, icon, ItemStack.EMPTY, Optional.empty(), 0, Optional.empty(), Optional.empty(), -1,
                Optional.empty(), Optional.empty());
    }

    // ---- Reading ----

    public boolean hasTint()
    {
        return tint >= 0;
    }

    // A TYPE drawer's transport type, if it's registered
    public @Nullable TransportType<?> transportType()
    {
        return typeId.flatMap(TransportTypes.REGISTRY::getOptional).orElse(null);
    }

    // A fluids TYPE drawer's fluid (to show the right amount), or null for the whole type
    public @Nullable Resource filter()
    {
        return fluid.flatMap(BuiltInRegistries.FLUID::getOptional).map(FluidResource::of).orElse(null);
    }

    // ---- Setting a drawer from what's held ----

    // The entry a drawer clicked with `held` gets, or null if it can't be set from it:
    // - a Trader's Ledger: its picked trade
    // - an item book (a storage book of items): the item on its open page (nothing on it: nothing set)
    // - a Book of Fluids / Experience / Energy: that type, library-wide
    // - any other Bibliomania book: that book
    // - anything else: that item
    // Books get their bibliomania:book_id here if they don't have one yet (server: `held` is the real stack).
    public static @Nullable DrawerEntry from(ItemStack held)
    {
        if (held.isEmpty()) return null;
        Item item = held.getItem();
        if (item instanceof VillagerBookItem)
        {
            VillagerContents contents = VillagerBookItem.contents(held);
            MerchantOffer offer = contents.currentOffer();
            if (offer == null) return null;
            int trade = Math.floorMod(contents.page(), contents.offers().size());
            return new DrawerEntry(Kind.TRADE, offer.getResult().copy(), offer.getCostA().copy(), Optional.of(bookId(held)), trade,
                    Optional.of(title(held)), Optional.empty(), EMERALD_BLOCK, Optional.empty(), Optional.empty());
        }
        if (item instanceof IPagedStorageItem paged)
        {
            PagedContents contents = paged.contents(held);
            ResourceStack<ItemResource> open = contents.stack(contents.selectedPage());
            return open.isEmpty() ? null : item(open.resource().toStack(1));
        }
        if (item instanceof StorageBookItem storageBook) return type(held, storageBook);
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof BookBlock) return book(held);
        return item(held.copyWithCount(1));
    }

    // A Book of Fluids / Experience / Energy: its whole type in the library
    private static @Nullable DrawerEntry type(ItemStack held, StorageBookItem storageBook)
    {
        TransportType<?> type = storageBook.book().type();
        Identifier typeId = type.getId();
        ItemStack book = new ItemStack(held.getItem());
        if (type == TransportTypes.FLUIDS.get())
        {
            // The fluid in it: its bucket as the icon, and only that fluid's amount shown
            ItemTransportStorage storage = ItemTransportStorage.open(held);
            Resource resource = storage == null ? null : storage.storage().getResource(0);
            if (!(resource instanceof FluidResource fluidResource) || fluidResource.isEmpty()) return null;
            Fluid fluid = fluidResource.getFluid();
            ItemStack bucket = new ItemStack(fluid.getBucket());
            Identifier fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            return new DrawerEntry(Kind.TYPE, bucket.isEmpty() || fluid == Fluids.EMPTY ? book : bucket, book, Optional.empty(), 0,
                    Optional.of(fluidResource.getHoverName()), Optional.of(type.getName()), -1, Optional.of(typeId), Optional.of(fluidId));
        }
        boolean energy = type == TransportTypes.ENERGY.get();
        ItemStack icon = energy ? new ItemStack(ItemRegistry.ENERGY_ICON.get())
                : type == TransportTypes.EXPERIENCE.get() ? new ItemStack(Items.EXPERIENCE_BOTTLE) : book;
        return new DrawerEntry(Kind.TYPE, icon, book, Optional.empty(), 0,
                Optional.of(type.getName()), Optional.empty(), -1, Optional.of(typeId), Optional.empty());
    }

    // Any other book: that one book, its icon showing what it holds
    private static DrawerEntry book(ItemStack held)
    {
        Item item = held.getItem();
        ItemStack icon = new ItemStack(item);
        // The book itself as the badge when the icon shows what's in it (crop, mob)
        ItemStack badge = ItemStack.EMPTY;
        Component subject = null;
        int tint = -1;
        if (item instanceof CropBookItem)
        {
            // The crop's own item (what pick-block gives for it: wheat seeds, sugar cane...)
            Optional<Item> seed = CropBookItem.contents(held).seed();
            if (seed.isPresent())
            {
                icon = new ItemStack(seed.get());
                badge = new ItemStack(item);
                subject = icon.getHoverName();
            }
        }
        else if (item instanceof MobBookItem || item instanceof EssenceBookItem)
        {
            Optional<EntityType<?>> type = item instanceof MobBookItem ? MobBookItem.contents(held).type() : EssenceBookItem.contents(held).type();
            if (type.isPresent())
            {
                icon = mobIcon(type.get());
                badge = new ItemStack(item);
                subject = type.get().getDescription();
            }
        }
        else if (item instanceof BarterBookItem)
        {
            icon = new ItemStack(Items.PIGLIN_HEAD);
            tint = GOLD_BLOCK;
        }
        return new DrawerEntry(Kind.BOOK, icon, badge, Optional.of(bookId(held)), 0,
                Optional.of(title(held)), Optional.ofNullable(subject), tint, Optional.empty(), Optional.empty());
    }

    // Vanilla's mob heads; any other mob its spawn egg (26.1+: byId gives an Optional holder of the egg item)
    private static final Map<EntityType<?>, Item> HEADS = Map.of(
            EntityTypes.ZOMBIE, Items.ZOMBIE_HEAD,
            EntityTypes.SKELETON, Items.SKELETON_SKULL,
            EntityTypes.WITHER_SKELETON, Items.WITHER_SKELETON_SKULL,
            EntityTypes.CREEPER, Items.CREEPER_HEAD,
            EntityTypes.PIGLIN, Items.PIGLIN_HEAD,
            EntityTypes.ENDER_DRAGON, Items.DRAGON_HEAD);

    private static ItemStack mobIcon(EntityType<?> type)
    {
        Item head = HEADS.get(type);
        if (head != null) return new ItemStack(head);
        return SpawnEggItem.byId(type).map(ItemStack::new).orElse(new ItemStack(Items.BOOK));
    }

    // "Grandad's Almanac (Farmer's Almanac)" for a renamed book; just "Farmer's Almanac" for one that isn't
    private static Component title(ItemStack book)
    {
        Component kind = new ItemStack(book.getItem()).getHoverName();
        if (!book.has(DataComponents.CUSTOM_NAME)) return kind;
        return Component.translatable("hud." + BibliomaniaReferences.MODID + ".drawer_title", book.getHoverName(), kind);
    }

    // The book's id, given one now if it has none
    public static UUID bookId(ItemStack book)
    {
        UUID id = book.get(DataComponentRegistry.BOOK_ID.get());
        if (id == null)
        {
            id = UUID.randomUUID();
            book.set(DataComponentRegistry.BOOK_ID.get(), id);
        }
        return id;
    }
}
