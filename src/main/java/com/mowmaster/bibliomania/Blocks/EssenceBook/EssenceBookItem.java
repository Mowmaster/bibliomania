package com.mowmaster.bibliomania.Blocks.EssenceBook;

import com.mowmaster.mowlib.storage.InventoryClicks;
import com.mowmaster.mowlib.storage.InventoryClick;
import com.mowmaster.mowlib.storage.IInventoryUsable;
import com.mowmaster.mowlib.keys.IKeyPlaceable;
import com.mowmaster.bibliomania.Blocks.Bookcase.ShelvedBook;
import com.mowmaster.bibliomania.Blocks.Bookcase.BookcaseBlockEntity;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.MobBook.MobAppearance;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookTooltip;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

// The Book of Essence as an item. For the mobs a Bestiary can't hold (monsters, villagers, golems...).
// - Right-click a mob with an empty book: sets the book to that mob (the mob isn't harmed or taken)
// - Right-click a spawner: an empty book is set to the spawner's mob; a book set to that mob gets
//   ServerConfig.essenceSpawnerPercent() essence (100% by default); the spawner is used up (broken, no drops)
// - Kill the book's mob with the book in your inventory: +1/killsRequired essence (100 kills by default)
// - At 100%, mobs "spawn" into the book over time (EssenceContents#tick), in the inventory or placed
// - In inventories, either way round (the book on the cursor onto an item, or the item onto the book):
//     a speed item (nether star) or size item (heavy core), left-click: boosts the spawning
//     a sword, right-click:  kills one stored mob; the drops go into your inventory and the XP to you
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public class EssenceBookItem extends BlockItem implements ShelvedBook, IKeyPlaceable, IInventoryUsable
{
    // On a Bookcase (ShelvedBook): its essence ticks on as in an inventory
    @Override
    public boolean shelfTick(ItemStack book, ServerLevel level, BookcaseBlockEntity shelf, int slot)
    {
        EssenceContents updated = contents(book).tick(level.getGameTime(), level.getRandom());
        if (updated == null) return false;
        setContents(book, updated);
        return true;
    }

    public EssenceBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    public static EssenceContents contents(ItemStack stack)
    {
        EssenceContents contents = stack.get(DataComponentRegistry.ESSENCE_CONTENTS.get());
        return contents != null ? contents : EssenceContents.EMPTY;
    }

    public static void setContents(ItemStack stack, EssenceContents contents)
    {
        stack.set(DataComponentRegistry.ESSENCE_CONTENTS.get(), contents);
    }

    // ---- Setting it from a mob ----

    // NeoForge's EntityInteract fires when a player right-clicks an entity, before the entity reacts. Items normally
    // get Item#interactLivingEntity, but only after the mob passes on the click: a villager opens its trades instead.
    // Cancelling the event with a result stops everything else (no trade screen); both sides do this, so the
    // client swings the arm and doesn't send a second click.
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        ItemStack book = event.getItemStack();
        if (!(book.getItem() instanceof EssenceBookItem)) return;
        Player player = event.getEntity();
        Entity target = event.getTarget();
        Level level = event.getLevel();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        EssenceContents contents = contents(book);
        if (contents.isSet() || !EssenceActions.canHold(target.getType(), target))
        {
            // Already set (even to this mob), or a mob it can't hold
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return;
        }
        if (level instanceof ServerLevel serverLevel)
        {
            setContents(book, contents.withType(target.getType(), MobAppearance.capture(target)));
            serverLevel.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    30, 0.3, 0.5, 0.3, 0.5);
            level.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1f);
        }
    }

    // Can't be placed until it's set to a mob
    @Override
    public InteractionResult place(BlockPlaceContext context)
    {
        if (!contents(context.getItemInHand()).isSet()) return MobBookActions.refusePlacing(context);
        return super.place(context);
    }

    // ---- Spawners ----

    // Right-clicking a block: a spawner gives essence (or sets an empty book); anything else places the book
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Optional<EntityType<?>> spawned = EssenceActions.spawnerType(level, pos);
        if (spawned.isEmpty() || context.getPlayer() == null) return super.useOn(context);

        Player player = context.getPlayer();
        ItemStack book = player.getItemInHand(context.getHand());
        EssenceContents contents = contents(book);
        EntityType<?> type = spawned.get();
        // A temporary mob of that type, to check it's one this book holds (a pig spawner is the Bestiary's)
        Entity example = MobBookActions.createTemporary(type, level, EntitySpawnReason.TRIGGERED);
        boolean wrongMob = contents.isSet() && contents.type().get() != type;
        if (wrongMob || contents.complete() || !EssenceActions.canHold(type, example))
        {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel)
        {
            EssenceContents set = contents.isSet() ? contents : contents.withType(type, DataComponentMap.EMPTY);
            setContents(book, set.withEssence(ServerConfig.essenceSpawnerPercent() / 100f));
            serverLevel.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    40, 0.5, 0.5, 0.5, 0.5);
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1f);
            // The spawner is used up: broken with its particles and sound, but no drops (false) and no experience
            level.destroyBlock(pos, false);
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Spawning in a player's inventory ----

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot)
    {
        super.inventoryTick(stack, level, entity, slot);
        if (!(entity instanceof Player) || stack.getCount() != 1) return;
        EssenceContents updated = contents(stack).tick(level.getGameTime(), level.getRandom());
        if (updated != null) setContents(stack, updated);
    }

    // ---- Inventory clicks (MowLib InventoryClicks: the guards; usedWith: what the book does) ----
    // Either way round: the book on the cursor clicked onto `other` in a slot, or `other` on the cursor clicked onto the
    // book. A left-click with an empty cursor picks the book up as normal; any other item it can't use is refused.

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        return InventoryClicks.stackedOnOther(this, self, slot, clickAction, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        return InventoryClicks.otherStackedOnMe(this, self, other, slot, clickAction, player, carriedItem);
    }

    @Override
    public boolean usedWith(ItemStack self, ItemStack other, ClickAction action, Player player, InventoryClick click)
    {
        return useOn(self, other, action, player);
    }

    @Override
    public boolean refusesOtherClicks(ItemStack self)
    {
        return true;
    }

    // The Reading Lectern standing in for this book: boost items (left-click) and swords (right-click). Server.
    public static boolean lecternUse(ItemStack book, ItemStack held, Player player)
    {
        if (held.isEmpty()) return false;
        return useOn(book, held, ClickAction.PRIMARY, player) || useOn(book, held, ClickAction.SECONDARY, player);
    }

    private static boolean useOn(ItemStack self, ItemStack other, ClickAction clickAction, Player player)
    {
        Level level = player.level();
        EssenceContents contents = contents(self);

        // A boost item, left-click
        if (clickAction == ClickAction.PRIMARY && EssenceActions.isBoost(contents, other))
        {
            if (level instanceof ServerLevel)
            {
                setContents(self, EssenceActions.boost(contents, other));
                other.consume(1, player);
                EssenceActions.boostSound(level, player.blockPosition());
            }
            return true;
        }

        // A sword, right-click: kill one, drops and XP to the player
        if (clickAction == ClickAction.SECONDARY && MobBookActions.isKillWeapon(other)
                && contents.stored() > 0 && contents.type().isPresent())
        {
            if (level instanceof ServerLevel serverLevel)
            {
                int killed = MobBookActions.kill(serverLevel, contents.type().get(), contents.appearance(), 1, player,
                        player.position(), true);
                setContents(self, contents.withKilled(killed));
            }
            return true;
        }
        return false;
    }

    // ---- Tooltip ----

    // Rebuilt every frame while the tooltip is open, so the timer counts down live
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        Level level = context.level();
        long now = level != null ? level.getGameTime() : 0;
        EssenceContents contents = contents(stack);
        if (contents.type().isEmpty())
        {
            tooltip.accept(line("empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        // "Zombie x12"
        tooltip.accept(line("count", contents.type().get().getDescription(), contents.stored()).withStyle(ChatFormatting.GRAY));
        if (!contents.complete())
        {
            // "Essence: 37% (63 kills to go)"
            int killsLeft = (int) Math.ceil((1f - contents.essence()) * ServerConfig.essenceKills());
            tooltip.accept(line("essence", contents.percent() + "%", killsLeft).withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        // "Spawns 1-4 every 0:40", "Next spawn in: 0:31"
        tooltip.accept(line("spawns", contents.spawnMin(), contents.spawnMax(), time(contents.interval())).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (contents.nextSpawn() > 0)
        {
            tooltip.accept(line("next_spawn", time(contents.spawnLeft(now))).withStyle(ChatFormatting.GRAY));
        }
    }

    private static MutableComponent line(String name, Object... args)
    {
        return Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".essence_book." + name, args);
    }

    // Ticks as m:ss, rounded up
    private static String time(long ticks)
    {
        long seconds = (ticks + 19) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // The picture: the mob's spawn egg with the stored count (the Bestiary's ClientMobBookTooltip draws it)
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack)
    {
        EssenceContents contents = contents(stack);
        if (contents.type().isEmpty()) return Optional.empty();
        return Optional.of(new MobBookTooltip(contents.type().get(), contents.stored()));
    }
}
