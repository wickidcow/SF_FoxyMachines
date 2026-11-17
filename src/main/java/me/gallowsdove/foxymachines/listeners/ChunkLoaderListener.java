package me.gallowsdove.foxymachines.listeners;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import me.gallowsdove.foxymachines.FoxyMachines;
import me.gallowsdove.foxymachines.Items;
import me.gallowsdove.foxymachines.utils.SlimefunBlockDataUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class ChunkLoaderListener implements Listener {
    private final Set<BlockPlaceEvent> provisional =
            Collections.newSetFromMap(new IdentityHashMap<>());
    @EventHandler(ignoreCancelled = true)
    public void onChunkLoaderPlace(@Nonnull BlockPlaceEvent e) {
        if(e.getBlock().getType() != Material.BEACON) {
            return;
        }

        ItemStack item = e.getItemInHand();
        Player p = e.getPlayer();

        if (!SlimefunUtils.isItemSimilar(item, Items.CHUNK_LOADER, true, false)) {
            return;
        }

        Block b = e.getBlockPlaced();
        if (!ChunkLoaderPolicyService.isRegistryHealthy()) {
            e.setCancelled(true);
            p.sendMessage(Component.text(
                    "FoxyMachines Chunk Loader ownership registry could not be read. "
                            + "Placement is blocked to protect saved loaders; check server logs.",
                    NamedTextColor.RED));
            return;
        }
        if (!ChunkLoaderPolicyService.canPlace()) {
            e.setCancelled(true);
            p.sendMessage(Component.text(
                    "FoxyMachines Chunk Loader is disabled in FoxyMachines config.yml.",
                    NamedTextColor.LIGHT_PURPLE));
            return;
        }
        if (b.getChunk().isForceLoaded() || ChunkLoaderPolicyService.isLoaderChunkTracked(b)) {
            e.setCancelled(true);
            p.sendMessage(Component.text("This chunk is already loaded!", NamedTextColor.LIGHT_PURPLE));
            return;
        }

        NamespacedKey key = new NamespacedKey(FoxyMachines.getInstance(), "chunkloaders");

        int i = p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0) + 1;
        Config cfg = new Config(FoxyMachines.getInstance());
        if (!p.hasPermission("foxymachines.bypass-chunk-loader-limit")) {
            int max = cfg.getInt("max-chunk-loaders");
            if (max != 0 && max < i) {
                p.sendMessage(Component.text("Maximum amount of chunk loaders already placed: " + max, NamedTextColor.LIGHT_PURPLE));
                e.setCancelled(true);
                return;
            }
        }
        int currentComplexity = Slimefun.getGPSNetwork().getNetworkComplexity(p.getUniqueId());
        int requiredComplexity = cfg.getInt("gps-complexity-per-loader") * i;
        if (currentComplexity < requiredComplexity) {
            p.sendMessage(Component.text("You have " + currentComplexity + "/" + requiredComplexity + " GPS Network Complexity required to place another Chunk Loader.", NamedTextColor.LIGHT_PURPLE));
            e.setCancelled(true);
            return;
        }

        // The new loader must be durably registered before charging the
        // owner's quota. If its registry cannot be saved (e.g. a full disk),
        // cancel placement rather than creating an untracked block.
        if (!ChunkLoaderPolicyService.register(b, p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(Component.text(
                    "Could not save Chunk Loader ownership; placement cancelled. Check server logs.",
                    NamedTextColor.RED));
            return;
        }
        p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, i);
        SlimefunBlockDataUtil.setValue(b, "owner", p.getUniqueId().toString());
        provisional.add(e);
    }

    /**
     * Other protections may cancel placement at HIGHEST after our NORMAL
     * handler. Roll back the provisional owner/quota so cancelled placements
     * cannot create phantom chunk tickets or consume a user's limit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChunkLoaderPlacementFinalized(BlockPlaceEvent event) {
        if (!provisional.remove(event) || !event.isCancelled()) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (!ChunkLoaderPolicyService.unregister(block)) {
            FoxyMachines.getInstance().getLogger().severe(
                    "A cancelled Chunk Loader placement at " + block.getLocation()
                            + " could not persist rollback; manual review is required.");
            return;
        }
        Player player = event.getPlayer();
        NamespacedKey key = new NamespacedKey(FoxyMachines.getInstance(), "chunkloaders");
        int used = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
        player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, Math.max(0, used - 1));
    }
}
