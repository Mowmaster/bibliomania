package com.mowmaster.bibliomania.Blocks.MobBook;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

// A placed book that shows a mob spinning above it (the Bestiary and the Book of Essence).
// MobBookRenderer draws any block entity that implements this.
public interface IMobBookDisplay
{
    // The (fake, client-side) mob to draw now, or null for nothing
    @Nullable Entity getDisplayMob();

    // Its type: the type's full-grown size is used to fit it on the book
    @Nullable EntityType<?> getDisplayType();

    // Extra scale on top of the fitted size: a growing baby (x1 to x2), a Book of Essence filling up...
    float getDisplayScale();

    // The game tick of the last kill, for the red flash
    long getLastKill();
}
