package me.gallowsdove.foxymachines.listeners;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.lang.reflect.Method;
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
     * Original Slimefun exposed BlockStorage#getRawStorage(World). Newer Slimefun cores do not.
     * Resolve the old API reflectively so this source still compiles when that method is absent,
     * then fall back to the newer block-data controller used by Slimefun Legacy.
     */
    private Map<Location, ?> getRawStorageCompat(World world) {
        Map<Location, Object> originalStorage = getOriginalRawStorage(world);
        if (originalStorage != null) {
            return originalStorage;
        }
        return getLegacyRawStorage(world);
    }

    private Map<Location, Object> getOriginalRawStorage(World world) {
        try {
            Method method = BlockStorage.class.getMethod("getRawStorage", World.class);
            Object value = method.invoke(null, world);
            if (!(value instanceof Map<?, ?> raw)) {
                return null;
            }

            Map<Location, Object> storage = new HashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() instanceof Location location) {
                    storage.put(location, entry.getValue());
                }
            }
            return storage;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
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
            // Optional compatibility feature: if neither storage API exists, do nothing.
        }

        return storage;
    }
}
