package com.mowmaster.bibliomania;

import net.neoforged.neoforge.common.ModConfigSpec;

// Gameplay settings, one file per world: <world>/syncedconfig/bibliomania-server.toml.
// A SYNCED config is read by the server and sent to players when they join, so everyone uses the same values.
// Registered in the Bibliomania constructor (modContainer.registerConfig(ModConfig.Type.SYNCED, ...)).
// All times are in ticks (20 ticks = 1 second).
public final class ServerConfig
{
    private ServerConfig() {}

    // push("bestiary") opens a [bestiary] section in the file; pop() closes it
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder().push("bestiary");

    private static final ModConfigSpec.IntValue BREEDING_COOLDOWN = BUILDER
            .comment("Ticks a Bestiary waits between breedings (babies grow up when it ends). Default 6000 = 5 minutes.")
            .defineInRange("breedingCooldown", 6000, 20, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue DROP_INTERVAL = BUILDER
            .comment("Ticks between egg laying / shedding for mobs in a Bestiary (chickens, armadillos...). Default 9000 = 7.5 minutes.")
            .defineInRange("dropInterval", 9000, 20, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SHEAR_REGROWTH = BUILDER
            .comment("Ticks for sheared mobs in a Bestiary (sheep...) to grow back. Default 2400 = 2 minutes.")
            .defineInRange("shearRegrowth", 2400, 20, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MILK_COOLDOWN = BUILDER
            .comment("Ticks before mobs in a Bestiary can be milked again (cows, mooshrooms, goats...). Default 0 = no wait, like vanilla.")
            .defineInRange("milkCooldown", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.pop().build();

    // The values. Before a world is loaded (e.g. a tooltip on the main menu) a server config isn't loaded and
    // get() would throw, so the default is used then.
    public static int breedingCooldown()
    {
        return get(BREEDING_COOLDOWN);
    }

    public static int dropInterval()
    {
        return get(DROP_INTERVAL);
    }

    public static int shearRegrowth()
    {
        return get(SHEAR_REGROWTH);
    }

    public static int milkCooldown()
    {
        return get(MILK_COOLDOWN);
    }

    private static int get(ModConfigSpec.IntValue value)
    {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
