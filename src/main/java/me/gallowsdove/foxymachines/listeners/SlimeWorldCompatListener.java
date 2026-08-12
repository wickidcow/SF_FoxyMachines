package me.gallowsdove.foxymachines.listeners;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashMap;
import java.util.Map;

public class SlimeWorldCompatListener implements Listener {

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        reapplyChunkLoaders(e.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        clearChunkLoaders(e.getWorld());
    }

    private void reapplyChunkLoaders(World world) {
        Map<Location, ?> storage = getRawStorageCompat(world);
        if (storage == null) return;

        for (Location loc : storage.keySet()) {
            try {
                String id = BlockStorage.checkID(loc);
                if ("CHUNK_LOADER".equals(id)) {
                    loc.getChunk().setForceLoaded(true);
                }
            } catch (Exception | LinkageError ignored) {
            }
        }
    }

    private void clearChunkLoaders(World world) {
        Map<Location, ?> storage = getRawStorageCompat(world);
        if (storage == null) return;

        for (Location loc : storage.keySet()) {
            try {
                String id = BlockStorage.checkID(loc);
                if ("CHUNK_LOADER".equals(id)) {
                    loc.getChunk().setForceLoaded(false);
                }
            } catch (Exception | LinkageError ignored) {
            }
        }
    }

    /**
     * Original Slimefun exposed BlockStorage#getRawStorage(World). Slimefun Legacy uses the
     * newer BlockDataController instead. Keep the old call for upstream compatibility and
     * transparently fall back to the Legacy controller when that binary API is unavailable.
     */
    private Map<Location, ?> getRawStorageCompat(World world) {
        try {
            return BlockStorage.getRawStorage(world);
        } catch (NoSuchMethodError ignored) {
            return getLegacyRawStorage(world);
        }
    }

    private Map<Location, Object> getLegacyRawStorage(World world) {
        Map<Location, Object> storage = new HashMap<>();

        try {
            Class<?> slimefunClass = Class.forName("io.github.thebusybiscuit.slimefun4.implementation.Slimefun");
            Object databaseManager = slimefunClass.getMethod("getDatabaseManager").invoke(null);
            Object controller = databaseManager.getClass().getMethod("getBlockDataController").invoke(databaseManager);
            Object loadedChunks = controller.getClass()
                    .getMethod("getAllLoadedChunkData", World.class)
                    .invoke(controller, world);

            if (!(loadedChunks instanceof Iterable<?> chunks)) {
                return storage;
            }

            for (Object chunkData : chunks) {
                Object allBlockData = chunkData.getClass().getMethod("getAllBlockData").invoke(chunkData);
                if (!(allBlockData instanceof Iterable<?> blocks)) {
                    continue;
                }

                for (Object blockData : blocks) {
                    Object location = blockData.getClass().getMethod("getLocation").invoke(blockData);
                    if (location instanceof Location loc) {
                        storage.put(loc, blockData);
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // If neither storage API is available, leave this optional compatibility feature inactive.
        }

        return storage;
    }
}
