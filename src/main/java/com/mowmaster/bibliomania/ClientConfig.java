package com.mowmaster.bibliomania;

import com.mowmaster.mowlib.client.HudAnchor;
import net.neoforged.neoforge.common.ModConfigSpec;

// Settings for this player's screen only (ModConfig.Type.CLIENT: config/bibliomania-client.toml, never sent to
// servers), registered in BibliomaniaClient. Editable in game: Mods > Bibliomania > Config.
// - [atlas_map]  where the Atlas's map goes: pinned to a screen anchor (MowLib's HudAnchor), then moved by the offsets
//   (positive x = right, positive y = down), and how big it's drawn
// The reading HUD's panels (Reading Goggles) are MowLib's: config/mowlib-client.toml.
public final class ClientConfig
{
    private ClientConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<HudAnchor> ATLAS_ANCHOR;
    public static final ModConfigSpec.IntValue ATLAS_X;
    public static final ModConfigSpec.IntValue ATLAS_Y;
    public static final ModConfigSpec.IntValue ATLAS_SIZE;

    // A static block: runs once when the class loads, in order, so each push/pop wraps the values between them
    static
    {
        BUILDER.comment("The Atlas's map (and the lodestone tracker under it)").push("atlas_map");
        ATLAS_ANCHOR = BUILDER.comment("Where on the screen it's pinned").defineEnum("anchor", HudAnchor.TOP_RIGHT);
        ATLAS_X = BUILDER.comment("Moved this far right from there (negative: left)").defineInRange("x_offset", 0, -4000, 4000);
        ATLAS_Y = BUILDER.comment("Moved this far down from there (negative: up)").defineInRange("y_offset", 0, -4000, 4000);
        ATLAS_SIZE = BUILDER.comment("How big it's drawn, in GUI pixels (it always shows 64 x 64 map pixels of ground; this only scales them)")
                .defineInRange("size", 80, 32, 512);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
