package com.mowmaster.bibliomania.Items;

import com.mowmaster.mowlib.MowLibUtils.MowLibCompoundTagUtils;
import com.mowmaster.mowlib.MowLibUtils.MowLibItemUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class TestBookItem extends Item {
    public TestBookItem(Properties p_41383_) {
        super(p_41383_);
    }

    public static int getBookThickness(ItemStack bookStack)
    {
        int returner = MowLibCompoundTagUtils.readIntegerFromNBT(bookStack.getOrCreateTag(),MODID + "_book_storage");
        List<ItemStack> stackList = MowLibItemUtils.readListStackFromNBT(bookStack,MODID + "_book_storage");

        return returner;
    }

    public static int getBookCover(ItemStack bookStack)
    {

        int returner = MowLibCompoundTagUtils.readIntegerFromNBT(bookStack.getOrCreateTag(),MODID + "_book_cover");
        /*
            0: grass weave
            1: grass woven
            2: wool weave
            3: wool woven
        */
        return returner;
    }

    /*
    Not in starter book
    public static int getBookAccent(ItemStack bookStack)
    {
        *//*
            0: none
            1: copper
            2: iron
            3: gold
            4: emerald
            5: diamond
            6: netherite
        *//*
        return 1;
    }
    */

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
