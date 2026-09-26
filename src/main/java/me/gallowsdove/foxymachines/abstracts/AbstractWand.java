package me.gallowsdove.foxymachines.abstracts;

import io.github.mooy1.infinitylib.core.AddonConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.attributes.Rechargeable;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import me.gallowsdove.foxymachines.FoxyMachines;
import me.gallowsdove.foxymachines.Items;
import me.gallowsdove.foxymachines.utils.AuraSkillsCompat;
import me.gallowsdove.foxymachines.utils.SimpleLocation;
import me.gallowsdove.foxymachines.utils.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public abstract class AbstractWand extends SlimefunItem implements NotPlaceable, Rechargeable {
    private static final NamespacedKey MATERIAL_KEY = new NamespacedKey(FoxyMachines.getInstance(), "wand_material");

    protected static final Set<Material> WHITELIST = new HashSet<>();

    protected static final Set<Material> BLACKLIST = new HashSet<>();

    public static void init() {
        if (!WHITELIST.isEmpty() || !BLACKLIST.isEmpty()) {
            FoxyMachines.log(Level.WARNING, "Attempted to initialize AbstractWand after already initialized!");
            return;
        }

        AddonConfig config = FoxyMachines.getInstance().getConfig();
        loadList("fill-wand-white-list", WHITELIST, config.getStringList("fill-wand-white-list"));
        loadList("fill-wand-black-list", BLACKLIST, config.getStringList("fill-wand-black-list"));
    }

    public static void loadList(String name, Set<Material> materials, List<String> values) {
        for (String value : values) {
            try {
                Material material = Material.valueOf(value);
                materials.add(material);
                continue;
            } catch (IllegalArgumentException ignored) {}

            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(value.toLowerCase()), Material.class);
            SlimefunTag slimefunTag = SlimefunTag.getTag(value);
            if (tag == null && slimefunTag == null) {
                FoxyMachines.log(Level.WARNING, "Invalid Entry in \"" + name + "\": " + value);
                continue;
            }

            materials.addAll(tag != null ? tag.getValues() : slimefunTag.getValues());
        }
    }

    protected AbstractWand(SlimefunItemStack item, RecipeType recipeType, ItemStack [] recipe) {
        super(Items.TOOLS_ITEM_GROUP, item, recipeType, recipe);
    }

    @Override
    public void preRegister() {
        addItemHandler(onUse());
    }

    @Nonnull
    protected ItemUseHandler onUse() {
        return e -> {
            Player player = e.getPlayer();
            ItemStack itemInInventory = e.getItem();
            if (itemInInventory == null) return;
            ItemMeta meta = itemInInventory.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();

            if (player.isSneaking() && !isRemoving() && e.getClickedBlock().isPresent()) {
                Material material = e.getClickedBlock().get().getType();
                String humanizedName = ChatUtils.humanize(material.toString());
                if ((material.isBlock() && material.isSolid() && material.isOccluding() && !BLACKLIST.contains(material)) ||
                        WHITELIST.contains(material)) {
                    player.sendMessage(Component.text("Material set to: " + humanizedName, NamedTextColor.LIGHT_PURPLE));
                    container.set(AbstractWand.MATERIAL_KEY, PersistentDataType.STRING, material.toString());
                    List<Component> baseLore = this.getItem().getItemMeta().lore();
                    if (baseLore != null && baseLore.size() >= 2) {
                        List<Component> lore = new ArrayList<>(baseLore);
                        lore.set(
                            lore.size() - 2,
                            Component.text("Material: ", NamedTextColor.GRAY)
                                .append(Component.text(humanizedName, NamedTextColor.YELLOW))
                        );
                        meta.lore(lore);
                    }
                    itemInInventory.setItemMeta(meta);
                    setItemCharge(itemInInventory, getItemCharge(itemInInventory)); // To update it in lore
                } else {
                    player.sendMessage(Component.text("Cannot use: " + humanizedName + ", with the fill wand", NamedTextColor.RED));
                }
            } else {
                if (isRemoving() && !container.has(MATERIAL_KEY, PersistentDataType.STRING)) {
                    container.set(MATERIAL_KEY, PersistentDataType.STRING, Material.AIR.toString());
                }

                List<Location> locs = getLocations(player);

                if (locs.isEmpty()) {
                    return;
                }

                Inventory inventory = player.getInventory();
                if (!container.has(MATERIAL_KEY, PersistentDataType.STRING)) {
                    player.sendMessage(Component.text("Select a building material with Shift + Right Click!", NamedTextColor.RED));
                    return;
                }
                Material material = Material.getMaterial(container.get(MATERIAL_KEY, PersistentDataType.STRING));

                ItemStack blocks = new ItemStack(material, locs.size());

                if (isRemoving() || inventory.containsAtLeast(blocks, locs.size())) {
                    if (removeItemCharge(e.getItem(), getCostPerBlock() * locs.size())) {
                        inventory.removeItem(blocks);
                        for (Location loc : locs) {
                            Bukkit.getScheduler().runTask(FoxyMachines.getInstance(), () -> {
                                loc.getBlock().setType(material);
                                if (Utils.isAuraSkillsLoaded()) {
                                    AuraSkillsCompat.addPlacedBlock(loc.getBlock());
                                }
                            });
                        }
                    } else {
                        player.sendMessage(Component.text("Your item doesn't have enough energy for that!", NamedTextColor.RED));
                        player.sendMessage(Component.text("Energy needed: " + getCostPerBlock() * locs.size(), NamedTextColor.RED));
                    }
                } else {
                    player.sendMessage(Component.text("There aren't enough materials in your inventory!", NamedTextColor.RED));
                    player.sendMessage(Component.text("Current items: " + Utils.countItemInInventory(inventory, blocks) + " Needed: " + locs.size(), NamedTextColor.RED));
                }
            }
        };
    }

    protected List<Location> getLocations(@Nonnull Player player) {
        List<Location> locs = new ArrayList<>();
        PersistentDataContainer container = player.getPersistentDataContainer();
        SimpleLocation loc1 = SimpleLocation.fromPersistentStorage(container, "primary_position");
        SimpleLocation loc2 = SimpleLocation.fromPersistentStorage(container, "secondary_position");

        if (loc1 == null || loc2 == null || !loc1.getWorldUUID().equals(loc2.getWorldUUID())) {
            player.sendMessage(Component.text("Please select both locations using Position Selector!", NamedTextColor.RED));
            return locs;
        }

        if (loc1.getX() < loc2.getX()) {
            int tmp = loc1.getX();
            loc1.setX(loc2.getX());
            loc2.setX(tmp);
        }

        if (loc1.getY() < loc2.getY()) {
            int tmp = loc1.getY();
            loc1.setY(loc2.getY());
            loc2.setY(tmp);
        }

        if (loc1.getZ() < loc2.getZ()) {
            int tmp = loc1.getZ();
            loc1.setZ(loc2.getZ());
            loc2.setZ(tmp);
        }

        if ((loc1.getX() - loc2.getX()) * (loc1.getY() - loc2.getY()) * (loc1.getZ() - loc2.getZ()) > getMaxBlocks()) {
            player.sendMessage(Component.text("Selected area is too big!", NamedTextColor.RED));
            return locs;
        }

        World world = Bukkit.getWorld(UUID.fromString(loc1.getWorldUUID()));

        if (world == null) {
            player.sendMessage(Component.text("Please select both locations using Position Selector!", NamedTextColor.RED));
            return locs;
        }

        for (int x = loc2.getX(); x <= loc1.getX(); x++) {
            for (int y = loc2.getY(); y <= loc1.getY(); y++) {
                for (int z = loc2.getZ(); z <= loc1.getZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (blockPredicate(player, block)) {
                        locs.add(block.getLocation());
                    }
                }
            }
        }

        if (locs.isEmpty()) {
            player.sendMessage(Component.text("No valid locations found given the selected points!", NamedTextColor.RED));
        }

        return locs;
    }

    protected abstract int getMaxBlocks();

    protected abstract boolean isRemoving();

    protected abstract float getCostPerBlock();

    protected abstract boolean blockPredicate(Player player, Block block);
}
