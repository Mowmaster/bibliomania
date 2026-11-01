package com.mowmaster.bibliomania.Blocks.CardCatalog;

// Where each drawer's front is on the card catalog models, made from the models (facing north, in pixels): for each
// size (1-16), each drawer in slot order (top row first, then left to right as seen from the front: the model's
// high x first) as {minX, minY, maxX, maxY}. A closed drawer's front is at z = CLOSED_Z, an open one's at OPEN_Z.
// Generated with the models; regenerate both together.
public final class CardCatalogLayout
{
    private CardCatalogLayout() {}

    public static final float CLOSED_Z = 8f;
    public static final float OPEN_Z = 1f;
    // Each drawer's handle sticks out half a pixel in front of its face
    public static final float HANDLE_DEPTH = 1f;

    // The case's default colour (undyed), and the drawers': the grey textures times these look like the wood
    public static final int CASE_COLOR = 0xCDA15E;
    public static final int DRAWER_COLOR = 0xC19C5F;

    private static final float[][][] DRAWERS = {
            // 1
            {{0.5f, 0.5f, 15.5f, 15.5f}},
            // 2
            {{8f, 0.5f, 15.5f, 15.5f}, {0.5f, 0.5f, 8f, 15.5f}},
            // 3
            {{8f, 8f, 15.5f, 15.5f}, {0.5f, 8f, 8f, 15.5f}, {0.5f, 0.5f, 15.5f, 8f}},
            // 4
            {{8f, 8f, 15.5f, 15.5f}, {0.5f, 8f, 8f, 15.5f}, {8f, 0.5f, 15.5f, 8f}, {0.5f, 0.5f, 8f, 8f}},
            // 5
            {{10.5f, 8f, 15.5f, 15.5f}, {5.5f, 8f, 10.5f, 15.5f}, {0.5f, 8f, 5.5f, 15.5f}, {8f, 0.5f, 15.5f, 8f}, {0.5f, 0.5f, 8f, 8f}},
            // 6
            {{10.5f, 8f, 15.5f, 15.5f}, {5.5f, 8f, 10.5f, 15.5f}, {0.5f, 8f, 5.5f, 15.5f}, {10.5f, 0.5f, 15.5f, 8f}, {5.5f, 0.5f, 10.5f, 8f}, {0.5f, 0.5f, 5.5f, 8f}},
            // 7
            {{11.75f, 8f, 15.5f, 15.5f}, {8f, 8f, 11.75f, 15.5f}, {4.25f, 8f, 8f, 15.5f}, {0.5f, 8f, 4.25f, 15.5f}, {10.5f, 0.5f, 15.5f, 8f}, {5.5f, 0.5f, 10.5f, 8f}, {0.5f, 0.5f, 5.5f, 8f}},
            // 8
            {{11.75f, 8f, 15.5f, 15.5f}, {8f, 8f, 11.75f, 15.5f}, {4.25f, 8f, 8f, 15.5f}, {0.5f, 8f, 4.25f, 15.5f}, {11.75f, 0.5f, 15.5f, 8f}, {8f, 0.5f, 11.75f, 8f}, {4.25f, 0.5f, 8f, 8f}, {0.5f, 0.5f, 4.25f, 8f}},
            // 9
            {{10.5f, 10.5f, 15.5f, 15.5f}, {5.5f, 10.5f, 10.5f, 15.5f}, {0.5f, 10.5f, 5.5f, 15.5f}, {10.5f, 5.5f, 15.5f, 10.5f}, {5.5f, 5.5f, 10.5f, 10.5f}, {0.5f, 5.5f, 5.5f, 10.5f}, {10.5f, 0.5f, 15.5f, 5.5f}, {5.5f, 0.5f, 10.5f, 5.5f}, {0.5f, 0.5f, 5.5f, 5.5f}},
            // 10
            {{11.75f, 10.5f, 15.5f, 15.5f}, {8f, 10.5f, 11.75f, 15.5f}, {4.25f, 10.5f, 8f, 15.5f}, {0.5f, 10.5f, 4.25f, 15.5f}, {10.5f, 5.5f, 15.5f, 10.5f}, {5.5f, 5.5f, 10.5f, 10.5f}, {0.5f, 5.5f, 5.5f, 10.5f}, {10.5f, 0.5f, 15.5f, 5.5f}, {5.5f, 0.5f, 10.5f, 5.5f}, {0.5f, 0.5f, 5.5f, 5.5f}},
            // 11
            {{11.75f, 10.5f, 15.5f, 15.5f}, {8f, 10.5f, 11.75f, 15.5f}, {4.25f, 10.5f, 8f, 15.5f}, {0.5f, 10.5f, 4.25f, 15.5f}, {11.75f, 5.5f, 15.5f, 10.5f}, {8f, 5.5f, 11.75f, 10.5f}, {4.25f, 5.5f, 8f, 10.5f}, {0.5f, 5.5f, 4.25f, 10.5f}, {10.5f, 0.5f, 15.5f, 5.5f}, {5.5f, 0.5f, 10.5f, 5.5f}, {0.5f, 0.5f, 5.5f, 5.5f}},
            // 12
            {{11.75f, 10.5f, 15.5f, 15.5f}, {8f, 10.5f, 11.75f, 15.5f}, {4.25f, 10.5f, 8f, 15.5f}, {0.5f, 10.5f, 4.25f, 15.5f}, {11.75f, 5.5f, 15.5f, 10.5f}, {8f, 5.5f, 11.75f, 10.5f}, {4.25f, 5.5f, 8f, 10.5f}, {0.5f, 5.5f, 4.25f, 10.5f}, {11.75f, 0.5f, 15.5f, 5.5f}, {8f, 0.5f, 11.75f, 5.5f}, {4.25f, 0.5f, 8f, 5.5f}, {0.5f, 0.5f, 4.25f, 5.5f}},
            // 13
            {{11.75f, 11.75f, 15.5f, 15.5f}, {8f, 11.75f, 11.75f, 15.5f}, {4.25f, 11.75f, 8f, 15.5f}, {0.5f, 11.75f, 4.25f, 15.5f}, {10.5f, 8f, 15.5f, 11.75f}, {5.5f, 8f, 10.5f, 11.75f}, {0.5f, 8f, 5.5f, 11.75f}, {10.5f, 4.25f, 15.5f, 8f}, {5.5f, 4.25f, 10.5f, 8f}, {0.5f, 4.25f, 5.5f, 8f}, {10.5f, 0.5f, 15.5f, 4.25f}, {5.5f, 0.5f, 10.5f, 4.25f}, {0.5f, 0.5f, 5.5f, 4.25f}},
            // 14
            {{11.75f, 11.75f, 15.5f, 15.5f}, {8f, 11.75f, 11.75f, 15.5f}, {4.25f, 11.75f, 8f, 15.5f}, {0.5f, 11.75f, 4.25f, 15.5f}, {11.75f, 8f, 15.5f, 11.75f}, {8f, 8f, 11.75f, 11.75f}, {4.25f, 8f, 8f, 11.75f}, {0.5f, 8f, 4.25f, 11.75f}, {10.5f, 4.25f, 15.5f, 8f}, {5.5f, 4.25f, 10.5f, 8f}, {0.5f, 4.25f, 5.5f, 8f}, {10.5f, 0.5f, 15.5f, 4.25f}, {5.5f, 0.5f, 10.5f, 4.25f}, {0.5f, 0.5f, 5.5f, 4.25f}},
            // 15
            {{11.75f, 11.75f, 15.5f, 15.5f}, {8f, 11.75f, 11.75f, 15.5f}, {4.25f, 11.75f, 8f, 15.5f}, {0.5f, 11.75f, 4.25f, 15.5f}, {11.75f, 8f, 15.5f, 11.75f}, {8f, 8f, 11.75f, 11.75f}, {4.25f, 8f, 8f, 11.75f}, {0.5f, 8f, 4.25f, 11.75f}, {11.75f, 4.25f, 15.5f, 8f}, {8f, 4.25f, 11.75f, 8f}, {4.25f, 4.25f, 8f, 8f}, {0.5f, 4.25f, 4.25f, 8f}, {10.5f, 0.5f, 15.5f, 4.25f}, {5.5f, 0.5f, 10.5f, 4.25f}, {0.5f, 0.5f, 5.5f, 4.25f}},
            // 16
            {{11.75f, 11.75f, 15.5f, 15.5f}, {8f, 11.75f, 11.75f, 15.5f}, {4.25f, 11.75f, 8f, 15.5f}, {0.5f, 11.75f, 4.25f, 15.5f}, {11.75f, 8f, 15.5f, 11.75f}, {8f, 8f, 11.75f, 11.75f}, {4.25f, 8f, 8f, 11.75f}, {0.5f, 8f, 4.25f, 11.75f}, {11.75f, 4.25f, 15.5f, 8f}, {8f, 4.25f, 11.75f, 8f}, {4.25f, 4.25f, 8f, 8f}, {0.5f, 4.25f, 4.25f, 8f}, {11.75f, 0.5f, 15.5f, 4.25f}, {8f, 0.5f, 11.75f, 4.25f}, {4.25f, 0.5f, 8f, 4.25f}, {0.5f, 0.5f, 4.25f, 4.25f}}
    };

    // The drawers of a catalog with `size` drawers
    public static float[][] drawers(int size)
    {
        return DRAWERS[Math.clamp(size, 1, 16) - 1];
    }
}
