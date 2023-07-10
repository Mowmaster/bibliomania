package com.mowmaster.bibliomania.Registry;

import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockItem;
import com.mowmaster.bibliomania.Items.TestBookItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class BibliomaniaItemModelProperties
{
    public static void bibliomaniaItemModes(Item item)
    {

        ItemProperties.register(item, new ResourceLocation(MODID + ":book_thickness"),(p_174625_, p_174626_, p_174627_, p_174628_) -> {
            return TestBookItem.getBookThickness(p_174625_);});
        ItemProperties.register(item, new ResourceLocation(MODID + ":book_cover"),(p_174625_, p_174626_, p_174627_, p_174628_) -> {
        return TestBookItem.getBookCover(p_174625_);});

        /*
        Not in starter book (just add another override to the model file for this)
        ItemProperties.register(item, new ResourceLocation(MODID + ":book_accent"),(p_174625_, p_174626_, p_174627_, p_174628_) -> {
            return TestBookItem.getBookAccent(p_174625_);});
        */


    }
}
