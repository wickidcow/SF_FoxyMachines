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
import me.gallowsdove.foxymachines.listeners.SlimeWorldCompatListener;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
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
        addItemHandler(onBreak(), onBlockUse());
    }

    @Nonnull
    private BlockBreakHandler onBreak() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e, @Nonnull ItemStack item, @Nonnull List<ItemStack> drops) {
                Block b = e.getBlock();
                String owner = BlockStorage.getLocationInfo(b.getLocation(), "owner");
                if (owner != null) {
                    try {
                        FoxyMachines.getInstance().getChunkLoaderQuotaService().release(UUID.fromString(owner));
                    } catch (IllegalArgumentException ignored) {
                        FoxyMachines.log(Level.WARNING, "Ignoring Chunk Loader with invalid owner data at " + b.getLocation());
                    }

                    b.getChunk().setForceLoaded(false);
                    SlimeWorldCompatListener.unmarkManaged(b.getChunk());
                    BlockStorage.clearBlockInfo(b);
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
