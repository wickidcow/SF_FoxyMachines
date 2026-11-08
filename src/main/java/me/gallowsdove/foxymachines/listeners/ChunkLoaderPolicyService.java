package me.gallowsdove.foxymachines.listeners;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import me.gallowsdove.foxymachines.FoxyMachines;
import me.gallowsdove.foxymachines.utils.SlimefunBlockDataUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * FoxyMachines-only ticket ownership and independent administrator policy.
 * It never reads or changes Slimefun Legacy's Resonance Beacon settings.
 *
 * Old FoxyMachines used persistent Chunk#setForceLoaded. A first-run import
 * must be explicitly enabled after an operator audits possible third-party
 * users of the same vanilla force-loaded chunks.
 */
public final class ChunkLoaderPolicyService implements Listener {

    private static final String POLICY_PATH = "chunk-loading-mode";
    private static ChunkLoaderPolicyService instance;

    private final FoxyMachines plugin;
    private final Path registryFile;
    private final Path pendingFile;
    private final Map<UUID, Integer> pendingOwnerDecrements = new HashMap<>();
    private final Map<BlockKey, UUID> placed = new HashMap<>();
    private final Set<ChunkKey> ticketed = new HashSet<>();
    private ChunkLoaderPolicy activePolicy = ChunkLoaderPolicy.ONLINE;
    // Never replace a corrupt or unreadable historical registry with an
    // empty one. Fail closed until an operator restores the original file.
    private boolean registryHealthy = true;
    // A damaged offline quota journal must not be partially loaded and
    // subsequently overwritten, nor should its owner counters be consumed.
    private boolean deferredOwnersHealthy = true;

    private ChunkLoaderPolicyService(FoxyMachines plugin) {
        this.plugin = plugin;
        registryFile = plugin.getDataFolder().toPath().resolve("chunk-loader-positions.properties");
        pendingFile = plugin.getDataFolder().toPath().resolve("chunk-loader-offline-decrements.properties");
    }

    public static void start(FoxyMachines plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new ChunkLoaderPolicyService(plugin);
        instance.loadRegistry();
        instance.loadDeferredOwners();
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        instance.refresh();
        Bukkit.getScheduler().runTaskTimer(plugin, instance::refresh, 40L, 40L);
        // ChunkLoadEvent is not fired for chunks already loaded when the
        // plugin starts. Validate those separately after Slimefun initializes.
        Bukkit.getScheduler().runTaskLater(plugin, instance::validateAlreadyLoadedRecords, 100L);
        Bukkit.getScheduler().runTaskLater(plugin, instance::discoverOldForceLoaded, 100L);
    }

    public static void stop() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public static boolean canPlace() {
        if (instance == null || !instance.registryHealthy) {
            return false;
        }
        instance.refresh();
        return instance.activePolicy != ChunkLoaderPolicy.OFF;
    }

    public static boolean isRegistryHealthy() {
        return instance != null && instance.registryHealthy
                && instance.deferredOwnersHealthy;
    }

    public static boolean isLoaderChunkTracked(Block block) {
        if (instance == null) {
            return false;
        }
        UUID worldId = block.getWorld().getUID();
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        return instance.placed.keySet().stream().anyMatch(key ->
                key.worldId().equals(worldId) && (key.x() >> 4) == chunkX
                        && (key.z() >> 4) == chunkZ);
    }

    public static boolean register(Block block, UUID owner) {
        if (instance == null || !instance.registryHealthy) {
            return false;
        }
        BlockKey key = BlockKey.of(block);
        UUID previous = instance.placed.put(key, owner);
        if (!instance.saveRegistry()) {
            if (previous == null) {
                instance.placed.remove(key);
            } else {
                instance.placed.put(key, previous);
            }
            return false;
        }
        instance.refresh();
        return true;
    }

    /**
     * Prefer the verified Foxy-owned position record when old Slimefun
     * block-data owner fields are temporarily unavailable. No chunk load.
     */
    public static UUID getRegisteredOwner(Block block) {
        return instance == null ? null : instance.placed.get(BlockKey.of(block));
    }

    public static boolean unregister(Block block) {
        if (instance == null || !instance.registryHealthy) {
            return false;
        }
        BlockKey key = BlockKey.of(block);
        UUID previous = instance.placed.remove(key);
        if (previous == null) {
            return false;
        }
        if (!instance.saveRegistry()) {
            instance.placed.put(key, previous);
            return false;
        }
        instance.refresh();
        return true;
    }

    /**
     * Persist the cumulative break total before an offline owner's next
     * login. A cumulative journal (never deleted on login) is idempotent
     * when paired with the player's last-applied total in the same PDC.
     */
    public static boolean deferOfflineOwnerDecrement(UUID owner) {
        if (instance == null || !instance.deferredOwnersHealthy) {
            return false;
        }
        int old = instance.pendingOwnerDecrements.getOrDefault(owner, 0);
        final int updated;
        try {
            updated = OfflineChunkLoaderQuota.nextJournalTotal(old);
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            instance.plugin.getLogger().log(Level.SEVERE,
                    "Invalid cumulative offline loader count for " + owner, invalid);
            return false;
        }
        instance.pendingOwnerDecrements.put(owner, updated);
        if (!instance.saveDeferredOwners()) {
            if (old == 0) {
                instance.pendingOwnerDecrements.remove(owner);
            } else {
                instance.pendingOwnerDecrements.put(owner, old);
            }
            return false;
        }
        return true;
    }

    private void applyDeferredOwnerDecrement(Player player) {
        if (!deferredOwnersHealthy) {
            plugin.getLogger().severe("Cannot reconcile offline Chunk Loader quota for "
                    + player.getUniqueId() + " while the owner journal is unreadable.");
            return;
        }
        int cumulative = pendingOwnerDecrements.getOrDefault(player.getUniqueId(), 0);
        if (cumulative <= 0) {
            return;
        }
        NamespacedKey quotaKey = new NamespacedKey(plugin, "chunkloaders");
        NamespacedKey appliedKey = new NamespacedKey(plugin, "chunkloaders_offline_applied");
        var pdc = player.getPersistentDataContainer();
        int quota = pdc.getOrDefault(quotaKey, PersistentDataType.INTEGER, 0);
        int applied = pdc.getOrDefault(appliedKey, PersistentDataType.INTEGER, 0);
        final OfflineChunkLoaderQuota.Adjustment adjustment;
        try {
            adjustment = OfflineChunkLoaderQuota.reconcile(quota, cumulative, applied);
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().log(Level.SEVERE,
                    "Conflicting cumulative Chunk Loader quota for " + player.getUniqueId()
                            + "; preserving player PDC for manual review.", invalid);
            return;
        }
        // Store both counters on the same player data object. Do not delete
        // the cumulative journal: repeated login/restart sees zero delta.
        pdc.set(quotaKey, PersistentDataType.INTEGER, adjustment.remaining());
        pdc.set(appliedKey, PersistentDataType.INTEGER, adjustment.appliedTotal());
    }

    private static Map<UUID, Integer> parseDeferredOwnerJournal(Properties entries) {
        Map<UUID, Integer> parsed = new HashMap<>();
        for (String ownerText : entries.stringPropertyNames()) {
            UUID owner = UUID.fromString(ownerText);
            if (!owner.toString().equals(ownerText)) {
                throw new IllegalArgumentException("Noncanonical journal owner UUID");
            }
            int total = Integer.parseInt(entries.getProperty(ownerText));
            if (total <= 0 || parsed.putIfAbsent(owner, total) != null) {
                throw new IllegalArgumentException("Invalid or duplicated offline owner total");
            }
        }
        return parsed;
    }

    static int validatedOfflineOwnerJournalCount(Properties entries) {
        return parseDeferredOwnerJournal(entries).size();
    }

    private void loadDeferredOwners() {
        if (!Files.isRegularFile(pendingFile)) {
            if (Files.exists(pendingFile)) {
                deferredOwnersHealthy = false;
                plugin.getLogger().severe("Offline Chunk Loader journal is not a regular file: "
                        + pendingFile + ". Not overwriting it.");
            }
            return;
        }
        Properties entries = new Properties();
        try (InputStream in = Files.newInputStream(pendingFile)) {
            entries.load(in);
            pendingOwnerDecrements.putAll(parseDeferredOwnerJournal(entries));
        } catch (IOException | IllegalArgumentException error) {
            pendingOwnerDecrements.clear();
            deferredOwnersHealthy = false;
            plugin.getLogger().log(Level.SEVERE,
                    "Cannot safely read offline owner quota journal from " + pendingFile
                            + ". Preserving the original file; blocked offline owner adjustments "
                            + "until an operator repairs it.", error);
        }
    }

    private boolean saveDeferredOwners() {
        if (!deferredOwnersHealthy) {
            return false;
        }
        Properties entries = new Properties();
        for (var entry : pendingOwnerDecrements.entrySet()) {
            entries.setProperty(entry.getKey().toString(), entry.getValue().toString());
        }
        try {
            Files.createDirectories(pendingFile.getParent());
            Path pending = Files.createTempFile(
                    pendingFile.getParent(), "chunk-loader-offline-", ".tmp");
            try {
                try (OutputStream out = Files.newOutputStream(pending)) {
                    entries.store(out, "Cumulative FoxyMachines offline loader decrements; never delete on login");
                }
                try {
                    Files.move(pending, pendingFile,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(pending, pendingFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(pending);
            }
            return true;
        } catch (IOException error) {
            deferredOwnersHealthy = false;
            plugin.getLogger().log(Level.SEVERE,
                    "Cannot persist offline Chunk Loader quota journal; refusing further "
                            + "offline owner mutations until the disk failure is resolved.", error);
            return false;
        }
    }

    private ChunkLoaderPolicy readPolicy() {
        // FoxyMachines has its own config and does not inherit the Resonance
        // Beacon's switch. Reading the file also picks up intentional edits
        // without calling reloadConfig() on unrelated addon settings.
        Path path = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(path)) {
            return ChunkLoaderPolicy.ONLINE;
        }
        var config = YamlConfiguration.loadConfiguration(path.toFile());
        return ChunkLoaderPolicy.parse(config.getString(POLICY_PATH), true);
    }

    private void refresh() {
        ChunkLoaderPolicy requested = readPolicy();
        if (requested != activePolicy) {
            activePolicy = requested;
            plugin.getLogger().info("FoxyMachines independent Chunk Loader policy: " + requested);
        }
        SlimefunItem item = SlimefunItem.getById("CHUNK_LOADER");
        if (item != null && item.getAddon() == plugin) {
            item.setHidden(activePolicy == ChunkLoaderPolicy.OFF);
        }

        Set<ChunkKey> wanted = new HashSet<>();
        if (activePolicy.shouldTicket(!Bukkit.getOnlinePlayers().isEmpty())) {
            for (BlockKey key : placed.keySet()) {
                wanted.add(new ChunkKey(key.worldId(), key.x() >> 4, key.z() >> 4));
            }
        }

        for (ChunkKey key : new HashSet<>(ticketed)) {
            if (wanted.contains(key)) {
                continue;
            }
            World world = Bukkit.getWorld(key.worldId());
            if (world != null) {
                try {
                    world.removePluginChunkTicket(key.x(), key.z(), plugin);
                } catch (RuntimeException error) {
                    plugin.getLogger().log(Level.WARNING,
                            "Could not release FoxyMachines chunk ticket at " + key, error);
                    continue;
                }
            }
            ticketed.remove(key);
        }

        for (ChunkKey key : wanted) {
            if (ticketed.contains(key)) {
                continue;
            }
            World world = Bukkit.getWorld(key.worldId());
            if (world == null) {
                continue;
            }
            try {
                if (world.addPluginChunkTicket(key.x(), key.z(), plugin)) {
                    ticketed.add(key);
                }
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not acquire FoxyMachines chunk ticket at " + key, error);
            }
        }
    }

    /**
     * Legacy force-loaded flags have no plugin owner metadata. Only inspect
     * beacons with an exact FoxyMachines Slimefun ID and UUID owner; do not
     * force-load arbitrary chunks during discovery. An explicit operator
     * opt-in is required to clear their old vanilla force-loaded flags.
     */
    private void discoverOldForceLoaded() {
        if (!registryHealthy) {
            plugin.getLogger().severe("Skipped historical Chunk Loader migration because its "
                    + "saved owner registry could not be safely read.");
            return;
        }
        boolean migrate = plugin.getConfig().getBoolean("migrate-legacy-force-loaded-chunks", false);
        int found = 0;
        int imported = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getForceLoadedChunks()) {
                for (BlockState tile : chunk.getTileEntities()) {
                    if (tile.getType() != Material.BEACON) {
                        continue;
                    }
                    Block block = tile.getBlock();
                    if (!"CHUNK_LOADER".equals(SlimefunBlockDataUtil.getId(block))) {
                        continue;
                    }
                    String ownerText = SlimefunBlockDataUtil.getValue(block, "owner");
                    UUID owner;
                    try {
                        owner = UUID.fromString(ownerText);
                    } catch (RuntimeException invalid) {
                        plugin.getLogger().warning("FoxyMachines loader has no verified owner at "
                                + block.getLocation() + "; retaining vanilla force-loaded flag.");
                        continue;
                    }
                    BlockKey key = BlockKey.of(block);
                    boolean previouslyRecorded = placed.containsKey(key);
                    // Resume interrupted migrations: the block registry may
                    // have been persisted before the legacy flag was cleared.
                    if (previouslyRecorded && !migrate) {
                        continue;
                    }
                    found++;
                    if (!migrate) {
                        continue;
                    }
                    // A force-loaded chunk can be shared; require an explicit
                    // operator opt-in before altering its historical vanilla
                    // force-loaded flag, even for a previously recorded block.
                    if (!previouslyRecorded) {
                        placed.put(key, owner);
                        if (!saveRegistry()) {
                            placed.remove(key);
                            plugin.getLogger().severe("Preserving old force-loaded flag at "
                                    + block.getLocation() + " because its owner record could not be saved.");
                            continue;
                        }
                    }
                    // Never release historical vanilla force-loading before
                    // the verified loader ownership is durable on disk. When
                    // the current policy calls for continuous loading, acquire
                    // our plugin ticket FIRST so machines do not temporarily
                    // drop out of memory during the one-time migration.
                    boolean anyPlayerOnline = !Bukkit.getOnlinePlayers().isEmpty();
                    if (activePolicy.shouldTicket(anyPlayerOnline)) {
                        refresh();
                    }
                    ChunkKey chunkKey = new ChunkKey(
                            world.getUID(), chunk.getX(), chunk.getZ());
                    if (!activePolicy.canReleaseAuditedLegacyFlag(
                            anyPlayerOnline, ticketed.contains(chunkKey))) {
                        plugin.getLogger().severe("Preserving old force-loaded flag at "
                                + block.getLocation()
                                + " because a replacement FoxyMachines ticket was not acquired.");
                        continue;
                    }
                    chunk.setForceLoaded(false);
                    imported++;
                }
            }
        }
        if (found > 0 && !migrate) {
            plugin.getLogger().warning("Found " + found + " historical FoxyMachines force-loaded chunks. "
                    + "They are NOT automatically released or converted. After auditing shared force-load "
                    + "ownership, set migrate-legacy-force-loaded-chunks: true in FoxyMachines config.yml "
                    + "and restart to migrate only verified Foxy loader blocks.");
        }
        if (imported > 0) {
            plugin.getLogger().info("Migrated " + imported + " verified FoxyMachines force-loaded "
                    + "chunks to plugin-owned ticket records.");
            refresh();
        }
    }

    /**
     * A stored position must never hold a ticket forever after an external
     * block editor or explosion removes the real Slimefun block. Do not load
     * chunks just to run this check; only validate an already-loaded chunk.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        boolean tracked = placed.keySet().stream().anyMatch(key ->
                key.worldId().equals(chunk.getWorld().getUID())
                        && key.x() >> 4 == chunk.getX()
                        && key.z() >> 4 == chunk.getZ());
        if (tracked) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> validateLoadedChunk(chunk), 20L);
        }
    }

    private void validateAlreadyLoadedRecords() {
        if (!registryHealthy) {
            return;
        }
        Set<ChunkKey> checked = new HashSet<>();
        for (BlockKey location : new HashSet<>(placed.keySet())) {
            ChunkKey key = new ChunkKey(
                    location.worldId(), location.x() >> 4, location.z() >> 4);
            if (!checked.add(key)) {
                continue;
            }
            World world = Bukkit.getWorld(key.worldId());
            // Do not force-load a dormant chunk just to check it.
            if (world != null && world.isChunkLoaded(key.x(), key.z())) {
                validateLoadedChunk(world.getChunkAt(key.x(), key.z()));
            }
        }
    }

    private void validateLoadedChunk(Chunk chunk) {
        boolean changed = false;
        for (BlockKey key : new HashSet<>(placed.keySet())) {
            if (!key.worldId().equals(chunk.getWorld().getUID())
                    || (key.x() >> 4) != chunk.getX() || (key.z() >> 4) != chunk.getZ()) {
                continue;
            }
            if (!chunk.isLoaded()) {
                continue;
            }
            Block block = chunk.getWorld().getBlockAt(key.x(), key.y(), key.z());
            String actualId = SlimefunBlockDataUtil.getId(block);
            // A temporarily missing block-data cache is not proof of deletion.
            if (block.getType() != Material.BEACON
                    || actualId != null && !"CHUNK_LOADER".equals(actualId)) {
                placed.remove(key);
                changed = true;
                plugin.getLogger().warning("Released stale FoxyMachines Chunk Loader record at "
                        + block.getLocation() + " without touching vanilla forced chunks.");
            }
        }
        if (changed) {
            saveRegistry();
            refresh();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyDeferredOwnerDecrement(event.getPlayer());
            refresh();
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, this::refresh, 1L);
    }

    private void shutdown() {
        for (ChunkKey key : new HashSet<>(ticketed)) {
            World world = Bukkit.getWorld(key.worldId());
            if (world != null) {
                world.removePluginChunkTicket(key.x(), key.z(), plugin);
            }
        }
        ticketed.clear();
        saveRegistry();
    }

    /**
     * Parse the complete registry before applying any record. A single bad
     * entry must not allow a later shutdown or placement to erase the owner's
     * remaining, valid historical loader records.
     */
    private static Map<BlockKey, UUID> parseRegistryEntries(Properties entries) {
        Map<BlockKey, UUID> parsed = new HashMap<>();
        for (String raw : entries.stringPropertyNames()) {
            BlockKey position = BlockKey.parse(raw);
            UUID owner = UUID.fromString(entries.getProperty(raw));
            if (parsed.putIfAbsent(position, owner) != null) {
                throw new IllegalArgumentException("Duplicate recorded position: " + raw);
            }
        }
        return parsed;
    }

    /** Pure structural validation for the no-loss persistence regression. */
    static int validatedRegistryEntryCount(Properties entries) {
        return parseRegistryEntries(entries).size();
    }

    private void loadRegistry() {
        if (!Files.isRegularFile(registryFile)) {
            if (Files.exists(registryFile)) {
                registryHealthy = false;
                plugin.getLogger().severe("FoxyMachines Chunk Loader registry is not a regular file: "
                        + registryFile + ". Not overwriting it.");
            }
            return;
        }
        Properties entries = new Properties();
        try (InputStream in = Files.newInputStream(registryFile)) {
            entries.load(in);
            placed.putAll(parseRegistryEntries(entries));
        } catch (IOException | IllegalArgumentException error) {
            placed.clear();
            registryHealthy = false;
            plugin.getLogger().log(Level.SEVERE,
                    "Cannot safely load historical FoxyMachines Chunk Loader records from "
                            + registryFile + "; refusing placements, breaking and legacy migration "
                            + "until the original ownership file is repaired.", error);
        }
    }

    private boolean saveRegistry() {
        if (!registryHealthy) {
            plugin.getLogger().severe("Refusing to overwrite unreadable Chunk Loader owner registry.");
            return false;
        }
        Properties entries = new Properties();
        for (var entry : placed.entrySet()) {
            entries.setProperty(entry.getKey().serialize(), entry.getValue().toString());
        }
        try {
            Files.createDirectories(registryFile.getParent());
            Path pending = registryFile.resolveSibling("chunk-loader-positions.properties.tmp");
            try (OutputStream out = Files.newOutputStream(pending)) {
                entries.store(out, "FoxyMachines chunk loader positions (Slimefun IDs preserved)");
            }
            try {
                Files.move(pending, registryFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(pending, registryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE,
                    "Cannot persist FoxyMachines chunk-loader registry; leaving live blocks intact.", error);
            return false;
        }
    }

    private record ChunkKey(UUID worldId, int x, int z) {}

    private record BlockKey(UUID worldId, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
        static BlockKey parse(String text) {
            String[] values = text.split(";", -1);
            if (values.length != 4) {
                throw new IllegalArgumentException("Invalid chunk-loader position");
            }
            return new BlockKey(UUID.fromString(values[0]), Integer.parseInt(values[1]),
                    Integer.parseInt(values[2]), Integer.parseInt(values[3]));
        }
        String serialize() {
            return worldId + ";" + x + ";" + y + ";" + z;
        }
    }
}
