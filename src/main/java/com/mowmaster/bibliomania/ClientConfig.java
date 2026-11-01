package com.mowmaster.bibliomania;

import net.neoforged.neoforge.common.ModConfigSpec;

// Settings for this player's screen only (ModConfig.Type.CLIENT: config/bibliomania-client.toml, never sent to
// servers), registered in BibliomaniaClient. Editable in game: Mods > Bibliomania > Config.
// Where the Reading Goggles' two panels go (ClientSide/ReadingHud):
// - [reading_hud]  the paper with a book's details (default: the middle of the left edge)
// - [block_label]  the small label naming any other block (default: the top middle)
// Each is pinned to a screen anchor, then moved by the offsets (positive x = right, positive y = down).
public final class ClientConfig
{
    private ClientConfig() {}

    // Nine places on the screen a panel can be pinned to
    public enum Anchor
    {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

        // The panel's top-left corner for a panel this big on a screen this big (before the offsets).
        // ordinal(): the enum's position in the list above (0-8), so % 3 is the column and / 3 the row.
        public int x(int screenWidth, int width)
        {
            return switch (ordinal() % 3)
            {
                case 0 -> MARGIN;
                case 1 -> (screenWidth - width) / 2;
                default -> screenWidth - width - MARGIN;
            };
        }

        public int y(int screenHeight, int height)
        {
            return switch (ordinal() / 3)
            {
                case 0 -> MARGIN;
                case 1 -> (screenHeight - height) / 2;
                default -> screenHeight - height - MARGIN;
            };
        }

        // Gap kept from the screen's edge
        private static final int MARGIN = 4;
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // [reading_hud]
    public static final ModConfigSpec.BooleanValue BOOK_ENABLED;
    public static final ModConfigSpec.EnumValue<Anchor> BOOK_ANCHOR;
    public static final ModConfigSpec.IntValue BOOK_X;
    public static final ModConfigSpec.IntValue BOOK_Y;
    public static final ModConfigSpec.IntValue BOOK_MAX_WIDTH;
    public static final ModConfigSpec.BooleanValue BOOK_HELD;
    // [block_label]
    public static final ModConfigSpec.BooleanValue LABEL_ENABLED;
    public static final ModConfigSpec.EnumValue<Anchor> LABEL_ANCHOR;
    public static final ModConfigSpec.IntValue LABEL_X;
    public static final ModConfigSpec.IntValue LABEL_Y;
    public static final ModConfigSpec.BooleanValue LABEL_AMOUNTS;

    // A static block: runs once when the class loads, in order, so each push/pop wraps the values between them
    static
    {
        BUILDER.comment("The paper showing a book's details while wearing Reading Goggles").push("reading_hud");
        BOOK_ENABLED = BUILDER.define("enabled", true);
        BOOK_ANCHOR = BUILDER.comment("Where on the screen it's pinned").defineEnum("anchor", Anchor.MIDDLE_LEFT);
        BOOK_X = BUILDER.comment("Moved this far right from there (negative: left)").defineInRange("x_offset", 0, -4000, 4000);
        BOOK_Y = BUILDER.comment("Moved this far down from there (negative: up)").defineInRange("y_offset", 0, -4000, 4000);
        BOOK_MAX_WIDTH = BUILDER.comment("Longer lines wrap at this width").defineInRange("max_width", 180, 60, 600);
        BOOK_HELD = BUILDER.comment("Also show a book held in the hand (when not looking at one); turning its pages then",
                "shows here instead of above the hotbar").define("held_books", true);
        BUILDER.pop();

        BUILDER.comment("The small label naming any other block while wearing Reading Goggles").push("block_label");
        LABEL_ENABLED = BUILDER.define("enabled", true);
        LABEL_ANCHOR = BUILDER.comment("Where on the screen it's pinned").defineEnum("anchor", Anchor.TOP_CENTER);
        LABEL_X = BUILDER.comment("Moved this far right from there (negative: left)").defineInRange("x_offset", 0, -4000, 4000);
        LABEL_Y = BUILDER.comment("Moved this far down from there (negative: up)").defineInRange("y_offset", 0, -4000, 4000);
        LABEL_AMOUNTS = BUILDER.comment("Also show the energy, fluids and experience the block holds").define("show_amounts", true);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
