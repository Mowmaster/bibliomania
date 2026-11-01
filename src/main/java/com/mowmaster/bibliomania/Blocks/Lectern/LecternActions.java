package com.mowmaster.bibliomania.Blocks.Lectern;

import com.mowmaster.bibliomania.Blocks.BarterBook.BarterBookItem;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookActions;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookItem;
import com.mowmaster.bibliomania.Blocks.CropBook.CropContents;
import com.mowmaster.bibliomania.Blocks.CropBook.CropProfile;
import com.mowmaster.bibliomania.Blocks.EssenceBook.EssenceBookItem;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookItem;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookBlock;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookItem;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookActions;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookItem;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerContents;
import com.mowmaster.mowlib.paging.IPageable;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.transport.TransportContents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

// The Reading Lectern standing in for the whole book on its open page: clicks on the lectern do what that book does,
// to the book on its shelf. Each book's own code does the work:
// - Farmer's Almanac: soil, seeds, boosts and yield items, bone meal, water buckets; left-click harvests with the held
//   tool (trees: axe or shears)
// - Trader's Ledger: a price trades, an upgrade upgrades; sneak + the arrows or page keys picks the trade
// - Book of Barter: gold for the piglin (and upgrades): it barters from that gold, the goods going into the cases
// - Bestiary and Book of Essence: their inventory clicks: breeding food, dyes, boosts, milking containers, shears,
//   swords (left-click with a sword kills too)
// - Books of Fluids, Experience and Energy: their placed-book actions (buckets, bottles, tanks, batteries; an empty
//   hand stores your experience, left-click takes it back), run on a block entity copy of the book (see placed)
// Everything runs on the server; true if the click did something. Changes go back to the bookcase
// (BookcaseBlockEntity#bookUpdated) and the lectern re-reads its page.
public final class LecternActions
{
    private LecternActions() {}

    // Right-click on the lectern with `held` (EMPTY: an empty hand)
    public static boolean use(ServerLevel level, Player player, InteractionHand hand, ItemStack held, ReadingLecternBlockEntity lectern)
    {
        ReadingLecternBlockEntity.Page page = lectern.currentPage();
        if (page == null || page.bookPage() >= 0) return false;
        BookcaseBlockEntity shelf = page.shelf();
        ItemStack book = shelf.getBook(page.slot());
        BlockPos pos = lectern.getBlockPos();
        boolean used = false;
        if (book.getItem() instanceof CropBookItem) used = useCrop(level, player, hand, held, book, pos);
        else if (book.getItem() instanceof VillagerBookItem) used = useLedger(level, player, held, book, pos);
        else if (book.getItem() instanceof BarterBookItem) used = BarterBookItem.lecternUse(book, held, player);
        else if (book.getItem() instanceof MobBookItem) used = MobBookItem.lecternUse(book, held, player);
        else if (book.getItem() instanceof EssenceBookItem) used = EssenceBookItem.lecternUse(book, held, player);
        else if (book.getItem() instanceof StorageBookItem storage)
        {
            // An empty hand counts unless the book passes; an item when the book used it (not "try with empty hand")
            used = placed(level, book, pos, copy -> held.isEmpty()
                    ? storage.book().behaviour().useWithoutItemPlaced(copy, level, pos, player) != InteractionResult.PASS
                    : storage.book().behaviour().useItemOnPlaced(held, copy, level, pos, player, hand).consumesAction());
        }
        if (used) done(level, shelf, page.slot(), lectern);
        return used;
    }

    // Left-click on the lectern
    public static boolean attack(ServerLevel level, Player player, ReadingLecternBlockEntity lectern)
    {
        ReadingLecternBlockEntity.Page page = lectern.currentPage();
        if (page == null || page.bookPage() >= 0) return false;
        BookcaseBlockEntity shelf = page.shelf();
        ItemStack book = shelf.getBook(page.slot());
        ItemStack held = player.getMainHandItem();
        BlockPos pos = lectern.getBlockPos();
        boolean used = false;
        if (book.getItem() instanceof CropBookItem) used = harvestCrop(level, player, book, pos);
        // A sword's kill (and shears, milking) are the books' right-click actions in an inventory
        else if (book.getItem() instanceof MobBookItem) used = MobBookItem.lecternUse(book, held, player);
        else if (book.getItem() instanceof EssenceBookItem) used = EssenceBookItem.lecternUse(book, held, player);
        else if (book.getItem() instanceof StorageBookItem storage)
        {
            used = placed(level, book, pos, copy -> storage.book().behaviour().leftClickPlaced(copy, level, pos, player));
        }
        if (used) done(level, shelf, page.slot(), lectern);
        return used;
    }

    // Sneak + an arrow or page key: turns the open book's own pages (a Ledger's trades), if it has any
    public static boolean turnBookPage(ServerLevel level, int direction, ReadingLecternBlockEntity lectern)
    {
        ReadingLecternBlockEntity.Page page = lectern.currentPage();
        if (page == null || page.bookPage() >= 0) return false;
        ItemStack book = page.shelf().getBook(page.slot());
        if (!(book.getItem() instanceof IPageable pageable) || pageable.getPageCount(book) <= 1) return false;
        pageable.setPage(book, pageable.nextPage(book, direction));
        done(level, page.shelf(), page.slot(), lectern);
        return true;
    }

    private static void done(ServerLevel level, BookcaseBlockEntity shelf, int slot, ReadingLecternBlockEntity lectern)
    {
        shelf.bookUpdated(slot);
        lectern.refresh(level, true);
    }

    // ---- Storage books: their placed actions on a copy ----

    // A storage book's placed actions need its block entity: a copy is made from the book (at the lectern's position,
    // never added to the level), the action runs on it, and its contents (mowlib:transport_contents) are copied back
    // onto the shelved book. (BookBlock#updateThickness won't touch the lectern's block.)
    // Function<A, B>: a lambda taking an A and returning a B (here: the copy in, "did it do something" out)
    private static boolean placed(ServerLevel level, ItemStack book, BlockPos pos, Function<StorageBookBlockEntity, Boolean> action)
    {
        if (!(book.getItem() instanceof StorageBookItem item)) return false;
        StorageBookBlock block = item.book();
        BlockState state = block.defaultBlockState();
        BlockEntity created = block.newBlockEntity(pos, state);
        if (!(created instanceof StorageBookBlockEntity copy)) return false;
        copy.setLevel(level);
        copy.applyComponentsFromItemStack(book);
        if (!action.apply(copy)) return false;
        @Nullable TransportContents<?> contents = copy.toItem().get(MowLibDataComponents.TRANSPORT_CONTENTS.get());
        if (contents == null) book.remove(MowLibDataComponents.TRANSPORT_CONTENTS.get());
        else book.set(MowLibDataComponents.TRANSPORT_CONTENTS.get(), contents);
        return true;
    }

    // ---- Farmer's Almanac ----

    private static boolean useCrop(ServerLevel level, Player player, InteractionHand hand, ItemStack held, ItemStack book, BlockPos pos)
    {
        CropContents contents = CropBookItem.contents(book);
        if (CropBookActions.isBoneMeal(held))
        {
            CropBookActions.BoneMeal grown = CropBookActions.boneMeal(contents, level.getRandom());
            if (grown == null) return false;
            CropBookItem.setContents(book, grown.contents());
            for (ItemStack drop : grown.drops()) StorageInteractions.giveToPlayer(player, drop);
            held.consume(1, player);
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
            return true;
        }
        CropContents watered = CropBookActions.useBucket(contents, held);
        if (watered != null)
        {
            CropBookItem.setContents(book, watered);
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, CropBookActions.bucketAfter(held)));
            level.playSound(null, pos, watered.water() ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
            return true;
        }
        CropContents planted = CropBookActions.insert(contents, held);
        if (planted == null) return false;
        CropBookItem.setContents(book, planted);
        held.consume(1, player);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    private static boolean harvestCrop(ServerLevel level, Player player, ItemStack book, BlockPos pos)
    {
        CropContents contents = CropBookItem.contents(book);
        CropProfile profile = contents.profile();
        ItemStack tool = player.getMainHandItem();
        if (profile == null || !contents.grown() || !profile.canHarvestWith(tool)) return false;
        if (profile.tree() != null) tool.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
        for (ItemStack drop : CropBookActions.harvest(level, contents, pos, tool, player))
        {
            StorageInteractions.giveToPlayer(player, drop);
        }
        CropBookItem.setContents(book, contents.withStage(0));
        return true;
    }

    // ---- Trader's Ledger ----

    private static boolean useLedger(ServerLevel level, Player player, ItemStack held, ItemStack book, BlockPos pos)
    {
        VillagerContents contents = VillagerBookItem.contents(book);
        if (!contents.hasVillager()) return false;
        if (VillagerBookActions.isUpgrade(held))
        {
            VillagerContents upgraded = VillagerBookActions.applyUpgrade(contents, held);
            level.playSound(null, pos, upgraded != null ? SoundEvents.VILLAGER_CELEBRATE : SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1f, 1f);
            if (upgraded == null) return false;
            VillagerBookItem.setContents(book, upgraded);
            held.consume(1, player);
            return true;
        }
        if (!VillagerBookActions.isPrice(contents, held)) return false;
        VillagerBookActions.Trade trade = VillagerBookActions.trade(level, player, contents);
        level.playSound(null, pos, trade != null ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1f, 1f);
        if (trade == null) return false;
        VillagerBookItem.setContents(book, trade.contents());
        StorageInteractions.giveToPlayer(player, trade.result());
        return true;
    }
}
