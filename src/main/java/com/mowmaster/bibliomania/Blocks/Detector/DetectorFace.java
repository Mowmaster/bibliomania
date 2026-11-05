package com.mowmaster.bibliomania.Blocks.Detector;

import net.minecraft.util.StringRepresentable;

// What one face of a Storage Detector shows (a blockstate property per face, so the model can change by face):
// - NONE: no item set (the plain _top texture)
// - OFF:  an item set, and the library has some (storage_detector_off)
// - ON:   an item set, and the library has none: lit (storage_detector), and a signal of 15 out of that face
// StringRepresentable: how a blockstate enum names its values in the blockstate file ("none", "off", "on")
public enum DetectorFace implements StringRepresentable
{
    NONE("none"),
    OFF("off"),
    ON("on");

    private final String name;

    DetectorFace(String name)
    {
        this.name = name;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
