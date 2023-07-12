package com.mowmaster.bibliomania.Compat.JEI;

public class JEISettings
{
    protected static boolean jeiLoaded = false;

    public static boolean isJeiLoaded() {
        return jeiLoaded;
    }

    public static void setJeiLoaded(boolean loaded) {
        jeiLoaded = loaded;
    }
}
