package com.mowmaster.bibliomania;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
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

    // ---- [essence] Book of Essence ----
    // pop() closes [bestiary], push("essence") opens [essence]. A static block runs once, in order with the fields.
    static
    {
        BUILDER.pop().push("essence");
    }

    private static final ModConfigSpec.IntValue ESSENCE_KILLS = BUILDER
            .comment("Kills of the book's mob (with the book in your inventory) for 100% essence. Default 100.")
            .defineInRange("killsRequired", 100, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ESSENCE_SPAWNER_PERCENT = BUILDER
            .comment("Essence (in %) added by right-clicking a spawner of the book's mob. Default 100.")
            .defineInRange("spawnerPercent", 100, 0, 100);

    private static final ModConfigSpec.BooleanValue ESSENCE_SPAWNER_CONSUMED = BUILDER
            .comment("Whether the spawner is used up (removed) when it gives essence. Default false.")
            .define("spawnerConsumed", false);

    private static final ModConfigSpec.IntValue ESSENCE_SPAWN_INTERVAL = BUILDER
            .comment("Ticks between spawns once a book has 100% essence. Default 800 = 40 seconds.")
            .defineInRange("spawnInterval", 800, 20, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ESSENCE_SPAWN_MIN = BUILDER
            .comment("Fewest mobs a spawn adds. Default 1.")
            .defineInRange("spawnMin", 1, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ESSENCE_SPAWN_MAX = BUILDER
            .comment("Most mobs a spawn adds. Default 4.")
            .defineInRange("spawnMax", 4, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ESSENCE_MAX_STORED = BUILDER
            .comment("Most mobs a Book of Essence holds. Default 256.")
            .defineInRange("maxStored", 256, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.ConfigValue<String> ESSENCE_SPEED_ITEM = BUILDER
            .comment("Item that shortens a book's spawn time. Default minecraft:nether_star.")
            .define("speedItem", "minecraft:nether_star");

    private static final ModConfigSpec.DoubleValue ESSENCE_SPEED_PERCENT = BUILDER
            .comment("How much (in %) each speed item shortens the spawn time; each one takes this off the time left by the ones before. Default 2.")
            .defineInRange("speedPercent", 2.0, 0.0, 100.0);

    private static final ModConfigSpec.ConfigValue<String> ESSENCE_SIZE_ITEM = BUILDER
            .comment("Item that raises how many mobs a spawn adds. Default minecraft:heavy_core.")
            .define("sizeItem", "minecraft:heavy_core");

    private static final ModConfigSpec.IntValue ESSENCE_MIN_INCREASE = BUILDER
            .comment("How much each size item raises the fewest mobs per spawn. Default 4.")
            .defineInRange("minIncrease", 4, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ESSENCE_MAX_INCREASE = BUILDER
            .comment("How much each size item raises the most mobs per spawn. Default 4.")
            .defineInRange("maxIncrease", 4, 0, Integer.MAX_VALUE);

    // ---- [storage_books] Books of Fluids, Experience and Energy ----
    static
    {
        BUILDER.pop().push("storage_books");
    }

    private static final ModConfigSpec.IntValue FLUID_CAPACITY = BUILDER
            .comment("How much a Book of Fluids holds, in mB (1000 = a bucket). Default 64000.")
            .defineInRange("fluidCapacity", 64000, 1000, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue EXPERIENCE_CAPACITY = BUILDER
            .comment("How much a Book of Experience holds, in experience points (level 30 is 1395 points). Default 100000.")
            .defineInRange("experienceCapacity", 100000, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue EXPERIENCE_PULL_RANGE = BUILDER
            .comment("How far (in blocks) a placed Book of Experience pulls experience orbs from. 0 = it doesn't. Default 8.")
            .defineInRange("experiencePullRange", 8, 0, 32);

    private static final ModConfigSpec.IntValue ENERGY_CAPACITY = BUILDER
            .comment("How much a Book of Energy holds, in FE. Default 1000000.")
            .defineInRange("energyCapacity", 1000000, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ENERGY_CHARGE_RATE = BUILDER
            .comment("How much a Book of Energy in your inventory charges your other items, in FE a tick (all items together). Default 2000.")
            .defineInRange("energyChargeRate", 2000, 0, Integer.MAX_VALUE);

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

    public static int essenceKills()           { return get(ESSENCE_KILLS); }
    public static int essenceSpawnerPercent()  { return get(ESSENCE_SPAWNER_PERCENT); }
    public static boolean essenceSpawnerConsumed() { return get(ESSENCE_SPAWNER_CONSUMED); }
    public static int essenceSpawnInterval()   { return get(ESSENCE_SPAWN_INTERVAL); }
    public static int essenceSpawnMin()        { return get(ESSENCE_SPAWN_MIN); }
    public static int essenceSpawnMax()        { return get(ESSENCE_SPAWN_MAX); }
    public static int essenceMaxStored()       { return get(ESSENCE_MAX_STORED); }
    public static double essenceSpeedPercent() { return get(ESSENCE_SPEED_PERCENT); }
    public static int essenceMinIncrease()     { return get(ESSENCE_MIN_INCREASE); }
    public static int essenceMaxIncrease()     { return get(ESSENCE_MAX_INCREASE); }

    public static int fluidCapacity()          { return get(FLUID_CAPACITY); }
    public static int experienceCapacity()     { return get(EXPERIENCE_CAPACITY); }
    public static int experiencePullRange()    { return get(EXPERIENCE_PULL_RANGE); }
    public static int energyCapacity()         { return get(ENERGY_CAPACITY); }
    public static int energyChargeRate()       { return get(ENERGY_CHARGE_RATE); }

    public static boolean isEssenceSpeedItem(ItemStack stack)
    {
        return isItem(stack, get(ESSENCE_SPEED_ITEM));
    }

    public static boolean isEssenceSizeItem(ItemStack stack)
    {
        return isItem(stack, get(ESSENCE_SIZE_ITEM));
    }

    // Whether `stack` is the item named by `id` ("minecraft:nether_star"). tryParse gives null for a badly written id;
    // getValue gives air for an id that isn't registered, and an item stack is never air unless it's empty.
    private static boolean isItem(ItemStack stack, String id)
    {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null && !stack.isEmpty() && stack.is(BuiltInRegistries.ITEM.getValue(parsed));
    }

    // ConfigValue<T> is the common type of every config value: one generic method reads them all
    private static <T> T get(ModConfigSpec.ConfigValue<T> value)
    {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
