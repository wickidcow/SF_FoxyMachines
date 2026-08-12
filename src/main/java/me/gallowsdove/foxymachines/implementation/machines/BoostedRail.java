package me.gallowsdove.foxymachines.implementation.machines;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.Placeable;
import me.gallowsdove.foxymachines.Items;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;

public class BoostedRail extends SlimefunItem implements Placeable {
    public BoostedRail(@Nonnull SlimefunItemStack item, @Nonnull ItemStack[] recipe, int amount) {
        super(Items.TOOLS_ITEM_GROUP, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe, new SlimefunItemStack(item, amount));
    }
}
