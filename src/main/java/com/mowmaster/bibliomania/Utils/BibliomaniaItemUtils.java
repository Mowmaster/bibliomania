package com.mowmaster.bibliomania.Utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class BibliomaniaItemUtils
{
    public static CompoundTag writeItemHandlerToNBT(CompoundTag tagIn, ItemStackHandler handler, String tagToSaveAs) {
        CompoundTag compound = tagIn;
        CompoundTag compoundStorage = handler.serializeNBT();
        compound.put(tagToSaveAs, compoundStorage);
        return compound;
    }

    public static ItemStackHandler readItemHandlerFromNBT(CompoundTag tagIn, String tagToSaveAs) {
        if (tagIn.contains(tagToSaveAs)) {
            CompoundTag invTag = tagIn.getCompound(tagToSaveAs);
            ItemStackHandler handler = new ItemStackHandler();
            handler.deserializeNBT(invTag);
            return handler;
        }

        return null;
    }

    public static List<ItemStack> readItemListFromNBT(CompoundTag tagIn, String tagToSaveAs) {
        List<ItemStack> stackList = new ArrayList<>();
        if (tagIn.contains(tagToSaveAs)) {
            CompoundTag invTag = tagIn.getCompound(tagToSaveAs);
            ItemStackHandler handler = new ItemStackHandler();
            handler.deserializeNBT(invTag);
            for(int i=0;i<handler.getSlots();i++) {stackList.add(handler.getStackInSlot(i));}
            return stackList;
        }

        return stackList;
    }
}
