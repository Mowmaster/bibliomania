package com.mowmaster.bibliomania.Items;

import com.mowmaster.mowlib.MowLibUtils.MowLibCompoundTagUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class BasePaperStack extends Item
{
    public BasePaperStack(Properties p_41383_) {
        super(p_41383_);
    }

    public static int getStackThickness(ItemStack bookStack)
    {
        int returner = MowLibCompoundTagUtils.readIntegerFromNBT(bookStack.getOrCreateTag(),MODID + "_paperstack_count");
        if(returner<4) {return 1;}
        else if(returner>=4 && returner<8) {return 2;}
        else if(returner>=8 && returner<12) {return 3;}
        else if(returner>=12 && returner<16) {return 4;}
        else if(returner>=16 && returner<20) {return 5;}
        else if(returner>=20 && returner<24) {return 6;}
        else if(returner>=24 && returner<28) {return 7;}
        else if(returner>=28 && returner<32) {return 8;}
        else if(returner>=32) {return 8;}
        else return 1;
    }

}
