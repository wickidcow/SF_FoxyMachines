package me.gallowsdove.foxymachines.implementation.machines;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import me.gallowsdove.foxymachines.FoxyMachines;
import me.gallowsdove.foxymachines.Items;
import me.gallowsdove.foxymachines.listeners.SlimeWorldCompatListener;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

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
        addItemHandler(onBreak(), onBlockUse(), onPlace());
    }

    @Nonnull
    private BlockBreakHandler onBreak() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e, @Nonnull ItemStack item, @Nonnull List<ItemStack> drops) {
                Block b = e.getBlock();
                String owner = StorageCacheUtils.getData(b.getLocation(), "owner");
                if (owner != null) {
                    try {
                        FoxyMachines.getInstance().getChunkLoaderQuotaService().release(UUID.fromString(owner));
                    } catch (IllegalArgumentException ignored) {
                        FoxyMachines.log(Level.WARNING, "Ignoring Chunk Loader with invalid owner data at " + b.getLocation());
                    }
                }

                b.getChunk().setForceLoaded(false);
                SlimeWorldCompatListener.unmarkManaged(b.getChunk());
                BlockStorage.clearBlockInfo(b);
                Scheduler.run(() -> b.setType(Material.GLASS));
            }
        };
    }

    @Nonnull
    private BlockUseHandler onBlockUse() {
        return PlayerRightClickEvent::cancel;
    }

    @Nonnull
    private BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent e) {
                var container = StorageCacheUtils.getBlock(e.getBlock().getLocation());
                if (container == null) {
                    FoxyMachines.log(Level.WARNING, "Could not persist Chunk Loader owner data at " + e.getBlock().getLocation());
                    return;
                }

                StorageCacheUtils.executeAfterLoad(container, () -> StorageCacheUtils.setData(
                        e.getBlock().getLocation(),
                        "owner",
                        e.getPlayer().getUniqueId().toString()
                ), true);
            }
        };
    }
}
