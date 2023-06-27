package com.mowmaster.bibliomania.Items;

import net.minecraft.world.item.Item;

public class BaseThreadItem extends Item
{
    int threadQuality;
    public BaseThreadItem(Properties p_41383_, int quality) {
        super(p_41383_);
        this.threadQuality = quality;
    }

    public int getBaseQuality() { return this.threadQuality; }
}
