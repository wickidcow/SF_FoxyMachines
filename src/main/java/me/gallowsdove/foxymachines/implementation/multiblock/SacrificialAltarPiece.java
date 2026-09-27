package me.gallowsdove.foxymachines.implementation.multiblock;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import me.gallowsdove.foxymachines.Items;
import me.gallowsdove.foxymachines.utils.SlimefunBlockDataUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

public class SacrificialAltarPiece extends SlimefunItem {
    public SacrificialAltarPiece(@Nonnull SlimefunItemStack item, @Nonnull ItemStack[] recipe, int amount) {
        super(Items.ALTAR_ITEM_GROUP, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe, new SlimefunItemStack(item, amount));
    }

    @Override
    public void preRegister() {
        addItemHandler(onBlockBreak());
    }

    private BlockBreakHandler onBlockBreak() {
        return new BlockBreakHandler(false, false) {
            @ParametersAreNonnullByDefault
            @Override
            public void onPlayerBreak(BlockBreakEvent e, ItemStack item, List<ItemStack> drops) {
                Block b = findAltar(e.getBlock());

                if (b != null) {
                    if ("true".equals(SlimefunBlockDataUtil.getValue(b, "complete"))) {
                        SlimefunBlockDataUtil.setValue(b, "complete", "false");
                        e.getPlayer().sendMessage(Component.text(
                            "Part of the altar has been broken, please repair it and reactivate it!",
                            NamedTextColor.LIGHT_PURPLE
                        ));
                    }
                }

                SlimefunBlockDataUtil.remove(e.getBlock());
            }
        };
    }

    @Nullable
    private Block findAltar(@Nonnull Block b) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }

                    Block block = b.getRelative(x, y, z);

                    if (block.getType() == Material.POLISHED_BLACKSTONE_PRESSURE_PLATE
                            && "SACRIFICIAL_ALTAR_BLACKSTONE_PRESSURE_PLATE".equals(SlimefunBlockDataUtil.getId(block))) {
                        return block;
                    }
                }
            }
        }

        return null;
    }
}
