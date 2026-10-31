package com.mowmaster.bibliomania.Blocks.MobBook;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;

// A mob's look, as data components: since 1.21.5 entities expose their variant and colour data the same way items do
// (Entity is a DataComponentGetter): a cow's minecraft:cow/variant, a chicken's minecraft:chicken/variant, a sheep's
// minecraft:sheep/color, a frog's, rabbit's, axolotl's... and any modded mob that does the same.
// A Bestiary keeps the first captured mob's look (MobBookContents#appearance) and gives it to every mob it shows or
// uses, so a red sheep book shows red sheep, is sheared for red wool, and a cold chicken book lays blue eggs.
public final class MobAppearance
{
    private MobAppearance() {}

    // Every component the mob has: asks the mob for each registered component type and keeps the answers.
    // Not the custom name (named mobs can't be captured anyway) and not "transient" types (ones that are never saved).
    public static DataComponentMap capture(Entity entity)
    {
        DataComponentMap.Builder builder = DataComponentMap.builder();
        for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE)
        {
            copy(entity, type, builder);
        }
        return builder.build();
    }

    // A generic method, so the type's <T> matches its value: Java can't do that with DataComponentType<?> directly
    private static <T> void copy(Entity entity, DataComponentType<T> type, DataComponentMap.Builder builder)
    {
        if (type == DataComponents.CUSTOM_NAME || type.isTransient()) return;
        T value = entity.get(type);
        if (value != null) builder.set(type, value);
    }

    // Gives `look` to a (temporary) mob
    public static void apply(Entity entity, DataComponentMap look)
    {
        for (TypedDataComponent<?> component : look)
        {
            set(entity, component);
        }
    }

    private static <T> void set(Entity entity, TypedDataComponent<T> component)
    {
        entity.setComponent(component.type(), component.value());
    }

    // Whether the look has a dye colour (a sheep's wool) that a dye can change
    public static boolean dyeable(DataComponentMap look)
    {
        for (TypedDataComponent<?> component : look)
        {
            if (component.value() instanceof DyeColor) return true;
        }
        return false;
    }

    // `look` with every dye colour in it changed to `color`
    public static DataComponentMap dyed(DataComponentMap look, DyeColor color)
    {
        DataComponentMap.Builder builder = DataComponentMap.builder();
        for (TypedDataComponent<?> component : look)
        {
            put(builder, component.type(), component.value() instanceof DyeColor ? color : component.value());
        }
        return builder.build();
    }

    // The cast is safe: the value is either the component's own, or a DyeColor replacing a DyeColor.
    // @SuppressWarnings("unchecked") tells the compiler we know it can't check this cast.
    @SuppressWarnings("unchecked")
    private static <T> void put(DataComponentMap.Builder builder, DataComponentType<T> type, Object value)
    {
        builder.set(type, (T) value);
    }
}
