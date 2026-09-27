package me.gallowsdove.foxymachines.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Isolates the RC-37/legacy ChestMenu click ABI from FoxyMachines machine code.
 *
 * <p>Current Slimefun Legacy still accepts this ABI for classic BlockMenuPreset
 * implementations, while the inherited ChestMenu and ClickAction types are
 * deprecated. Keeping them here preserves behavior without spreading those
 * types through each machine implementation.</p>
 */
@SuppressWarnings("deprecation")
public final class LegacyMenuCompat {

    private static final me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler
        OUTPUT_SLOT_HANDLER =
            new me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler() {

                @Override
                public boolean onClick(
                    Player player,
                    int slot,
                    ItemStack cursor,
                    me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction action
                ) {
                    return false;
                }

                @Override
                public boolean onClick(
                    InventoryClickEvent event,
                    Player player,
                    int slot,
                    @Nullable ItemStack cursor,
                    me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction action
                ) {
                    return cursor == null || cursor.getType() == Material.AIR;
                }
            };

    private LegacyMenuCompat() {}

    @Nonnull
    public static me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.MenuClickHandler runAndCancel(
        @Nonnull Runnable action
    ) {
        return (player, slot, item, clickAction) -> {
            action.run();
            return false;
        };
    }

    @Nonnull
    public static me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler
        getOutputSlotHandler() {
        return OUTPUT_SLOT_HANDLER;
    }
}
