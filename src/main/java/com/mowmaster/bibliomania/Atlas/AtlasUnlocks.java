package com.mowmaster.bibliomania.Atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

// Which maps an Atlas may draw. The surface of any dimension nobody locks (the Overworld, most mods' dimensions) is
// always mapped; everything else needs unlocking, once per Atlas, by right-clicking an unlock item while carrying it
// (the first Atlas in the inventory, the one the map shows). Until then the map stays blank paper there (markers and
// the lodestone tracker still work), and nothing is explored: after unlocking, the ground is uncovered by walking it.
// - a dimension is locked if any item in the bibliomania:atlas_unlock data map unlocks it (the Nether, the End...)
// - underground slices (AtlasLayers) of a dimension that isn't locked need UNDERGROUND (a locked dimension's
//   unlock covers its slices too: the Nether is all slices)
// Any mod or datapack adds its own: data/<namespace>/data_maps/item/atlas_unlock.json, e.g.
//   { "values": { "mymod:twilight_page": { "unlocks": "mymod:twilight" } } }
// which both locks mymod:twilight and makes that item unlock it. A lang key "atlas_unlock.mymod.twilight" names it in
// tooltips (else the id is shown).
@EventBusSubscriber(modid = BibliomaniaReferences.MODID)
public final class AtlasUnlocks
{
    private AtlasUnlocks() {}

    public static final Identifier UNDERGROUND = Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "underground");

    // One data map entry: what the item unlocks (a dimension id, or bibliomania:underground)
    public record Unlock(Identifier unlocks)
    {
        public static final Codec<Unlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("unlocks").forGetter(Unlock::unlocks)
        ).apply(instance, Unlock::new));
    }

    public static final DataMapType<Item, Unlock> ATLAS_UNLOCK = DataMapType.builder(
                    Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "atlas_unlock"), Registries.ITEM, Unlock.CODEC)
            // Sent to clients (the map and tooltips need it); not required, so clients without it can still join
            .synced(Unlock.CODEC, false)
            .build();

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event)
    {
        event.register(ATLAS_UNLOCK);
    }

    // What an item unlocks, or null if it's not an unlock item. getData: NeoForge's data map lookup on a holder.
    public static @Nullable Identifier unlockOf(ItemStack stack)
    {
        // wrapAsHolder: the item's registry entry (a Holder), which NeoForge gives getData for data map lookups
        Unlock unlock = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getData(ATLAS_UNLOCK);
        return unlock == null ? null : unlock.unlocks();
    }

    // Every dimension some item unlocks. getDataMap: all of a data map's entries, item key -> value.
    private static Set<Identifier> lockedDimensions()
    {
        Set<Identifier> locked = new HashSet<>();
        for (Unlock unlock : BuiltInRegistries.ITEM.getDataMap(ATLAS_UNLOCK).values())
        {
            if (!unlock.unlocks().equals(UNDERGROUND)) locked.add(unlock.unlocks());
        }
        return locked;
    }

    // Whether an Atlas may map this dimension and layer
    public static boolean unlocked(AtlasData data, ResourceKey<Level> dimension, int layer)
    {
        Identifier id = dimension.identifier();
        if (lockedDimensions().contains(id)) return data.unlocks().contains(id);
        return layer == AtlasLayers.SURFACE || data.unlocks().contains(UNDERGROUND);
    }

    // The name of an unlock, for tooltips and messages
    public static Component name(Identifier unlock)
    {
        // translatableWithFallback: the lang key if there's one, else the plain id
        return Component.translatableWithFallback("atlas_unlock." + unlock.getNamespace() + "." + unlock.getPath(), unlock.toString());
    }

    // Right-clicking an unlock item while carrying an Atlas: used up, and the Atlas unlocked. Done on the server; the
    // client only says it worked (the hand swings). Cancelling stops the item's own use (if it had one).
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        ItemStack stack = event.getItemStack();
        Identifier unlock = unlockOf(stack);
        if (unlock == null) return;
        Player player = event.getEntity();
        ItemStack atlas = AtlasItem.carried(player);
        event.setCanceled(true);
        if (atlas.isEmpty())
        {
            event.setCancellationResult(InteractionResult.FAIL);
            if (!player.level().isClientSide()) player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".atlas.no_atlas"));
            return;
        }
        AtlasData data = AtlasItem.data(atlas);
        if (data.unlocks().contains(unlock))
        {
            event.setCancellationResult(InteractionResult.FAIL);
            if (!player.level().isClientSide()) player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".atlas.already_unlocked", name(unlock)));
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player.level().isClientSide()) return;
        atlas.set(DataComponentRegistry.ATLAS.get(), data.withUnlock(unlock));
        // consume: takes one unless the player is in creative
        stack.consume(1, player);
        player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".atlas.unlocked", name(unlock)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.5f, 1.6f);
    }
}
