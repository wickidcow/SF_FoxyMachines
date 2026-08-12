package me.gallowsdove.foxymachines.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.gallowsdove.foxymachines.FoxyMachines;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Releases Chunk Loader placement quota even when the owner is offline.
 * Pending decrements are persisted and applied on the owner's next join.
 */
public final class ChunkLoaderQuotaService implements Listener {

    private static final Type PENDING_TYPE = new TypeToken<Map<UUID, Integer>>() {}.getType();

    private final FoxyMachines plugin;
    private final Gson gson = new Gson();
    private final Map<UUID, Integer> pendingDecrements = new HashMap<>();
    private final Path storageFile;
    private final NamespacedKey quotaKey;

    public ChunkLoaderQuotaService(@Nonnull FoxyMachines plugin) {
        this.plugin = plugin;
        this.storageFile = plugin.getDataFolder().toPath()
                .resolve("data-storage")
                .resolve("chunk-loader-decrements.json");
        this.quotaKey = new NamespacedKey(plugin, "chunkloaders");
        load();
    }

    public void release(@Nonnull UUID ownerId) {
        Player player = plugin.getServer().getPlayer(ownerId);
        if (player != null) {
            decrement(player, 1);
            return;
        }

        pendingDecrements.merge(ownerId, 1, Integer::sum);
        save();
    }

    @EventHandler
    public void onPlayerJoin(@Nonnull PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Integer pending = pendingDecrements.remove(playerId);
        if (pending == null || pending <= 0) {
            return;
        }

        decrement(event.getPlayer(), pending);
        save();
    }

    private void decrement(@Nonnull Player player, int amount) {
        int current = player.getPersistentDataContainer()
                .getOrDefault(quotaKey, PersistentDataType.INTEGER, 0);
        player.getPersistentDataContainer().set(
                quotaKey,
                PersistentDataType.INTEGER,
                Math.max(0, current - amount)
        );
    }

    private void load() {
        if (!Files.exists(storageFile)) {
            return;
        }

        try {
            Map<UUID, Integer> restored = gson.fromJson(Files.readString(storageFile), PENDING_TYPE);
            if (restored != null) {
                restored.forEach((uuid, amount) -> {
                    if (uuid != null && amount != null && amount > 0) {
                        pendingDecrements.put(uuid, amount);
                    }
                });
            }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not load pending chunk-loader quota releases; keeping the file for recovery.",
                    exception
            );
        }
    }

    private void save() {
        try {
            Files.createDirectories(storageFile.getParent());
            Path temporaryFile = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
            Files.writeString(temporaryFile, gson.toJson(pendingDecrements));
            try {
                Files.move(
                        temporaryFile,
                        storageFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, storageFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not persist pending chunk-loader quota releases.", exception);
        }
    }
}
