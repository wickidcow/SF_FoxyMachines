package me.gallowsdove.foxymachines.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Properties;
import java.util.UUID;
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

    @Test
    void allSavedLoaderOwnersMustParseBeforeAnyAreAccepted() {
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Properties valid = new Properties();
        valid.setProperty(world + ";10;64;-4", owner.toString());
        valid.setProperty(world + ";-26;70;42", owner.toString());
        assertEquals(2, ChunkLoaderPolicyService.validatedRegistryEntryCount(valid));

        // Even with one valid entry, one corrupt second entry rejects the
        // whole registry. On disk the original file must remain untouched.
        valid.setProperty(world + ";11;64;-4", "invalid-player-uuid");
        assertThrows(IllegalArgumentException.class,
                () -> ChunkLoaderPolicyService.validatedRegistryEntryCount(valid));
        valid.remove(world + ";11;64;-4");
        assertEquals(2, ChunkLoaderPolicyService.validatedRegistryEntryCount(valid));
    }

    @Test
    void duplicateSerializedCoordinatesCannotOverruleAnotherOwner() {
        UUID world = UUID.randomUUID();
        Properties duplicate = new Properties();
        duplicate.setProperty(world + ";1;64;2", UUID.randomUUID().toString());
        duplicate.setProperty(world + ";01;64;2", UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class,
                () -> ChunkLoaderPolicyService.validatedRegistryEntryCount(duplicate));
    }
}
