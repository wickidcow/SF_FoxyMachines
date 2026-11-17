package me.gallowsdove.foxymachines.implementation.machines;

import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import me.gallowsdove.foxymachines.FoxyMachines;
import me.gallowsdove.foxymachines.Items;
import me.gallowsdove.foxymachines.listeners.ChunkLoaderPolicyService;
import me.gallowsdove.foxymachines.utils.SlimefunBlockDataUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;


public class ChunkLoader extends SlimefunItem {
    public ChunkLoader() {
        super(Items.MACHINES_ITEM_GROUP, Items.CHUNK_LOADER, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                Items.REINFORCED_STRING, Items.STABILIZED_BLISTERING_BLOCK, Items.REINFORCED_STRING,
                SlimefunItems.ENRICHED_NETHER_ICE, Items.STABILIZED_BLISTERING_BLOCK, Items.WIRELESS_TRANSMITTER,
                Items.REINFORCED_STRING, Items.STABILIZED_BLISTERING_BLOCK, Items.REINFORCED_STRING
        });
    }

    @Override
    public void preRegister() {
        addItemHandler(onBreak(), onBlockUse());
    }

    @Nonnull
    private BlockBreakHandler onBreak() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e, @Nonnull ItemStack item, @Nonnull List<ItemStack> drops) {
                Block b = e.getBlock();
                if (!ChunkLoaderPolicyService.isRegistryHealthy()) {
                    e.setCancelled(true);
                    FoxyMachines.getInstance().getLogger().severe(
                            "Chunk Loader break cancelled at " + b.getLocation()
                                    + ": historical ownership registry is unreadable.");
                    return;
                }
                String owner = SlimefunBlockDataUtil.getValue(b, "owner");
                UUID registeredOwner = ChunkLoaderPolicyService.getRegisteredOwner(b);
                boolean managed = ChunkLoaderPolicyService.unregister(b);
                if (registeredOwner != null && !managed) {
                    // Preserve the physical loader and existing quota if
                    // its owner registry could not be safely persisted.
                    e.setCancelled(true);
                    FoxyMachines.getInstance().getLogger().severe(
                            "Cancelled Chunk Loader break at " + b.getLocation()
                                    + ": the owner registry update failed.");
                    return;
                }
                if (owner != null || registeredOwner != null) {
                    try {
                        UUID ownerId = owner == null ? registeredOwner : UUID.fromString(owner);
                        if (registeredOwner != null && !registeredOwner.equals(ownerId)) {
                            // A stale or conflicting record must never decrement
                            // another player's persistent placement quota.
                            FoxyMachines.getInstance().getLogger().warning(
                                    "Conflicting stored owner at " + b.getLocation()
                                            + "; left player quota unchanged for operator review.");
                        } else {
                            NamespacedKey key = new NamespacedKey(FoxyMachines.getInstance(), "chunkloaders");
                            Player player = Bukkit.getPlayer(ownerId);
                            if (player != null) {
                                int count = player.getPersistentDataContainer()
                                        .getOrDefault(key, PersistentDataType.INTEGER, 0);
                                player.getPersistentDataContainer().set(
                                        key, PersistentDataType.INTEGER, Math.max(0, count - 1));
                            } else if (!ChunkLoaderPolicyService.deferOfflineOwnerDecrement(ownerId)) {
                                // A failed durable journal write cannot be
                                // treated as a successful quota refund.
                                // Preserve the placed loader and its owner
                                // record instead of silently losing the
                                // offline player's adjustment.
                                if (managed && !ChunkLoaderPolicyService.register(b, ownerId)) {
                                    FoxyMachines.getInstance().getLogger().severe(
                                            "Unable to restore owner registry after offline quota journal failure at "
                                                    + b.getLocation() + ". Operator recovery required.");
                                }
                                e.setCancelled(true);
                                FoxyMachines.getInstance().getLogger().severe(
                                        "Chunk Loader break blocked at " + b.getLocation()
                                                + " because its offline owner's quota journal could not be saved.");
                                return;
                            }
                        }
                    } catch (IllegalArgumentException invalidOwner) {
                        FoxyMachines.getInstance().getLogger().warning(
                                "Could not parse historical Chunk Loader owner; leaving owner count unchanged.");
                    }
                    if (!managed && b.getChunk().isForceLoaded()) {
                        // A legacy vanilla force-load flag has no recorded owner.
                        // Even a removed FoxyMachines block does not prove the
                        // flag is unused by an administrator or another plugin.
                        // Do NOT clear it implicitly. The operator can review
                        // remaining flags and use the explicit audited migration.
                        FoxyMachines.getInstance().getLogger().warning(
                                "Old FoxyMachines loader broken at " + b.getLocation()
                                        + "; retained ambiguous vanilla force-loaded chunk flag. "
                                        + "Audit it before using /forceload remove.");
                    }
                    SlimefunBlockDataUtil.remove(b);
                }

                Scheduler.run(() -> b.setType(Material.GLASS));
            }
        };
    }

    @Nonnull
    private BlockUseHandler onBlockUse() {
        return PlayerRightClickEvent::cancel;
    }

}
