package me.gallowsdove.foxymachines.listeners;

import java.util.Locale;

/** Standalone FoxyMachines chunk-loader policy, independent of other plugins. */
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

    /**
     * A manually audited historical vanilla force-load flag may be dropped
     * only if the policy does not require a replacement ticket right now, or
     * after a Foxy-owned replacement ticket has actually been acquired.
     */
    boolean canReleaseAuditedLegacyFlag(boolean anyPlayerOnline, boolean replacementTicketHeld) {
        return !shouldTicket(anyPlayerOnline) || replacementTicketHeld;
    }
}
