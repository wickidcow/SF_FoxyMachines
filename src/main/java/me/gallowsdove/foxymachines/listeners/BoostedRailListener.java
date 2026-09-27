package me.gallowsdove.foxymachines.listeners;

import me.gallowsdove.foxymachines.utils.SlimefunBlockDataUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import javax.annotation.Nonnull;
import java.util.Set;

public class BoostedRailListener implements Listener {
    private static final Set<Material> RAILS = Set.of(Material.RAIL, Material.ACTIVATOR_RAIL, Material.DETECTOR_RAIL, Material.POWERED_RAIL);

    @EventHandler(ignoreCancelled = true)
    private void onRailUse(@Nonnull VehicleMoveEvent e) {
        if (e.getVehicle() instanceof Minecart cart) {
            Block b = cart.getLocation().getBlock();
            if (RAILS.contains(b.getType())) {
                if (SlimefunBlockDataUtil.getValue(b, "boosted") != null) {
                    cart.setMaxSpeed(1d);
                } else {
                    cart.setMaxSpeed(.4d);
                }
            }
        }
    }
}
