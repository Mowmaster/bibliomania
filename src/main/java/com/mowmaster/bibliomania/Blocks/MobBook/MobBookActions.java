package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

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

    // ---- Killing ----

    // Kills `count` stored mobs of `type` as `player`, at `at`. With toPlayer, the drops go into the player's
    // inventory (or at their feet if it's full) and the XP straight to them; otherwise both land at `at`.
    // The first kill plays the mob's own death sound. Returns how many were killed.
    public static int kill(ServerLevel level, EntityType<?> type, int count, Player player, Vec3 at, boolean toPlayer)
    {
        DamageSource source = level.damageSources().playerAttack(player);
        int killed = 0;
        for (int i = 0; i < count; i++)
        {
            Entity created = createTemporary(type, level, EntitySpawnReason.TRIGGERED);
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
