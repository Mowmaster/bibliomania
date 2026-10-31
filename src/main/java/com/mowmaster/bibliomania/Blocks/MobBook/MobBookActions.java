package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.BibliomaniaTags;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// What a Bestiary does to the mobs in it, shared by the placed book and the item.
//
// Killing: the book never holds real mobs, only a count. To kill one, it makes a temporary mob of the stored type
// (never added to the world, so nobody sees it) at the book, marks it as hurt by the player, and calls die(). That
// runs vanilla's own death code, so the drops come from the mob's loot table (with Looting from the player's weapon),
// equipment and XP are dropped, and other mods' drop and XP events apply, exactly as if the player had killed it.
// For kills from the inventory, the drops and XP are caught in the events below and given to the player instead.
//
// @EventBusSubscriber: the event handlers are on the game event bus.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class MobBookActions
{
    private MobBookActions() {}

    // While an inventory kill is running: the temporary mob, and the player its drops and XP go to.
    // Everything here runs on the server thread, one kill at a time, so a plain field is enough.
    private record Redirect(LivingEntity mob, Player player) {}
    @Nullable private static Redirect redirect;

    // ---- Temporary mobs ----

    // Makes a mob of `type` that is never added to the world (for display, food checks and kills).
    // Since 26.x a mob only gets its network ID when it's added to a level, and asking for it before then crashes
    // ("Tried to access entity ID before ID assignment"). Rendering and dying both ask, so it gets -1:
    // the same ID vanilla gives the mob spinning in a spawner (BaseSpawner#SET_DISPLAY_ENTITY_ID).
    @Nullable
    public static Entity createTemporary(EntityType<?> type, Level level, EntitySpawnReason reason)
    {
        Entity entity = type.create(level, reason);
        if (entity != null) entity.setId(-1);
        return entity;
    }

    // A temporary mob that looks like the book's mobs: its variant/colour (MobAppearance), and a sheared sheep while
    // the wool grows back. Used for kills, shearing, egg laying, so the drops match (red wool from red sheep).
    @Nullable
    public static Entity createFor(MobBookContents contents, Level level)
    {
        if (contents.type().isEmpty()) return null;
        Entity mob = createTemporary(contents.type().get(), level, EntitySpawnReason.TRIGGERED);
        if (mob == null) return null;
        MobAppearance.apply(mob, contents.appearance());
        if (mob instanceof Sheep sheep && contents.regrowing(level.getGameTime())) sheep.setSheared(true);
        return mob;
    }

    // ---- What can go in, and what feeds them ----

    // Adults only, and never pets or named mobs (so nobody loses one by accident)
    public static boolean canCapture(Entity target)
    {
        if (!(target instanceof Animal animal) || animal.isBaby() || animal.hasCustomName()) return false;
        return !(animal instanceof TamableAnimal tamable && tamable.isTame());
    }

    // Whether the stack is this mob's breeding food. Asks a temporary mob of that type (e.g. a cow says wheat).
    public static boolean isBreedingFood(Level level, MobBookContents contents, ItemStack stack)
    {
        if (stack.isEmpty() || contents.type().isEmpty()) return false;
        Entity mob = createTemporary(contents.type().get(), level, EntitySpawnReason.TRIGGERED);
        return mob instanceof Animal animal && animal.isFood(stack);
    }

    // Any sword kills, however much damage it does
    public static boolean isKillWeapon(ItemStack stack)
    {
        return stack.is(ItemTags.SWORDS);
    }

    // ---- Dyeing ----

    // The dye colour of a dye item (26.1+: any item with the minecraft:dye component), or null
    @Nullable
    public static DyeColor dyeColor(ItemStack stack)
    {
        return stack.get(DataComponents.DYE);
    }

    // Whether `stack` would change the book's mobs' colour (a dyeable mob, and a different colour)
    public static boolean canDye(MobBookContents contents, ItemStack stack)
    {
        DyeColor color = dyeColor(stack);
        return color != null && !contents.isEmpty() && MobAppearance.dyeable(contents.appearance()) && color != contents.color();
    }

    // ---- Shearing ----

    public static boolean isShears(ItemStack stack)
    {
        return stack.is(Tags.Items.TOOLS_SHEAR);
    }

    // Whether the book's mobs can be sheared now: they're shearable (NeoForge's IShearable, which every vanilla and most
    // modded shearable mobs have), not growing back, and not in #bibliomania:mob_book_no_shear (mobs whose shearing
    // turns them into something else, which would spawn into the world). Mooshrooms are in that tag, and are handled
    // by shearKill instead (the shear_kills data map), which callers check first.
    public static boolean canShear(Level level, MobBookContents contents)
    {
        if (contents.adults() <= 0 || contents.type().isEmpty() || contents.regrowing(level.getGameTime())) return false;
        if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(contents.type().get()).is(BibliomaniaTags.MOB_BOOK_NO_SHEAR)) return false;
        return createFor(contents, level) instanceof IShearable;
    }

    // Shears every adult: each one is a temporary mob sheared the normal way, so the drops come from its own shearing
    // (a sheep's wool loot table, in its colour). NeoForge's onSheared returns the drops instead of spawning them.
    // The caller starts the regrowth timer (MobBookContents#withSheared) and places the items.
    public static List<ItemStack> shear(ServerLevel level, MobBookContents contents, Player player, ItemStack shears, Vec3 at)
    {
        List<ItemStack> items = new ArrayList<>();
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < contents.adults(); i++)
        {
            Entity mob = createFor(contents, level);
            if (!(mob instanceof IShearable shearable)) break;
            mob.setPos(at.x, at.y, at.z);
            if (!shearable.isShearable(player, shears, level, pos)) continue;
            for (ItemStack drop : shearable.onSheared(player, shears, level, pos)) MobBookDrops.merge(items, drop.copy());
        }
        // The mob's own shear sound doesn't reach anyone (it isn't in the world), so play one at the book/player
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1f, 1f);
        return items;
    }

    // Shears on mobs in #shear_kills (mooshrooms): one is sheared and killed. Its shearing drops (mushrooms) and its
    // death drops and XP go to the player (toPlayer, from the inventory) or land at `at` (a placed book).
    // Returns the book's contents with that mob gone.
    public static MobBookContents shearKill(ServerLevel level, MobBookContents contents, Player player, ItemStack shears,
                                            Vec3 at, boolean toPlayer)
    {
        MobBookDrops.MobDrop drop = MobBookDrops.shearKillFor(contents);
        if (drop == null || contents.adults() <= 0) return contents;
        BlockPos pos = BlockPos.containing(at);
        for (ItemStack item : MobBookDrops.rollShear(level, contents, drop, 1, at, shears))
        {
            if (toPlayer) StorageInteractions.giveToPlayer(player, item);
            else Block.popResource(level, pos, item);
        }
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1f, 1f);
        int killed = kill(level, contents, 1, player, at, toPlayer);
        return contents.withKilled(killed);
    }

    // ---- Killing ----

    // Kills `count` of the book's adult mobs as `player`, at `at`. With toPlayer, the drops go into the player's
    // inventory (or at their feet if it's full) and the XP straight to them; otherwise both land at `at`.
    // The first kill plays the mob's own death sound. Returns how many were killed.
    public static int kill(ServerLevel level, MobBookContents contents, int count, Player player, Vec3 at, boolean toPlayer)
    {
        DamageSource source = level.damageSources().playerAttack(player);
        int killed = 0;
        for (int i = 0; i < count; i++)
        {
            Entity created = createFor(contents, level);
            if (!(created instanceof LivingEntity mob)) break;
            mob.setPos(at.x, at.y, at.z);
            // Counts as killed by the player: player-only drops and XP happen, and Looting applies
            mob.setLastHurtByPlayer(player, 100);

            if (toPlayer) redirect = new Redirect(mob, player);
            try
            {
                if (i == 0)
                {
                    // A lethal hit, so vanilla plays this mob's death sound (and then calls die)
                    mob.hurtServer(level, source, Float.MAX_VALUE);
                }
                if (mob.isAlive()) mob.die(source); // later kills, or a mob that shrugged off the hit
            }
            finally
            {
                redirect = null;
            }
            mob.remove(Entity.RemovalReason.KILLED);
            killed++;
        }
        return killed;
    }

    // ---- Left-clicking a placed book with a sword ----

    // NeoForge fires LeftClickBlock on both sides when a player starts hitting a block, before vanilla does
    // anything (in creative too, where Block#attack never runs). Cancelling it stops the client from
    // breaking/mining the book and the server from doing its own break handling; the server then kills instead.
    // Only START: the other actions (STOP, ABORT, CLIENT_HOLD) are for finishing or holding a dig.
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Level level = event.getLevel();
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof MobBookBlock)) return;
        Player player = event.getEntity();
        if (!isKillWeapon(player.getMainHandItem())) return;
        event.setCanceled(true);
        if (level instanceof ServerLevel serverLevel) MobBookBlock.killAction(serverLevel, event.getPos(), player);
    }

    // ---- Inventory kills: drops and XP to the player ----

    // LOWEST so other mods have changed the drops first; we take whatever is left
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event)
    {
        Redirect target = redirect;
        if (target == null || event.getEntity() != target.mob()) return;
        for (ItemEntity drop : event.getDrops())
        {
            StorageInteractions.giveToPlayer(target.player(), drop.getItem());
        }
        event.setCanceled(true); // so they aren't also dropped in the world
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperience(LivingExperienceDropEvent event)
    {
        Redirect target = redirect;
        if (target == null || event.getEntity() != target.mob()) return;
        target.player().giveExperiencePoints(event.getDroppedExperience());
        event.setDroppedExperience(0);
    }
}
