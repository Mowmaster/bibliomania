package com.mowmaster.bibliomania.Blocks.BarterBook;

import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// What a Book of Barter does, shared by the placed book and the item: gold in, and every ServerConfig.barterInterval()
// ticks (6 seconds) a barter from vanilla's piglin bartering loot table, one roll per gold.
public final class BarterActions
{
    private BarterActions() {}

    // ---- Gold and upgrades ----

    // Gold a stack is worth per item (ingots 1, blocks 9), 0 for anything else
    public static int goldValue(ItemStack stack)
    {
        if (stack.is(Items.GOLD_INGOT)) return 1;
        if (stack.is(Items.GOLD_BLOCK)) return 9;
        return 0;
    }

    // How many of `stack` fit (the book holds up to ServerConfig.barterMaxGold())
    public static int goldItemsThatFit(BarterContents contents, ItemStack stack)
    {
        int value = goldValue(stack);
        if (value == 0) return 0;
        int room = ServerConfig.barterMaxGold() - contents.gold();
        return Math.max(0, Math.min(stack.getCount(), room / value));
    }

    public static boolean isUpgrade(ItemStack stack)
    {
        return ServerConfig.isBarterSpeedItem(stack) || ServerConfig.isBarterYieldItem(stack);
    }

    // The book with one more upgrade from `stack`, or null if it can't take another
    public static @Nullable BarterContents applyUpgrade(BarterContents contents, ItemStack stack)
    {
        if (ServerConfig.isBarterSpeedItem(stack)) return contents.withSpeedUp();
        if (ServerConfig.isBarterYieldItem(stack) && contents.yields() < ServerConfig.barterMaxYields()) return contents.withYield();
        return null;
    }

    // Ticks between barters: the config's 6 seconds, shortened by each speed item in turn (5% each by default:
    // 0.95 x 0.95 x ...); never under a quarter of a second
    public static int interval(BarterContents contents)
    {
        double factor = Math.pow(1.0 - ServerConfig.barterSpeedPercent() / 100.0, contents.speedUps());
        return (int) Math.max(5, Math.round(ServerConfig.barterInterval() * factor));
    }

    // ---- Bartering ----

    // The result of a tick: the contents to keep (null = unchanged) and the items bartered (often none)
    public record Result(@Nullable BarterContents contents, List<ItemStack> items) {}
    private static final Result NOTHING = new Result(null, List.of());

    // Every tick (placed, or in a player's inventory). With gold and no timer: start it. Timer done: barter up to
    // perBarter() times (one gold each, as far as the gold goes), then restart the timer if gold is left.
    public static Result tick(ServerLevel level, BarterContents contents, Vec3 at)
    {
        long now = level.getGameTime();
        // No piglin, or no gold: nothing to do (and no timer)
        if (!contents.piglin() || contents.gold() <= 0) return contents.nextDrop() != 0 ? new Result(contents.withNextDrop(0), List.of()) : NOTHING;
        if (contents.nextDrop() == 0) return new Result(contents.withNextDrop(now + interval(contents)), List.of());
        if (now < contents.nextDrop()) return NOTHING;

        int rolls = Math.min(contents.gold(), contents.perBarter());
        List<ItemStack> items = barter(level, rolls, at);
        BarterContents next = contents.withGold(contents.gold() - rolls);
        next = next.withNextDrop(next.gold() > 0 ? now + interval(next) : 0);
        return new Result(next, items);
    }

    // `rolls` rolls of vanilla's piglin bartering loot table, for a temporary piglin standing at `at` (the table is
    // rolled the way a real piglin does when it's given gold, so datapacks that change bartering change this too)
    private static List<ItemStack> barter(ServerLevel level, int rolls, Vec3 at)
    {
        List<ItemStack> items = new ArrayList<>();
        Entity piglin = MobBookActions.createTemporary(EntityTypes.PIGLIN, level, EntitySpawnReason.TRIGGERED);
        if (piglin == null) return items;
        piglin.setPos(at.x, at.y, at.z);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PIGLIN_BARTERING);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, piglin)
                .create(LootContextParamSets.PIGLIN_BARTER);
        for (int roll = 0; roll < rolls; roll++) items.addAll(table.getRandomItems(params));
        return items;
    }
}
