package me.gallowsdove.foxymachines.implementation.multiblock;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import me.gallowsdove.foxymachines.Items;
import me.gallowsdove.foxymachines.utils.SlimefunBlockDataUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;

public class SacrificialAltarPressurePlate extends SlimefunItem {
    public SacrificialAltarPressurePlate() {
        super(Items.ALTAR_ITEM_GROUP, Items.SACRIFICIAL_ALTAR_BLACKSTONE_PRESSURE_PLATE, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                new ItemStack(Material.GHAST_TEAR), SlimefunItems.MAGIC_LUMP_3, new ItemStack(Material.GHAST_TEAR),
                SlimefunItems.MAGIC_LUMP_3, new ItemStack(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE), SlimefunItems.MAGIC_LUMP_3,
                new ItemStack(Material.GHAST_TEAR), SlimefunItems.MAGIC_LUMP_3, new ItemStack(Material.GHAST_TEAR)
        });
    }

    @Override
    public void preRegister() {
        addItemHandler(onPlace(), onUse(), onBreak());
    }

    private BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {

            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent e) {
                Block b = e.getBlockPlaced();
                if (isComplete(b)) {
                    SlimefunBlockDataUtil.setValue(b, "complete", "true");
                    e.getPlayer().sendMessage(Component.text("The Sacrificial Altar has been activated.", NamedTextColor.LIGHT_PURPLE));
                } else {
                    SlimefunBlockDataUtil.setValue(b, "complete", "false");
                    e.getPlayer().sendMessage(Component.text("Finish your Altar and click this block again to activate it.", NamedTextColor.LIGHT_PURPLE));
                }
            }
        };
    }

    private BlockUseHandler onUse() {
        return e -> {
            Block b = e.getClickedBlock().get();
            if ("false".equals(SlimefunBlockDataUtil.getValue(b, "complete"))) {
                if (isComplete(b)) {
                    SlimefunBlockDataUtil.setValue(b, "complete", "true");
                    e.getPlayer().sendMessage(Component.text("The Sacrificial Altar has been activated.", NamedTextColor.LIGHT_PURPLE));
                } else {
                    SlimefunBlockDataUtil.setValue(b, "complete", "false");
                    e.getPlayer().sendMessage(Component.text("The Altar is not finished!", NamedTextColor.LIGHT_PURPLE));
                }
            }

            e.cancel();
        };
    }

    private BlockBreakHandler onBreak() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e, @Nonnull ItemStack item, @Nonnull List<ItemStack> drops) {
                SlimefunBlockDataUtil.setValue(e.getBlock(), "complete", null);
                SlimefunBlockDataUtil.remove(e.getBlock());
                e.getPlayer().sendMessage(Component.text("The Altar has been broken!", NamedTextColor.LIGHT_PURPLE));
            }
        };
    }

    private boolean isComplete(@Nonnull Block b) {

        if (b.getRelative(1, 1, 1).getType() != Material.POLISHED_BLACKSTONE_BRICK_STAIRS || !isAltarPiece(b.getRelative(1, 1, 1)) ||
                b.getRelative(-1, 1, 1).getType() != Material.POLISHED_BLACKSTONE_BRICK_STAIRS || !isAltarPiece(b.getRelative(-1, 1, 1)) ||
                b.getRelative(1, 1, -1).getType() != Material.POLISHED_BLACKSTONE_BRICK_STAIRS || !isAltarPiece(b.getRelative(1, 1, -1)) ||
            b.getRelative(-1, 1, -1).getType() != Material.POLISHED_BLACKSTONE_BRICK_STAIRS || !isAltarPiece(b.getRelative(-1, 1, -1))) {
            return false;
        }

        if (b.getRelative(1, 0, 1).getType() != Material.POLISHED_BLACKSTONE_BRICKS || !isAltarPiece(b.getRelative(1, 0, 1)) ||
                b.getRelative(-1, 0, 1).getType() != Material.POLISHED_BLACKSTONE_BRICKS || !isAltarPiece(b.getRelative(-1, 0, 1)) ||
                b.getRelative(1, 0, -1).getType() != Material.POLISHED_BLACKSTONE_BRICKS || !isAltarPiece(b.getRelative(1, 0, -1)) ||
                b.getRelative(-1, 0, -1).getType() != Material.POLISHED_BLACKSTONE_BRICKS || !isAltarPiece(b.getRelative(-1, 0, -1))) {
            return false;
        }

        if (b.getRelative(0, 1, 1).getType() != Material.SOUL_TORCH || !isAltarPiece(b.getRelative(0, 1, 1)) ||
                b.getRelative(0, 1, -1).getType() != Material.SOUL_TORCH || !isAltarPiece(b.getRelative(0, 1, -1)) ||
                b.getRelative(1, 1, 0).getType() != Material.SOUL_TORCH || !isAltarPiece(b.getRelative(1, 1, 0)) ||
                b.getRelative(-1, 1, 0).getType() != Material.SOUL_TORCH || !isAltarPiece(b.getRelative(-1, 1, 0))) {
            return false;
        }

        if (b.getRelative(0, 0, 1).getType() != Material.POLISHED_BLACKSTONE_BRICK_WALL || !isAltarPiece(b.getRelative(0, 0, 1)) ||
                b.getRelative(0, 0, -1).getType() != Material.POLISHED_BLACKSTONE_BRICK_WALL || !isAltarPiece(b.getRelative(0, 0, -1)) ||
                b.getRelative(1, 0, 0).getType() != Material.POLISHED_BLACKSTONE_BRICK_WALL || !isAltarPiece(b.getRelative(1, 0, 0)) ||
                b.getRelative(-1, 0, 0).getType() != Material.POLISHED_BLACKSTONE_BRICK_WALL || !isAltarPiece(b.getRelative(-1, 0, 0))) {
            return false;
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (b.getRelative(x, -1, z).getType() != Material.POLISHED_BLACKSTONE_BRICKS || !isAltarPiece(b.getRelative(x, -1, z))) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isAltarPiece(@Nonnull Block b) {
        String id = SlimefunBlockDataUtil.getId(b);
        if (id == null) {
            return false;
        }

        return switch (id) {
            case "SACRIFICIAL_ALTAR_BLACKSTONE_BRICKS", "SACRIFICIAL_ALTAR_BLACKSTONE_BRICK_WALL", "SACRIFICIAL_ALTAR_BLACKSTONE_BRICK_STAIRS", "SACRIFICIAL_ALTAR_SOUL_TORCH" -> true;
            default -> false;
        };
    }
}