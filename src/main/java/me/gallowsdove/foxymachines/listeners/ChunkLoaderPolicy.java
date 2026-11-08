package me.gallowsdove.foxymachines.listeners;

import java.util.Locale;

/** Policy shared with Slimefun Legacy's Resonance Beacon settings. */
public enum ChunkLoaderPolicy {
    ONLINE, ALWAYS, OFF;

    static ChunkLoaderPolicy parse(String configured, boolean previousEnabled) {
        if (configured == null || configured.isBlank()) {
            return previousEnabled ? ONLINE : OFF;
        }
        try {
            return valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OFF;
        }
    }

    boolean shouldTicket(boolean anyPlayerOnline) {
        return this == ALWAYS || this == ONLINE && anyPlayerOnline;
    }
}
