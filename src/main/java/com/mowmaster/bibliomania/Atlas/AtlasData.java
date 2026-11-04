package com.mowmaster.bibliomania.Atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// What an Atlas item keeps on itself (bibliomania:atlas): its zoom and its markers. The explored map is kept by the
// world, per Atlas (AtlasSavedData), found by the book's bibliomania:book_id.
// - zoom: 0-3, blocks per map pixel 1, 2, 4, 8 (the page keys / scrolling change it: AtlasItem is IPageable)
// - unlocks: which locked maps it may draw (AtlasUnlocks: dimension ids, or bibliomania:underground)
// - markers: points of interest (a block, drawn as its item), banners (their colour and name) and lodestones (an X;
//   the nearest one is tracked under the map), each in one dimension
public record AtlasData(int zoom, List<Marker> markers, List<Identifier> unlocks)
{
    public static final int ZOOM_LEVELS = 4;
    public static final AtlasData EMPTY = new AtlasData(0, List.of(), List.of());

    public enum Kind { POINT, BANNER, LODESTONE }

    public record Marker(Kind kind, ResourceKey<Level> dimension, BlockPos pos, Item icon, int color, Optional<Component> name)
    {
        private static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(Kind::valueOf, Kind::name);

        public static final Codec<Marker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                KIND_CODEC.fieldOf("kind").forGetter(Marker::kind),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Marker::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Marker::pos),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(Marker::icon),
                Codec.INT.optionalFieldOf("color", 0).forGetter(Marker::color),
                ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(Marker::name)
        ).apply(instance, Marker::new));
    }

    public static final Codec<AtlasData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("zoom", 0).forGetter(AtlasData::zoom),
            Marker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(AtlasData::markers),
            // optionalFieldOf with a default: Atlases from before unlocks existed load with none
            Identifier.CODEC.listOf().optionalFieldOf("unlocks", List.of()).forGetter(AtlasData::unlocks)
    ).apply(instance, AtlasData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public AtlasData
    {
        markers = List.copyOf(markers);
        unlocks = List.copyOf(unlocks);
    }

    // Blocks per map pixel
    public int scale()
    {
        return 1 << Math.max(0, Math.min(ZOOM_LEVELS - 1, zoom));
    }

    public AtlasData withZoom(int newZoom)
    {
        return new AtlasData(Math.floorMod(newZoom, ZOOM_LEVELS), markers, unlocks);
    }

    // The marker at that spot taken away if there's one (of any kind), else this one added
    public AtlasData toggled(Marker marker)
    {
        List<Marker> changed = new ArrayList<>(markers);
        boolean removed = changed.removeIf(each -> each.dimension().equals(marker.dimension()) && each.pos().equals(marker.pos()));
        if (!removed) changed.add(marker);
        return new AtlasData(zoom, changed, unlocks);
    }

    public AtlasData withUnlock(Identifier unlock)
    {
        if (unlocks.contains(unlock)) return this;
        List<Identifier> changed = new ArrayList<>(unlocks);
        changed.add(unlock);
        return new AtlasData(zoom, markers, changed);
    }
}
