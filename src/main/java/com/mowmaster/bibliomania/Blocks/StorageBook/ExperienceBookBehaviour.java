package com.mowmaster.bibliomania.Blocks.StorageBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.ServerConfig;
import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.transport.AmountResource;
import com.mowmaster.mowlib.transport.ExperienceTransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.mowlib.util.MowLibXpUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// The Book of Experience, in experience points (MowLib's ExperienceTransportType; MowLibXpUtils does the level maths).
// - Placed: right-click stores all your experience; left-click gives you 1 level, sneak + left-click 10;
//   right-click with bottles o' enchanting stores them (3-11 points for one, 7 each for a stack; glass bottles back);
//   left-click with glass bottles fills them (11 points each). Experience orbs nearby fly to the book.
// - Held: the same: right-click stores all, left-click 1 level (sneak 10); sneak + right-click on a block places it
// - Inventory: left-click the book stores all your experience, right-click takes 5 levels;
//   bottles o' enchanting (left-click, either way round) are stored; glass bottles (right-click) are filled
public class ExperienceBookBehaviour implements StorageBookBehaviour
{
    public static final int POINTS_PER_BOTTLE = 11;
    // Ticks between left-click withdrawals (holding left-click swings/clicks every few ticks)
    private static final int CLICK_DELAY = 5;

    private static ResourceHandler<AmountResource> xp(BookStorage storage)
    {
        return storage.handler(TransportTypes.EXPERIENCE.get());
    }

    // ---- Placed ----

    @Override
    public InteractionResult useItemOnPlaced(ItemStack held, StorageBookBlockEntity book, Level level, BlockPos pos, Player player, InteractionHand hand)
    {
        if (!held.is(Items.EXPERIENCE_BOTTLE)) return InteractionResult.TRY_WITH_EMPTY_HAND; // -> stores all (below)
        if (!level.isClientSide() && storeBottles(book.storage(TransportTypes.EXPERIENCE.get()), held.getCount(), level.getRandom()))
        {
            int count = held.getCount();
            held.consume(count, player);
            StorageInteractions.giveToPlayer(player, new ItemStack(Items.GLASS_BOTTLE, count));
            orbSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useWithoutItemPlaced(StorageBookBlockEntity book, Level level, BlockPos pos, Player player)
    {
        if (!level.isClientSide() && storeAll(player, book.storage(TransportTypes.EXPERIENCE.get())) > 0) orbSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean leftClickPlaced(StorageBookBlockEntity book, Level level, BlockPos pos, Player player)
    {
        if (level.isClientSide() || !book.clickReady(CLICK_DELAY)) return true;
        ResourceHandler<AmountResource> storage = book.storage(TransportTypes.EXPERIENCE.get());
        ItemStack held = player.getMainHandItem();
        if (held.is(Items.GLASS_BOTTLE)) fillBottles(player, storage, held);
        else if (giveLevels(player, storage, player.isShiftKeyDown() ? 10 : 1) > 0) orbSound(level, pos);
        return true;
    }

    // Experience orbs within ServerConfig.experiencePullRange() fly to the book (vanilla orbs fly to players the same
    // way), and are absorbed when they touch it. Every other tick, to keep it cheap.
    @Override
    public void serverTickPlaced(StorageBookBlockEntity book, ServerLevel level)
    {
        if (level.getGameTime() % 2 != 0) return;
        ResourceHandler<AmountResource> storage = book.storage(TransportTypes.EXPERIENCE.get());
        if (storage.getAmountAsLong(0) >= storage.getCapacityAsLong(0, ExperienceTransportType.EXPERIENCE)) return;
        BlockPos pos = book.getBlockPos();
        Vec3 target = Vec3.atCenterOf(pos);
        AABB touching = new AABB(pos).inflate(0.3);
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(ServerConfig.experiencePullRange())))
        {
            if (orb.getBoundingBox().intersects(touching))
            {
                // MowLib's experience type knows orbs: stores the whole orb (if it fits) and removes it
                TransportTypes.EXPERIENCE.get().absorbEntity(level, orb, storage, orb.getValue());
                continue;
            }
            Vec3 toBook = target.subtract(orb.position());
            // Slow down, then pull: speed 0.15 blocks a tick towards the book, a bit faster than an orb's own pull
            orb.setDeltaMovement(orb.getDeltaMovement().scale(0.6).add(toBook.normalize().scale(0.15)));
            // Tells the server to send the new motion to clients this tick (orbs are otherwise only synced rarely)
            orb.hurtMarked = true;
        }
    }

    // ---- Held ----

    // Players who right-clicked with the book this tick: the arm swing that follows a right-click mustn't count as
    // a left-click (see StorageBookEvents, which fills this, and swingHeld)
    static final Map<UUID, Long> LAST_RIGHT_CLICK = new HashMap<>();

    @Override
    public @Nullable InteractionResult useOnHeld(UseOnContext context)
    {
        Player player = context.getPlayer();
        return player == null || player.isShiftKeyDown() ? null : InteractionResult.PASS;
    }

    // CONSUME: handled, but the arm doesn't swing (a swing would be read as a left-click)
    @Override
    public InteractionResult useHeld(Level level, Player player, InteractionHand hand)
    {
        if (!level.isClientSide())
        {
            BookStorage storage = BookStorage.open(player.getItemInHand(hand));
            if (storage != null && storeAll(player, xp(storage)) > 0) orbSound(level, player.blockPosition());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean swingHeld(ItemStack book, Player player, InteractionHand hand)
    {
        long now = player.level().getGameTime();
        Long rightClick = LAST_RIGHT_CLICK.get(player.getUUID());
        if (rightClick != null && now - rightClick <= 1) return false;
        if (player.getCooldowns().isOnCooldown(book)) return false;
        player.getCooldowns().addCooldown(book, CLICK_DELAY);
        BookStorage storage = BookStorage.open(book);
        if (storage != null && giveLevels(player, xp(storage), player.isShiftKeyDown() ? 10 : 1) > 0)
        {
            orbSound(player.level(), player.blockPosition());
        }
        return true;
    }

    // ---- Inventory ----

    @Override
    public boolean clickedWith(ItemStack book, ItemStack cursor, Slot slot, ClickAction action, Player player)
    {
        BookStorage storage = BookStorage.open(book);
        if (storage == null) return false;
        boolean server = !player.level().isClientSide();
        if (cursor.isEmpty())
        {
            // Left-click: store all; right-click: take 5 levels (move the book with shift-click or number keys)
            if (server)
            {
                int moved = action == ClickAction.PRIMARY ? storeAll(player, xp(storage)) : giveLevels(player, xp(storage), 5);
                if (moved > 0) orbSound(player.level(), player.blockPosition());
            }
            return true;
        }
        return useBottles(storage, cursor, action, player, server);
    }

    @Override
    public boolean clickedOnto(ItemStack book, Slot slot, ClickAction action, Player player)
    {
        BookStorage storage = BookStorage.open(book);
        if (storage == null || slot.getItem().isEmpty()) return false;
        boolean handled = useBottles(storage, slot.getItem(), action, player, !player.level().isClientSide());
        if (handled) slot.setChanged();
        return handled;
    }

    // Bottles o' enchanting + left-click are stored (glass bottles back, into the same stack's place);
    // glass bottles + right-click are filled (the filled ones go into the inventory)
    private static boolean useBottles(BookStorage storage, ItemStack bottles, ClickAction action, Player player, boolean server)
    {
        if (action == ClickAction.PRIMARY && bottles.is(Items.EXPERIENCE_BOTTLE))
        {
            if (server && storeBottles(xp(storage), bottles.getCount(), player.getRandom()))
            {
                int count = bottles.getCount();
                bottles.shrink(count);
                StorageInteractions.giveToPlayer(player, new ItemStack(Items.GLASS_BOTTLE, count));
                orbSound(player.level(), player.blockPosition());
            }
            return true;
        }
        if (action == ClickAction.SECONDARY && bottles.is(Items.GLASS_BOTTLE))
        {
            if (server) fillBottles(player, xp(storage), bottles);
            return true;
        }
        return false;
    }

    // ---- The experience maths ----

    // All of the player's experience into the book (as much as fits). Returns the points moved.
    private static int storeAll(Player player, ResourceHandler<AmountResource> storage)
    {
        int wanted = MowLibXpUtils.getPlayerExperience(player);
        if (wanted <= 0) return 0;
        int stored;
        try (Transaction transaction = Transaction.openRoot())
        {
            stored = storage.insert(ExperienceTransportType.EXPERIENCE, wanted, transaction);
            if (stored > 0) transaction.commit();
        }
        if (stored > 0) MowLibXpUtils.takePlayerExperience(player, stored);
        return stored;
    }

    // Enough points to raise the player `levels` levels (or what's left in the book). Returns the points given.
    private static int giveLevels(Player player, ResourceHandler<AmountResource> storage, int levels)
    {
        int current = MowLibXpUtils.getPlayerExperience(player);
        int needed = MowLibXpUtils.pointsForLevel(player.experienceLevel + levels) - current;
        if (needed <= 0) return 0;
        int taken;
        try (Transaction transaction = Transaction.openRoot())
        {
            taken = storage.extract(ExperienceTransportType.EXPERIENCE, needed, transaction);
            if (taken > 0) transaction.commit();
        }
        if (taken > 0) MowLibXpUtils.setPlayerExperience(player, current + taken);
        return taken;
    }

    // `count` bottles o' enchanting: one is worth 3-11 points (random), a stack 7 each. Stores all or nothing.
    private static boolean storeBottles(ResourceHandler<AmountResource> storage, int count, RandomSource random)
    {
        // nextInt(9) is 0 to 8, so 3 + that is 3 to 11
        int points = count == 1 ? 3 + random.nextInt(9) : 7 * count;
        try (Transaction transaction = Transaction.openRoot())
        {
            if (storage.insert(ExperienceTransportType.EXPERIENCE, points, transaction) < points) return false;
            transaction.commit();
            return true;
        }
    }

    // As many of `bottles` as the book can fill (11 points each) become bottles o' enchanting in the inventory
    private static void fillBottles(Player player, ResourceHandler<AmountResource> storage, ItemStack bottles)
    {
        int count = (int) Math.min(bottles.getCount(), storage.getAmountAsLong(0) / POINTS_PER_BOTTLE);
        if (count <= 0) return;
        try (Transaction transaction = Transaction.openRoot())
        {
            if (storage.extract(ExperienceTransportType.EXPERIENCE, count * POINTS_PER_BOTTLE, transaction) < count * POINTS_PER_BOTTLE) return;
            transaction.commit();
        }
        bottles.consume(count, player);
        StorageInteractions.giveToPlayer(player, new ItemStack(Items.EXPERIENCE_BOTTLE, count));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 1f);
    }

    private static void orbSound(Level level, BlockPos pos)
    {
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.9f + level.getRandom().nextFloat() * 0.3f);
    }

    // ---- Tooltip: "Experience: 1395/100000 (level 30)" ----

    @Override
    public void appendTooltip(BookStorage storage, Consumer<Component> tooltip)
    {
        int points = (int) storage.amount();
        tooltip.accept(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".experience_book.amount",
                points, storage.capacity(), MowLibXpUtils.levelForPoints(points)).withStyle(TransportTypes.EXPERIENCE.get().getColor()));
    }
}
