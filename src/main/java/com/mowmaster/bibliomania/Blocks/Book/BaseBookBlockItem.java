package com.mowmaster.bibliomania.Blocks.Book;

import com.mowmaster.mowlib.MowLibUtils.MowLibCompoundTagUtils;
import com.mowmaster.mowlib.MowLibUtils.MowLibItemUtils;
import com.mowmaster.mowlib.api.Coloring.IColorable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.ItemStackHandler;

import java.util.List;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class BaseBookBlockItem extends BlockItem {
    private static ItemStackHandler itemHandler;

    public BaseBookBlockItem(Block p_40565_, Properties p_40566_) {
        super(p_40565_, p_40566_);
    }

    public static int getBookQuality(ItemStack bookStack)
    {
        int returner = MowLibCompoundTagUtils.readIntegerFromNBT(bookStack.getOrCreateTag(),MODID + "_bookquality");
        return returner;
    }

    public static int getBookThickness(ItemStack bookStack)
    {
        if(bookStack.getOrCreateTag().contains(MODID + "_bookstorage"))
        {
            CompoundTag invTag = bookStack.getOrCreateTag().getCompound(MODID + "_bookstorage");
            itemHandler.deserializeNBT(invTag);
            //Some method to check storage capacity
        }

        return 0;
    }

    public static int getBookCover(ItemStack bookStack)
    {

        int returner = MowLibCompoundTagUtils.readIntegerFromNBT(bookStack.getOrCreateTag(),MODID + "_bookcover");
        /*
            0: grass weave
            2: wool weave
        */
        return returner;
    }

    public int getCurrentPage(ItemStack bookStack)
    {

        int returner = MowLibCompoundTagUtils.readIntegerFromNBT(bookStack.getOrCreateTag(),MODID + "_currentpage");
        /*
            0: grass weave
            2: wool weave
        */
        return returner;
    }

    /*
    Not in starter book
    public static int getBookAccent(ItemStack bookStack)
    {
        *//*
            0: none - 4 items per page???
            1: copper - 8 items per page???
            2: iron - 16 items per page???
            3: gold - 24 items per page???
            4: emerald - 32 items per page???
            5: diamond - 40 items per page???
            6: netherite - 48 items per page???
            storage above 48 requires quality???
            maybe also look into soph storages way to store more then X items in a slot
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
