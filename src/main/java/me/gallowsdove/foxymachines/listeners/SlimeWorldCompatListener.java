package me.gallowsdove.foxymachines.listeners;

import com.xzavier0722.mc.plugin.slimefun4.storage.event.SlimefunChunkDataLoadEvent;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps FoxyMachines chunk loaders force-loaded across world/Slimefun data reloads.
 *
 * <p>Modern Slimefun storage no longer exposes BlockStorage#getRawStorage(World).
 * Slimefun Legacy emits SlimefunChunkDataLoadEvent after a chunk's Slimefun block
 * records have been restored, so this listener reapplies the force-load state at
 * that boundary instead of scanning private/raw storage.</p>
 */
public class SlimeWorldCompatListener implements Listener {
    private static final Set<ManagedChunk> MANAGED_CHUNKS = ConcurrentHashMap.newKeySet();

    @EventHandler
    public void onSlimefunChunkDataLoad(SlimefunChunkDataLoadEvent event) {
        boolean containsChunkLoader = event.getChunkData().getAllBlockData().stream()
                .anyMatch(data -> "CHUNK_LOADER".equals(data.getSfId()));

        if (!containsChunkLoader) {
            return;
        }

        Chunk chunk = event.getChunk();
        chunk.setForceLoaded(true);
        markManaged(chunk);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        UUID worldId = world.getUID();

        MANAGED_CHUNKS.removeIf(key -> {
            if (!key.worldId().equals(worldId)) {
                return false;
            }

            if (world.isChunkLoaded(key.x(), key.z())) {
                world.getChunkAt(key.x(), key.z()).setForceLoaded(false);
            }
            return true;
        });
    }

    public static void markManaged(Chunk chunk) {
        MANAGED_CHUNKS.add(new ManagedChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
    }

    public static void unmarkManaged(Chunk chunk) {
        MANAGED_CHUNKS.remove(new ManagedChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
    }

    private record ManagedChunk(UUID worldId, int x, int z) {}
}
