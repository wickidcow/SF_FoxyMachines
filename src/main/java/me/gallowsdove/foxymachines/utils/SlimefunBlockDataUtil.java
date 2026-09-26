package me.gallowsdove.foxymachines.utils;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * FoxyMachines' adapter for Slimefun Legacy block data.
 *
 * <p>These methods mirror the load/read/write/remove behavior of the deprecated
 * BlockStorage facade without changing any persisted key or value.</p>
 */
public final class SlimefunBlockDataUtil {

    private SlimefunBlockDataUtil() {}

    public static @Nullable SlimefunBlockData getData(@NotNull Location location) {
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        SlimefunBlockData data = controller.getBlockData(location);
        if (data != null && !data.isDataLoaded()) {
            controller.loadBlockData(data);
        }
        return data;
    }

    public static @Nullable SlimefunBlockData getData(@NotNull Block block) {
        return getData(block.getLocation());
    }

    public static @Nullable String getValue(@NotNull Location location, @NotNull String key) {
        SlimefunBlockData data = getData(location);
        return data == null ? null : data.getData(key);
    }

    public static @Nullable String getValue(@NotNull Block block, @NotNull String key) {
        return getValue(block.getLocation(), key);
    }

    public static void setValue(@NotNull Location location, @NotNull String key, @Nullable String value) {
        SlimefunBlockData data = getData(location);
        if (data == null) {
            return;
        }

        if (value == null) {
            data.removeData(key);
        } else {
            data.setData(key, value);
        }
    }

    public static void setValue(@NotNull Block block, @NotNull String key, @Nullable String value) {
        setValue(block.getLocation(), key, value);
    }

    public static @Nullable BlockMenu getMenu(@NotNull Block block) {
        SlimefunBlockData data = getData(block);
        return data == null ? null : data.getBlockMenu();
    }

    public static void remove(@NotNull Location location) {
        Slimefun.getDatabaseManager().getBlockDataController().removeBlock(location);
    }

    public static void remove(@NotNull Block block) {
        remove(block.getLocation());
    }
}
