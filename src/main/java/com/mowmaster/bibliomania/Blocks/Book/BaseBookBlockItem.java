package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.mowlib.MowLibUtils.MowLibItemUtils;
import com.mowmaster.mowlib.api.Coloring.IColorable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class BaseBookBlockItem extends BlockItem {
    public BaseBookBlockItem(Block p_40565_, Properties p_40566_) {
        super(p_40565_, p_40566_);
    }

    public static int getBookThickness(ItemStack bookStack)
    {

        List<ItemStack> stackList = MowLibItemUtils.readListStackFromNBT(bookStack,MODID + "_book_storage");


        return 0;
    }

    public static int getBookCover(ItemStack bookStack)
    {


        return 0;
    }

    public static int getBookPage1Color(ItemStack bookStack)
    {
        /*
        ribbon = potency color
        normal:RED - 16733525
        1:ORANGE - 16755200
        2:YELLOW - 16777045
        3:GREEN - 5635925
        4:BLUE - 43775
        5:PURPLE - 11163135
        6+:PINK - 16755455
         */
        return 16777045;
    }
    public static int getBookPage2Color(ItemStack bookStack) {return 16733525;}
    public static int getBookPage3Color(ItemStack bookStack) {return 16755200;}
    public static int getBookPage4Color(ItemStack bookStack) {return 16777045;}
    public static int getBookPage5Color(ItemStack bookStack) {return 5635925;}
    public static int getBookPage6Color(ItemStack bookStack) {return 43775;}
    public static int getBookPage7Color(ItemStack bookStack) {return 11163135;}
    public static int getBookPage8Color(ItemStack bookStack) {return 16755455;}


}
