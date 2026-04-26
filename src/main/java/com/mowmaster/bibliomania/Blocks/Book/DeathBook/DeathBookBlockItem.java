package com.mowmaster.bibliomania.Blocks.Book.DeathBook;

import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockItem;
import com.mowmaster.bibliomania.Utils.BibliomaniaItemUtils;
import com.mowmaster.mowlib.MowLibUtils.MowLibCompoundTagUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class DeathBookBlockItem extends BlockItem {

    public DeathBookBlockItem(Block p_40565_, Properties p_40566_) {
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
            ItemStackHandler handler = BibliomaniaItemUtils.readItemHandlerFromNBT(bookStack.getOrCreateTag(),MODID + "_bookstorage");
            if(handler != null)
            {

                int space = 0;
                int slotLimit = 4 + getBookQuality(bookStack);
                int maxSpace = slotLimit * (handler.getSlots()-1);
                for(int i=0;i< handler.getSlots();i++)
                {
                    ItemStack stack = handler.getStackInSlot(i);
                    if(stack.getCount()<slotLimit)
                    {
                        space += (slotLimit-stack.getCount());
                    }
                }

                int difference = maxSpace - space;
                double div = (double)difference/maxSpace;

                if(div < 0.1D){return 0;}
                else if(div < 0.2D && div >= 0.1D){return 1;}
                else if(div < 0.3D && div >= 0.2D){return 2;}
                else if(div < 0.4D && div >= 0.3D){return 3;}
                else if(div < 0.5D && div >= 0.4D){return 4;}
                else if(div < 0.6D && div >= 0.5D){return 5;}
                else if(div < 0.7D && div >= 0.6D){return 6;}
                else if(div < 0.8D && div >= 0.7D){return 7;}
                else if(div < 0.9D && div >= 0.8D){return 8;}
                else if(div >= 0.9D){return 9;}
            }
        }

        return 0;
    }

    public static int getBookCover(ItemStack bookStack)
    {

        int returner = MowLibCompoundTagUtils.readIntegerFromNBT(bookStack.getOrCreateTag(),MODID + "_bookcover");
        /*
            0: grass weave
            1: wool weave
            2: leather
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

    public int getPagesCount(ItemStack bookStack)
    {

        int returner = MowLibCompoundTagUtils.readIntegerFromNBT(bookStack.getOrCreateTag(),MODID + "_bookpagescount");

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

    @Override
    public void appendHoverText(ItemStack p_40572_, @Nullable Level p_40573_, List<Component> p_40574_, TooltipFlag p_40575_) {
        super.appendHoverText(p_40572_, p_40573_, p_40574_, p_40575_);
        MutableComponent deathbook = Component.translatable(getDescriptionId() + ".deathbook_description");
        deathbook.withStyle(ChatFormatting.GOLD);
        p_40574_.add(deathbook);
    }
}