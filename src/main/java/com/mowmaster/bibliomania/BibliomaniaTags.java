package com.mowmaster.bibliomania;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

// Our tags. The JSON files are in data/bibliomania/tags/item/ and data/bibliomania/tags/block/
public class BibliomaniaTags
{
    // Anything that works as a knife in recipes (ours, plus any mod's knife in c:tools/knife)
    public static final TagKey<Item> KNIVES = item("knives");
    // Books that can be crafted into a death book
    public static final TagKey<Item> STORAGE_BOOKS = item("storage_books");

    // Blocks a knife mines quickly
    public static final TagKey<Block> MINEABLE_WITH_KNIFE = block("mineable/knife");
    // Plants that sometimes drop grass fibers when cut with a knife
    public static final TagKey<Block> GRASS_FIBER_PLANTS = block("grass_fiber_plants");

    private static TagKey<Item> item(String name)
    {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, name));
    }

    private static TagKey<Block> block(String name)
    {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, name));
    }
}
