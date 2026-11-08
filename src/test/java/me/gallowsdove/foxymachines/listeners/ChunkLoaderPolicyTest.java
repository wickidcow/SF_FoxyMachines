package me.gallowsdove.foxymachines.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkLoaderPolicyTest {
    @Test
    void onlineIsDefaultAndDependsOnServerPlayerCount() {
        assertEquals(ChunkLoaderPolicy.ONLINE, ChunkLoaderPolicy.parse(null, true));
        assertFalse(ChunkLoaderPolicy.ONLINE.shouldTicket(false));
        assertTrue(ChunkLoaderPolicy.ONLINE.shouldTicket(true));
    }

    @Test
    void alwaysHoldsTicketsWithNobodyOnline() {
        assertEquals(ChunkLoaderPolicy.ALWAYS, ChunkLoaderPolicy.parse(" always ", false));
        assertTrue(ChunkLoaderPolicy.ALWAYS.shouldTicket(false));
        assertTrue(ChunkLoaderPolicy.ALWAYS.shouldTicket(true));
    }

    @Test
    void explicitOffAndOldDisabledNeverTicket() {
        assertEquals(ChunkLoaderPolicy.OFF, ChunkLoaderPolicy.parse(null, false));
        assertEquals(ChunkLoaderPolicy.OFF, ChunkLoaderPolicy.parse("off", true));
        assertFalse(ChunkLoaderPolicy.OFF.shouldTicket(true));
        assertFalse(ChunkLoaderPolicy.OFF.shouldTicket(false));
    }

    @Test
    void invalidConfigFailsClosedRatherThanRunningUnattended() {
        assertEquals(ChunkLoaderPolicy.OFF, ChunkLoaderPolicy.parse("invalid", true));
        assertEquals(ChunkLoaderPolicy.OFF, ChunkLoaderPolicy.parse("true", true));
    }
}
