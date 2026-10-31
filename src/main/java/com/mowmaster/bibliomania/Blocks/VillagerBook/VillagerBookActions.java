package com.mowmaster.bibliomania.Blocks.VillagerBook;

import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

// What a Trader's Ledger does with its villager, shared by the placed book and the item.
// Trades are made by vanilla's own code, on a temporary villager (never added to the world) loaded with the book's
// villager data, offers and experience: since 26.1 trades are data (trade sets per profession and level), and a
// villager knows how to roll its own.
public final class VillagerBookActions
{
    private VillagerBookActions() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    // Experience a villager needs to reach the next level, by its current level (vanilla's thresholds: 10, 70, 150, 250)
    private static final int[] NEXT_LEVEL_XP = {0, 10, 70, 150, 250};
    public static final int MAX_LEVEL = 5;

    // ---- The temporary villager ----

    // A villager (not in the world) with the book's data, offers and experience
    public static @Nullable Villager temporaryVillager(Level level, VillagerData data, @Nullable MerchantOffers offers, int xp)
    {
        Entity entity = MobBookActions.createTemporary(EntityTypes.VILLAGER, level, EntitySpawnReason.TRIGGERED);
        if (!(entity instanceof Villager villager)) return null;
        villager.setVillagerData(data);
        if (offers != null) villager.overrideOffers(offers.copy());
        villager.setVillagerXp(xp);
        return villager;
    }

    // ---- Capturing ----

    // Grown villagers only (not babies), and not ones that are trading with someone
    public static boolean canCapture(Entity target)
    {
        return target instanceof Villager villager && !villager.isBaby() && !villager.isTrading();
    }

    // The villager as book contents (server: getOffers rolls its first trades if it has none yet)
    public static VillagerContents capture(Villager villager)
    {
        VillagerData data = villager.getVillagerData();
        return VillagerContents.EMPTY.withVillager(data, villager.getOffers().copy(), villager.getVillagerXp(), workstationFor(data.profession()));
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
    // vanilla for that profession). Null if the book is empty or the villager couldn't be made.
    public static @Nullable VillagerContents setProfession(ServerLevel level, VillagerContents contents, Holder<VillagerProfession> profession, Block block)
    {
        if (contents.villager().isEmpty()) return null;
        VillagerData data = contents.villager().get().withProfession(profession).withLevel(1);
        Villager villager = temporaryVillager(level, data, null, 0);
        if (villager == null) return null;
        // No offers set: getOffers rolls the level 1 trades for the new profession
        return contents.withVillager(data, villager.getOffers().copy(), 0, Optional.of(block));
    }

    // A fresh villager of the same type: no profession, level 1, no trades (crafting resets it)
    public static VillagerContents reset(VillagerContents contents)
    {
        if (contents.villager().isEmpty()) return contents;
        Holder<VillagerProfession> none = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE);
        VillagerData data = contents.villager().get().withProfession(none).withLevel(1);
        return VillagerContents.EMPTY.withVillager(data, new MerchantOffers(), 0, Optional.empty());
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

    public static @Nullable Trade trade(ServerLevel level, Player player, VillagerContents contents, int restockTicks)
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
        // Level up (maybe more than once), and each new level adds its trades
        while (data.level() < MAX_LEVEL && xp >= NEXT_LEVEL_XP[data.level()])
        {
            data = data.withLevel(data.level() + 1);
            offers = addLevelTrades(level, data, offers, xp);
        }
        VillagerContents next = contents.withTrade(data, offers, xp);
        if (next.restockAt() == 0) next = next.withRestockAt(level.getGameTime() + restockTicks);
        return new Trade(next, result);
    }

    // Vanilla adds a level's trades in Villager#updateTrades, which is protected (only villagers call it). Reflection
    // finds and calls it anyway: getDeclaredMethod looks it up by name on the class, setAccessible lets us call it.
    // NeoForge runs with Mojang's names, so the name is the same in development and in a real game.
    private static @Nullable Method updateTrades;

    private static MerchantOffers addLevelTrades(ServerLevel level, VillagerData data, MerchantOffers offers, int xp)
    {
        Villager villager = temporaryVillager(level, data, offers, xp);
        if (villager == null) return offers;
        try
        {
            if (updateTrades == null)
            {
                updateTrades = Villager.class.getDeclaredMethod("updateTrades", ServerLevel.class);
                updateTrades.setAccessible(true);
            }
            updateTrades.invoke(villager, level);
            return villager.getOffers().copy();
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            LOGGER.warn("Trader's Ledger couldn't add trades for villager level {}: {}", data.level(), exception.toString());
            return offers;
        }
    }

    // Every trade usable again
    public static VillagerContents restock(VillagerContents contents)
    {
        MerchantOffers offers = contents.offers().copy();
        for (MerchantOffer offer : offers) offer.resetUses();
        return new VillagerContents(contents.villager(), offers, contents.xp(), contents.workstation(), contents.page(), 0L);
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
