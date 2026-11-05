package com.mowmaster.bibliomania;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Keeps left-click actions on our blocks (taking from a book, killing in a Bestiary, a lectern's left-click) to
// right-click's pace: at most once every TICKS ticks per player.
// Why: when a LeftClickBlock event is cancelled, NeoForge's patch to MultiPlayerGameMode stops before vanilla records
// "mining this block". While the button is held, the client then starts a fresh click every tick, and sends the
// server a new START each time: 20 actions a second, against right-click's 5 (vanilla waits 4 ticks between
// right-click uses). Still cancel the event when this says no, so the block isn't mined.
// The client and server keep separate times (in single player they share this class, so they're kept apart).
public final class LeftClickCooldown
{
    private LeftClickCooldown() {}

    public static final int TICKS = 4;

    private static final Map<UUID, Long> CLIENT = new HashMap<>();
    private static final Map<UUID, Long> SERVER = new HashMap<>();

    // Whether the player may act now; if so, it counts as acting
    public static boolean ready(Player player, Level level)
    {
        Map<UUID, Long> last = level.isClientSide() ? CLIENT : SERVER;
        long now = level.getGameTime();
        Long previous = last.get(player.getUUID());
        // previous > now: the world's time went back (another world, or /time): start over
        if (previous != null && previous <= now && now - previous < TICKS) return false;
        last.put(player.getUUID(), now);
        return true;
    }
}
