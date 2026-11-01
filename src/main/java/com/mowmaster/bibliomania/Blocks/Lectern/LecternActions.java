package com.mowmaster.bibliomania.Blocks.Lectern;

import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookActions;
import com.mowmaster.bibliomania.Blocks.CropBook.CropBookItem;
import com.mowmaster.bibliomania.Blocks.CropBook.CropContents;
import com.mowmaster.bibliomania.Blocks.CropBook.CropProfile;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookItem;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookActions;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerBookItem;
import com.mowmaster.bibliomania.Blocks.VillagerBook.VillagerContents;
import com.mowmaster.mowlib.paging.IPageable;
import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.transport.TransportType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.block.LevelEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

// The Reading Lectern standing in for a book: when its open page is a whole book (not a page of items), clicks on the
// lectern do what they'd do on that book placed on the ground, to the book on its shelf:
// - Farmer's Almanac: soil, seeds, boosts and yield items go in, bone meal grows it, buckets add or take water;
//   left-click harvests with the held tool (trees: axe or shears, as on the book)
// - Trader's Ledger: a price trades (the item bought goes to the player), an upgrade item upgrades;
//   sneak + an arrow turns to its next or previous trade
// - Books of Fluids and Energy: a container (bucket, tank, battery...) fills the book, or is filled from it
// Every method runs on the server and returns true if the click was used. Changes go back to the bookcase
// (BookcaseBlockEntity#bookUpdated), and the lectern re-reads its page.
public final class LecternActions
{
    private LecternActions() {}

    // Right-click with `held` on the lectern
    public static boolean use(ServerLevel level, Player player, InteractionHand hand, ItemStack held, ReadingLecternBlockEntity lectern)
    {
        ReadingLecternBlockEntity.Page page = lectern.currentPage();
        if (page == null || page.bookPage() >= 0) return false;
        BookcaseBlockEntity shelf = page.shelf();
        ItemStack book = shelf.getBook(page.slot());
        boolean used = false;
        if (book.getItem() instanceof CropBookItem) used = useCrop(level, player, hand, held, book, lectern.getBlockPos());
        else if (book.getItem() instanceof VillagerBookItem) used = useLedger(level, player, held, book, lectern.getBlockPos());
        else if (book.getItem() instanceof StorageBookItem storage) used = useStorage(player, hand, storage.book().type(), shelf, page.slot());
        if (used) done(level, shelf, page.slot(), lectern);
        return used;
    }

    // Left-click on the lectern: harvests an Almanac
    public static boolean attack(ServerLevel level, Player player, ReadingLecternBlockEntity lectern)
    {
        ReadingLecternBlockEntity.Page page = lectern.currentPage();
        if (page == null || page.bookPage() >= 0) return false;
        ItemStack book = page.shelf().getBook(page.slot());
        if (!(book.getItem() instanceof CropBookItem)) return false;
        CropContents contents = CropBookItem.contents(book);
        CropProfile profile = contents.profile();
        ItemStack tool = player.getMainHandItem();
        if (profile == null || !contents.grown() || !profile.canHarvestWith(tool)) return false;
        if (profile.tree() != null) tool.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
        for (ItemStack drop : CropBookActions.harvest(level, contents, lectern.getBlockPos(), tool, player))
        {
            StorageInteractions.giveToPlayer(player, drop);
        }
        CropBookItem.setContents(book, contents.withStage(0));
        done(level, page.shelf(), page.slot(), lectern);
        return true;
    }

    // Sneak + an arrow: turns the open book's own pages (a Ledger's trades), if it has any
    public static boolean turnBookPage(ServerLevel level, int direction, ReadingLecternBlockEntity lectern)
    {
        ReadingLecternBlockEntity.Page page = lectern.currentPage();
        if (page == null || page.bookPage() >= 0) return false;
        ItemStack book = page.shelf().getBook(page.slot());
        if (!(book.getItem() instanceof IPageable pageable)) return false;
        pageable.setPage(book, pageable.nextPage(book, direction));
        done(level, page.shelf(), page.slot(), lectern);
        return true;
    }

    private static void done(ServerLevel level, BookcaseBlockEntity shelf, int slot, ReadingLecternBlockEntity lectern)
    {
        shelf.bookUpdated(slot);
        lectern.refresh(level, true);
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

    // ---- Trader's Ledger ----

    private static boolean useLedger(ServerLevel level, Player player, ItemStack held, ItemStack book, BlockPos pos)
    {
        VillagerContents contents = VillagerBookItem.contents(book);
        if (!contents.hasVillager()) return false;
        if (VillagerBookActions.isUpgrade(held))
        {
            VillagerContents upgraded = VillagerBookActions.applyUpgrade(contents, held);
            level.playSound(null, pos, upgraded != null ? SoundEvents.VILLAGER_CELEBRATE : SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1f, 1f);
            if (upgraded == null) return true;
            VillagerBookItem.setContents(book, upgraded);
            held.consume(1, player);
            return true;
        }
        if (!VillagerBookActions.isPrice(contents, held)) return false;
        VillagerBookActions.Trade trade = VillagerBookActions.trade(level, player, contents);
        level.playSound(null, pos, trade != null ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1f, 1f);
        if (trade == null) return true;
        VillagerBookItem.setContents(book, trade.contents());
        StorageInteractions.giveToPlayer(player, trade.result());
        return true;
    }

    // ---- Books of Fluids and Energy ----

    // Moves between the held container and the book: into the book first, or if nothing went in, out of it
    private static <R extends Resource> boolean useStorage(Player player, InteractionHand hand, TransportType<R> type,
                                                           BookcaseBlockEntity shelf, int slot)
    {
        ResourceHandler<R> bookHandler = shelf.bookHandler(slot, type);
        // The held item's own handler of the type (a bucket's fluid, a battery's energy), through the hand it's in
        ResourceHandler<R> heldHandler = type.getItemHandler(ItemAccess.forPlayerInteraction(player, hand));
        if (bookHandler == null || heldHandler == null) return false;
        try (Transaction transaction = Transaction.openRoot())
        {
            int moved = type.transfer(heldHandler, bookHandler, Integer.MAX_VALUE / 2, transaction);
            if (moved <= 0) moved = type.transfer(bookHandler, heldHandler, Integer.MAX_VALUE / 2, transaction);
            if (moved <= 0) return false;
            transaction.commit();
            return true;
        }
    }
}
