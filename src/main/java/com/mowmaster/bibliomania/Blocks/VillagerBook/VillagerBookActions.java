package com.mowmaster.bibliomania.Blocks.VillagerBook;

import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

// What a Trader's Ledger does with its villager, shared by the placed book and the item.
// Trades are made by vanilla's own code, on a temporary villager (never added to the world) loaded with the book's
// villager data, offers and experience: since 26.1 trades are data (trade sets per profession and level), and a
// villager knows how to roll its own.
public final class VillagerBookActions
{
    private VillagerBookActions() {}

    // Experience a villager needs to reach the next level, by its current level (vanilla's thresholds: 10, 70, 150, 250)
    private static final int[] NEXT_LEVEL_XP = {0, 10, 70, 150, 250};
    public static final int MAX_LEVEL = 5;

    // ---- The temporary villager ----

    // A villager (not in the world) with the book's villager data and experience, and no trades yet: asking it for
    // its offers (server) makes it roll the trades for its profession at its current level
    // (`data` null: a new villager's own, plains with no profession)
    public static @Nullable Villager temporaryVillager(Level level, @Nullable VillagerData data, int xp)
    {
        Entity entity = MobBookActions.createTemporary(EntityTypes.VILLAGER, level, EntitySpawnReason.TRIGGERED);
        if (!(entity instanceof Villager villager)) return null;
        if (data != null) villager.setVillagerData(data);
        villager.setVillagerXp(xp);
        return villager;
    }

    // A wandering trader (not in the world), for drawing a book that holds one
    public static @Nullable WanderingTrader temporaryWanderingTrader(Level level)
    {
        Entity entity = MobBookActions.createTemporary(EntityTypes.WANDERING_TRADER, level, EntitySpawnReason.TRIGGERED);
        return entity instanceof WanderingTrader trader ? trader : null;
    }

    // ---- Capturing ----

    // Grown villagers (not babies) and wandering traders, and not ones that are trading with someone
    public static boolean canCapture(Entity target)
    {
        if (target instanceof Villager villager) return !villager.isBaby() && !villager.isTrading();
        if (target instanceof WanderingTrader trader) return !trader.isTrading();
        return false;
    }

    // A wandering trader into the book, with its trades (keeping the book's upgrades). It has no villager data of its
    // own, so a plain villager's stands in (never shown or used). Null if that villager couldn't be made.
    public static @Nullable VillagerContents capture(VillagerContents book, WanderingTrader trader)
    {
        Villager placeholder = temporaryVillager(trader.level(), null, 0);
        if (placeholder == null) return null;
        MerchantOffers offers = trader.getOffers().copy();
        applyDiscounts(offers, book.discounts());
        return book.withWanderingTrader(placeholder.getVillagerData(), offers);
    }

    // The villager into the book (keeping the book's upgrades). Server: getOffers rolls its first trades if it has
    // none yet.
    public static VillagerContents capture(VillagerContents book, Villager villager)
    {
        VillagerData data = villager.getVillagerData();
        MerchantOffers offers = villager.getOffers().copy();
        applyDiscounts(offers, book.discounts());
        return book.withVillager(data, offers, villager.getVillagerXp(), workstationFor(data.profession()));
    }

    // ---- Professions and workstations ----

    // The profession whose job site this block is (a lectern -> librarian...), if any. A block's point-of-interest
    // type (PoiTypes.forState) is what villagers look for; each profession says which one is its job site.
    public static Optional<Holder<VillagerProfession>> professionFor(BlockState state)
    {
        Optional<Holder<PoiType>> poi = PoiTypes.forState(state);
        if (poi.isEmpty()) return Optional.empty();
        // listElements: every registered profession, as registry entries (Holder.Reference)
        return BuiltInRegistries.VILLAGER_PROFESSION.listElements()
                .filter(profession -> profession.value().heldJobSite().test(poi.get()))
                .map(profession -> (Holder<VillagerProfession>) profession)
                .findFirst();
    }

    // A block that is this profession's job site (for showing), if any
    public static Optional<Block> workstationFor(Holder<VillagerProfession> profession)
    {
        return BuiltInRegistries.POINT_OF_INTEREST_TYPE.listElements()
                .filter(poi -> profession.value().heldJobSite().test(poi))
                .flatMap(poi -> poi.value().matchingStates().stream())
                .map(BlockState::getBlock)
                .findFirst();
    }

    // The villager set to a new profession at its workstation: level 1, no experience, new trades (rolled by
    // vanilla for that profession). Null if the book is empty, holds a wandering trader, or the villager couldn't be made.
    public static @Nullable VillagerContents setProfession(ServerLevel level, VillagerContents contents, Holder<VillagerProfession> profession, Block block)
    {
        if (contents.villager().isEmpty() || contents.wandering()) return null;
        VillagerData data = contents.villager().get().withProfession(profession).withLevel(1);
        Villager villager = temporaryVillager(level, data, 0);
        if (villager == null) return null;
        // A new villager has no offers: getOffers rolls the level 1 trades for the new profession
        MerchantOffers offers = villager.getOffers().copy();
        applyDiscounts(offers, contents.discounts());
        return contents.withVillager(data, offers, 0, Optional.of(block));
    }

    // A fresh villager of the same type: no profession, level 1, no trades (crafting resets it). The book's upgrades
    // stay.
    public static VillagerContents reset(VillagerContents contents)
    {
        if (contents.villager().isEmpty() || contents.wandering()) return contents;
        Holder<VillagerProfession> none = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE);
        VillagerData data = contents.villager().get().withProfession(none).withLevel(1);
        return contents.withVillager(data, new MerchantOffers(), 0, Optional.empty());
    }

    // ---- Levels (vanilla's names and experience) ----

    // "Novice", "Apprentice", "Journeyman", "Expert", "Master": vanilla's own lang keys merchant.level.1 to 5
    public static Component levelName(int level)
    {
        return Component.translatable("merchant.level." + Math.clamp(level, 1, MAX_LEVEL));
    }

    // How far to the next level, 0 to 100 (100 at Master)
    public static int percentToNextLevel(int level, int xp)
    {
        if (level >= MAX_LEVEL) return 100;
        int from = NEXT_LEVEL_XP[level - 1];
        int to = NEXT_LEVEL_XP[level];
        return Math.clamp((int) Math.floor(100.0 * (xp - from) / (to - from)), 0, 100);
    }

    // ---- Upgrades: discounts and faster restocking ----

    public static boolean isUpgrade(ItemStack stack)
    {
        return ServerConfig.isVillagerDiscountItem(stack) || ServerConfig.isVillagerRestockItem(stack);
    }

    // The book with one more upgrade from `stack`, or null if it can't take another (the discount item has a limit,
    // and isn't used when there's nothing left to take off)
    public static @Nullable VillagerContents applyUpgrade(VillagerContents contents, ItemStack stack)
    {
        if (ServerConfig.isVillagerDiscountItem(stack))
        {
            if (contents.discounts() >= ServerConfig.villagerMaxDiscounts()) return null;
            VillagerContents upgraded = contents.withDiscount();
            MerchantOffers offers = upgraded.offers().copy();
            applyDiscounts(offers, upgraded.discounts());
            return upgraded.withOffers(offers, upgraded.restockAt());
        }
        if (ServerConfig.isVillagerRestockItem(stack)) return contents.withSpeedUp();
        return null;
    }

    // Takes ServerConfig.villagerDiscountAmount() emeralds per discount off every trade whose first price is emeralds,
    // never below ServerConfig.villagerMinEmeraldCost(). It uses the offer's "special price" (the same field vanilla's
    // cured-zombie and Hero of the Village discounts use), and never undoes a bigger discount the villager already had.
    public static void applyDiscounts(MerchantOffers offers, int discounts)
    {
        if (discounts <= 0) return;
        for (MerchantOffer offer : offers)
        {
            ItemStack base = offer.getBaseCostA();
            if (!base.is(Items.EMERALD)) continue;
            int off = Math.min(discounts * ServerConfig.villagerDiscountAmount(), base.getCount() - ServerConfig.villagerMinEmeraldCost());
            if (off > 0) offer.setSpecialPriceDiff(Math.min(offer.getSpecialPriceDiff(), -off));
        }
    }

    // Ticks until a restock: the config's 10 minutes, shortened by each restock item in turn (1% each by default:
    // 0.99 x 0.99 x ...); never under a second
    public static int restockTicks(VillagerContents contents)
    {
        double factor = Math.pow(1.0 - ServerConfig.villagerRestockPercent() / 100.0, contents.speedUps());
        return (int) Math.max(20, Math.round(ServerConfig.villagerRestockInterval() * factor));
    }

    // ---- Trading ----

    // Whether `stack` is one of the current trade's prices (so a right-click with it on the book is a trade)
    public static boolean isPrice(VillagerContents contents, ItemStack stack)
    {
        MerchantOffer offer = contents.currentOffer();
        if (offer == null || stack.isEmpty()) return false;
        return offer.getItemCostA().test(stack) || offer.getItemCostB().map(cost -> cost.test(stack)).orElse(false);
    }

    // The trade on show, once: both prices are taken from the player's inventory (all of it, not just the hand), the
    // villager gets the trade's experience (levelling up as vanilla would, new trades and all; no experience orbs),
    // and the restock timer starts. Returns the new contents and the item bought, or null if it can't be made
    // (sold out, or the player can't pay).
    public record Trade(VillagerContents contents, ItemStack result) {}

    public static @Nullable Trade trade(ServerLevel level, Player player, VillagerContents contents)
    {
        MerchantOffer offer = contents.currentOffer();
        if (offer == null || offer.isOutOfStock() || contents.villager().isEmpty()) return null;
        ItemCost costA = offer.getItemCostA();
        int countA = offer.getCostA().getCount();
        Optional<ItemCost> costB = offer.getItemCostB();
        int countB = offer.getCostB().getCount();
        if (count(player, costA::test) < countA) return null;
        if (costB.isPresent() && count(player, costB.get()::test) < countB) return null;
        take(player, costA::test, countA);
        costB.ifPresent(cost -> take(player, cost::test, countB));

        // The trade is made on a copy of the offers (data components never change in place)
        MerchantOffers offers = contents.offers().copy();
        MerchantOffer used = offers.get(Math.floorMod(contents.page(), offers.size()));
        ItemStack result = used.assemble();
        used.increaseUses();

        VillagerData data = contents.villager().get();
        int xp = contents.xp() + used.getXp();
        // Level up (maybe more than once): each new level's trades are added to the ones it already has. Wandering
        // traders have no levels (or experience).
        if (contents.wandering()) xp = 0;
        while (!contents.wandering() && data.level() < MAX_LEVEL && xp >= NEXT_LEVEL_XP[data.level()])
        {
            data = data.withLevel(data.level() + 1);
            addLevelTrades(level, data, offers, contents.discounts());
        }
        VillagerContents next = contents.withTrade(data, offers, xp);
        if (next.restockAt() == 0) next = next.withRestockAt(level.getGameTime() + restockTicks(next));
        return new Trade(next, result);
    }

    // A level's own trades, added to `offers`: a new villager at that level, asked for its offers, rolls exactly that
    // level's trades (vanilla's own trade sets), which are copied across. Discounts are applied to them too.
    private static void addLevelTrades(ServerLevel level, VillagerData data, MerchantOffers offers, int discounts)
    {
        Villager villager = temporaryVillager(level, data, 0);
        if (villager == null) return;
        MerchantOffers added = villager.getOffers().copy();
        applyDiscounts(added, discounts);
        offers.addAll(added);
    }

    // Every trade usable again
    public static VillagerContents restock(VillagerContents contents)
    {
        MerchantOffers offers = contents.offers().copy();
        for (MerchantOffer offer : offers) offer.resetUses();
        return contents.withOffers(offers, 0L);
    }

    // ---- The player's items ----

    // The main inventory and the offhand (not armour)
    private static boolean usable(int slot)
    {
        return slot < Inventory.INVENTORY_SIZE || slot == Inventory.SLOT_OFFHAND;
    }

    private static int count(Player player, Predicate<ItemStack> matches)
    {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (usable(slot) && !stack.isEmpty() && matches.test(stack)) total += stack.getCount();
        }
        return total;
    }

    private static void take(Player player, Predicate<ItemStack> matches, int amount)
    {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (!usable(slot) || stack.isEmpty() || !matches.test(stack)) continue;
            int taken = Math.min(amount, stack.getCount());
            stack.shrink(taken);
            amount -= taken;
        }
        inventory.setChanged();
    }
}
