package me.gallowsdove.foxymachines.implementation.machines;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import me.gallowsdove.foxymachines.FoxyMachines;
import me.gallowsdove.foxymachines.Items;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ElectricGoldRefinery extends SlimefunItem implements EnergyNetComponent {
    private static final int[] BORDER = { 2, 3, 4, 5, 6, 7, 8, 14, 23, 32, 41, 47, 48, 49, 50, 51, 52, 53 };
    private static final int[] BORDER_IN = { 11, 12, 13, 20, 22, 29, 31, 38, 39, 40 };
    private static final int[] BORDER_OUT = { 15, 16, 17, 24, 26, 33, 35, 42, 43, 44 };
    private static final int[] GOLD_INDEXES = { 0, 9, 18, 27, 36, 45, 1, 10, 19, 28, 37, 46 };
    private static final ItemStack[] GOLDS = {
            SlimefunItems.GOLD_4K, SlimefunItems.GOLD_6K, SlimefunItems.GOLD_8K,
            SlimefunItems.GOLD_10K, SlimefunItems.GOLD_12K, SlimefunItems.GOLD_14K,
            SlimefunItems.GOLD_16K, SlimefunItems.GOLD_18K, SlimefunItems.GOLD_20K,
            SlimefunItems.GOLD_22K, SlimefunItems.GOLD_24K
    };

    public static final int ENERGY_CONSUMPTION = 36;
    public static final int CAPACITY = 512;

    public static final Map<Block, MachineRecipe> processing = new HashMap<>();
    public static final Map<Block, Integer> progress = new HashMap<>();

    public ElectricGoldRefinery() {
        super(Items.MACHINES_ITEM_GROUP, Items.ELECTRIC_GOLD_REFINERY, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                SlimefunItems.HARDENED_METAL_INGOT, SlimefunItems.ELECTRIC_MOTOR, SlimefunItems.HARDENED_METAL_INGOT,
                SlimefunItems.HEATING_COIL, SlimefunItems.ELECTRIC_SMELTERY, SlimefunItems.HEATING_COIL,
                SlimefunItems.HARDENED_METAL_INGOT, SlimefunItems.MEDIUM_CAPACITOR, SlimefunItems.HARDENED_METAL_INGOT
        });

        new BlockMenuPreset(getId(), "&6Electric Gold Refinery") {
            @Override
            public void init() {
                constructMenu(this);
            }

            @Override
            public void newInstance(@Nonnull BlockMenu menu, @Nonnull Block b) {
                for (int i = 0; i < 12; i++) {
                    final int recipeIndex = i;
                    menu.addMenuClickHandler(GOLD_INDEXES[recipeIndex], (p, slot, item, action) -> {
                        var container = StorageCacheUtils.getBlock(b.getLocation());
                        if (container == null) {
                            return false;
                        }

                        StorageCacheUtils.executeAfterLoad(container, () -> {
                            StorageCacheUtils.setData(b.getLocation(), "gold_recipe", Integer.toString(recipeIndex));
                            newInstance(menu, b);
                        }, true);
                        return false;
                    });
                }

                var container = StorageCacheUtils.getBlock(b.getLocation());
                if (container == null) {
                    showNoRecipe(menu, "&7Block data is not available yet");
                    return;
                }

                if (!container.isDataLoaded()) {
                    StorageCacheUtils.requestLoad(container);
                    showNoRecipe(menu, "&7Loading block data...");
                    return;
                }

                String recipe = StorageCacheUtils.getData(b.getLocation(), "gold_recipe");
                if (recipe == null || "11".equals(recipe)) {
                    showNoRecipe(menu, "&e> Choose on the left to change it");
                    return;
                }

                switch (recipe) {
                    case "0" -> showRecipe(menu, 4);
                    case "1" -> showRecipe(menu, 6);
                    case "2" -> showRecipe(menu, 8);
                    case "3" -> showRecipe(menu, 10);
                    case "4" -> showRecipe(menu, 12);
                    case "5" -> showRecipe(menu, 14);
                    case "6" -> showRecipe(menu, 16);
                    case "7" -> showRecipe(menu, 18);
                    case "8" -> showRecipe(menu, 20);
                    case "9" -> showRecipe(menu, 22);
                    case "10" -> showRecipe(menu, 24);
                    default -> showNoRecipe(menu, "&cStored recipe data was invalid; choose a recipe again");
                }
            }

            @Override
            public boolean canOpen(@Nonnull Block b, @Nonnull Player p) {
                return p.hasPermission("slimefun.inventory.bypass")
                        || Slimefun.getProtectionManager().hasPermission(
                                p,
                                b.getLocation(),
                                Interaction.INTERACT_BLOCK
                        );
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(@Nonnull ItemTransportFlow flow) {
                return new int[0];
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(
                    @Nonnull DirtyChestMenu menu,
                    @Nonnull ItemTransportFlow flow,
                    @Nonnull ItemStack item
            ) {
                if (flow == ItemTransportFlow.WITHDRAW) {
                    return getOutputSlots();
                }

                List<Integer> slots = new LinkedList<>();
                for (int slot : getInputSlots()) {
                    ItemStack stack = menu.getItemInSlot(slot);
                    if (stack != null) {
                        if (SlimefunUtils.isItemSimilar(stack, item, true, false)
                                && stack.getAmount() < stack.getMaxStackSize()) {
                            slots.add(slot);
                        }
                    } else {
                        slots.add(slot);
                    }
                }

                if (slots.isEmpty()) {
                    return getInputSlots();
                }

                int[] array = new int[slots.size()];
                for (int i = 0; i < slots.size(); i++) {
                    array[i] = slots.get(i);
                }
                return array;
            }
        };
    }

    private static void showNoRecipe(@Nonnull BlockMenu menu, @Nonnull String detail) {
        menu.replaceExistingItem(32, new CustomItemStack(
                Material.RED_STAINED_GLASS_PANE,
                "&6Current Recipe: &cNONE",
                "",
                detail
        ));
    }

    private static void showRecipe(@Nonnull BlockMenu menu, int carat) {
        menu.replaceExistingItem(32, new CustomItemStack(
                Material.GOLD_INGOT,
                "&6Current Recipe: &fGold Ingot &7(" + carat + " Carat)",
                "",
                "&e> Choose on the left to change it"
        ));
    }

    public int[] getInputSlots() {
        return new int[] { 21, 30 };
    }

    public int[] getOutputSlots() {
        return new int[] { 25, 34 };
    }

    @Nonnull
    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    public int getEnergyConsumption() {
        return ENERGY_CONSUMPTION;
    }

    public ItemStack getProgressBar() {
        return new ItemStack(Material.GOLDEN_CHESTPLATE);
    }

    public MachineRecipe getProcessing(Block b) {
        return processing.get(b);
    }

    public boolean isProcessing(Block b) {
        return getProcessing(b) != null;
    }

    private BlockBreakHandler onBreak() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(
                    @Nonnull BlockBreakEvent e,
                    @Nonnull ItemStack item,
                    @Nonnull List<ItemStack> drops
            ) {
                Block b = e.getBlock();
                BlockMenu inv = StorageCacheUtils.getMenu(b.getLocation());
                if (inv != null) {
                    inv.dropItems(b.getLocation(), getOutputSlots());
                    inv.dropItems(b.getLocation(), getInputSlots());
                }

                processing.remove(b);
                progress.remove(b);
            }
        };
    }

    @Override
    public void preRegister() {
        addItemHandler(new BlockTicker() {
            @Override
            public void tick(@Nonnull Block b, @Nonnull SlimefunItem sf, @Nonnull Config data) {
                ElectricGoldRefinery.this.tick(b);
            }

            @Override
            public boolean isSynchronized() {
                return false;
            }
        }, onBreak());
    }

    protected void tick(@Nonnull Block b) {
        BlockMenu inv = StorageCacheUtils.getMenu(b.getLocation());
        if (inv == null) {
            return;
        }

        if (isProcessing(b)) {
            int timeLeft = progress.getOrDefault(b, 0);
            if (timeLeft > 0) {
                ChestMenuUtils.updateProgressbar(inv, 23, timeLeft, processing.get(b).getTicks(), getProgressBar());

                if (isChargeable()) {
                    if (getCharge(b.getLocation()) < getEnergyConsumption()) {
                        return;
                    }
                    removeCharge(b.getLocation(), getEnergyConsumption());
                }

                progress.put(b, timeLeft - 1);
                return;
            }

            inv.replaceExistingItem(23, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "));
            MachineRecipe completedRecipe = processing.remove(b);
            progress.remove(b);
            if (completedRecipe != null) {
                for (ItemStack output : completedRecipe.getOutput()) {
                    inv.pushItem(output.clone(), getOutputSlots());
                }
            }
            return;
        }

        MachineRecipe next = findNextRecipe(inv, StorageCacheUtils.getData(b.getLocation(), "gold_recipe"));
        if (next != null) {
            processing.put(b, next);
            progress.put(b, next.getTicks());
        }
    }

    @Nullable
    protected MachineRecipe findNextRecipe(@Nonnull BlockMenu menu, @Nullable String recipeId) {
        Integer goldTier = parseGoldTier(recipeId);
        if (goldTier == null) {
            return null;
        }

        int[] inputSlots = getInputSlots();
        ItemStack goldDust1 = menu.getItemInSlot(inputSlots[0]);
        ItemStack goldDust2 = menu.getItemInSlot(inputSlots[1]);
        List<ItemStack> goldDustList = new ArrayList<>();

        if (goldDust1 != null && SlimefunUtils.isItemSimilar(goldDust1, SlimefunItems.GOLD_DUST, true, false)) {
            goldDustList.add(goldDust1);
        } else {
            goldDustList.add(new SlimefunItemStack(SlimefunItems.GOLD_DUST, 0));
        }

        if (goldDust2 != null && SlimefunUtils.isItemSimilar(goldDust2, SlimefunItems.GOLD_DUST, true, false)) {
            goldDustList.add(goldDust2);
        } else {
            goldDustList.add(new SlimefunItemStack(SlimefunItems.GOLD_DUST, 0));
        }

        int goldDusts = 0;
        for (ItemStack dust : goldDustList) {
            goldDusts += dust.getAmount();
        }

        int goldCost = goldTier + 1;
        if (goldDusts < goldCost) {
            return null;
        }

        ItemStack output = GOLDS[goldTier];
        if (!menu.fits(output, getOutputSlots())) {
            return null;
        }

        for (int i = 0; i < goldDustList.size(); i++) {
            ItemStack dust = goldDustList.get(i);
            if (dust.getAmount() == 0) {
                continue;
            }

            if (dust.getAmount() >= goldCost) {
                menu.consumeItem(inputSlots[i], goldCost);
                break;
            }

            int consumed = dust.getAmount();
            menu.consumeItem(inputSlots[i], consumed);
            goldCost -= consumed;
        }

        return new MachineRecipe(
                (int) (3 + 0.5 * goldTier),
                new ItemStack[] { new SlimefunItemStack(SlimefunItems.GOLD_DUST, goldTier) },
                new ItemStack[] { output }
        );
    }

    @Nullable
    private static Integer parseGoldTier(@Nullable String recipeId) {
        if (recipeId == null) {
            return null;
        }

        try {
            int tier = Integer.parseInt(recipeId);
            return tier >= 0 && tier < GOLDS.length ? tier : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    protected void constructMenu(@Nonnull BlockMenuPreset preset) {
        for (int i : BORDER) {
            preset.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        for (int i : BORDER_IN) {
            preset.addItem(
                    i,
                    new SlimefunItemStack("_UI_INPUT_SLOT", Material.CYAN_STAINED_GLASS_PANE, " "),
                    ChestMenuUtils.getEmptyClickHandler()
            );
        }

        for (int i : BORDER_OUT) {
            preset.addItem(
                    i,
                    new SlimefunItemStack("_UI_OUTPUT_SLOT", Material.ORANGE_STAINED_GLASS_PANE, " "),
                    ChestMenuUtils.getEmptyClickHandler()
            );
        }

        preset.addItem(23, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "), ChestMenuUtils.getEmptyClickHandler());
        preset.addItem(46, new CustomItemStack(Material.RED_STAINED_GLASS_PANE, "&cNONE"), ChestMenuUtils.getEmptyClickHandler());

        NamespacedKey key = new NamespacedKey(FoxyMachines.getInstance(), "nonstackable");
        for (int i = 0; i < GOLDS.length; i++) {
            ItemStack itemStack = GOLDS[i].clone();
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            itemStack.setItemMeta(itemMeta);
            preset.addItem(GOLD_INDEXES[i], itemStack, ChestMenuUtils.getEmptyClickHandler());
        }

        for (int i : getOutputSlots()) {
            preset.addMenuClickHandler(i, new AdvancedMenuClickHandler() {
                @Override
                public boolean onClick(Player p, int slot, ItemStack cursor, ClickAction action) {
                    return false;
                }

                @Override
                public boolean onClick(InventoryClickEvent e, Player p, int slot, ItemStack cursor, ClickAction action) {
                    return cursor == null || cursor.getType() == Material.AIR;
                }
            });
        }
    }
}
