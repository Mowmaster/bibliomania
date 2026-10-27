package com.mowmaster.bibliomania.ClientSide;

import com.mojang.serialization.MapCodec;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.Book.BookContents;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// How full a book item is, 0 to 1. The item models use it ("property": "bibliomania:book_fullness" in a
// minecraft:range_dispatch) to show a thicker book as it fills up. This replaces 1.20's ItemProperties.register.
public record BookFullnessProperty() implements RangeSelectItemModelProperty
{
    // MapCodec.unit: the property has no settings, so reading it from JSON always gives this one instance
    public static final MapCodec<BookFullnessProperty> MAP_CODEC = MapCodec.unit(new BookFullnessProperty());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed)
    {
        BookContents contents = stack.get(DataComponentRegistry.BOOK_CONTENTS.get());
        if (contents == null) return 0;
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof BookBlock book)) return 0;
        return contents.fullness(book.pageCapacity());
    }

    @Override
    public MapCodec<BookFullnessProperty> type()
    {
        return MAP_CODEC;
    }
}
