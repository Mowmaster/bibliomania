package com.mowmaster.bibliomania.Blocks.EssenceBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookActions;
import com.mowmaster.bibliomania.ServerConfig;
import com.mowmaster.mowlib.display.MobDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

// What a Book of Essence does, shared by the placed book and the item.
// @EventBusSubscriber: the kill counter below is on the game event bus.
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class EssenceActions
{
    private EssenceActions() {}

    // ---- Which mobs ----

    // Every mob the Bestiary can't hold: not animals (those go in a Bestiary), and not in MowLib's mob blacklist
    // (#mowlib:mob_display_blacklist: players, the ender dragon...). Only mobs (Mob), so not armour stands etc.
    public static boolean canHold(EntityType<?> type, @Nullable Entity example)
    {
        if (!MobDisplay.canDisplay(type)) return false;
        return example == null || (example instanceof Mob && !(example instanceof Animal));
    }

    // The mob a vanilla spawner spawns, read from its saved data ({"SpawnData": {"entity": {"id": "minecraft:zombie"}}}),
    // or empty if it isn't a spawner or has no mob set. getCompound/getString return Optionals in 26.x (empty if missing);
    // flatMap chains them, so any missing step gives empty.
    public static Optional<EntityType<?>> spawnerType(Level level, BlockPos pos)
    {
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) return Optional.empty();
        CompoundTag saved = spawner.saveCustomOnly(level.registryAccess()); // the same call our books use for syncing
        return saved.getCompound("SpawnData")
                .flatMap(data -> data.getCompound("entity"))
                .flatMap(entity -> entity.getString("id"))
                .flatMap(EntityType::byString);
    }

    // ---- Boost items ----

    // Whether `stack` boosts the book: a speed item (nether star) or a size item (heavy core), on a book that's set
    public static boolean isBoost(EssenceContents contents, ItemStack stack)
    {
        return contents.isSet() && (ServerConfig.isEssenceSpeedItem(stack) || ServerConfig.isEssenceSizeItem(stack));
    }

    // The book after using one boost item (the caller uses up the item)
    public static EssenceContents boost(EssenceContents contents, ItemStack stack)
    {
        if (ServerConfig.isEssenceSpeedItem(stack)) return contents.withSpeedUp();
        if (ServerConfig.isEssenceSizeItem(stack)) return contents.withSizeUp();
        return contents;
    }

    // The sound for a boost (the beacon "power up" sound)
    public static void boostSound(Level level, BlockPos pos)
    {
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    // ---- Kills fill the essence ----

    // A player killed a mob: the first Book of Essence in their inventory that's set to that mob and not yet at 100%
    // gets 1/killsRequired more essence. Book kills (MobBookActions#kill) aren't real kills and don't count.
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (MobBookActions.isBookKill()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        EntityType<?> type = event.getEntity().getType();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof EssenceBookItem)) continue;
            EssenceContents contents = EssenceBookItem.contents(stack);
            if (contents.type().orElse(null) != type || contents.complete()) continue;
            EssenceBookItem.setContents(stack, contents.withEssence(1f / ServerConfig.essenceKills()));
            if (EssenceBookItem.contents(stack).complete())
            {
                // 100%: the level-up sound (null = no player is left out, everyone nearby hears it)
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
            }
            return;
        }
    }
}
