package me.gallowsdove.foxymachines.implementation.weapons;

import io.github.thebusybiscuit.slimefun4.core.handlers.BowShootHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.weapons.SlimefunBow;
import me.gallowsdove.foxymachines.Items;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nonnull;

public class HealingBow extends SlimefunBow {

    private static final Effect BLOCK_BREAK_EFFECT = resolveEffect("DESTROY_BLOCK", "STEP_SOUND");

    private static Effect resolveEffect(String currentName, String legacyName) {
        try {
            return Effect.valueOf(currentName);
        } catch (IllegalArgumentException ignored) {
            return Effect.valueOf(legacyName);
        }
    }

    private static Object brainCoralEffectData() {
        return BLOCK_BREAK_EFFECT.name().equals("DESTROY_BLOCK")
                ? Material.BRAIN_CORAL.createBlockData()
                : Material.BRAIN_CORAL;
    }

    public HealingBow() {
        super(Items.WEAPONS_AND_ARMORS_ITEM_GROUP, Items.HEALING_BOW, new ItemStack[] {
                null, SlimefunItems.SYNTHETIC_DIAMOND, Items.REINFORCED_STRING,
                SlimefunItems.SYNTHETIC_DIAMOND, SlimefunItems.ESSENCE_OF_AFTERLIFE, Items.REINFORCED_STRING,
                null, SlimefunItems.SYNTHETIC_DIAMOND, Items.REINFORCED_STRING
        });
    }

    @Nonnull
    @Override
    public BowShootHandler onShoot() {
        return (e, n) -> {
            n.getWorld().playEffect(n.getLocation(), BLOCK_BREAK_EFFECT, brainCoralEffectData());
            n.getWorld().playEffect(n.getEyeLocation(), BLOCK_BREAK_EFFECT, brainCoralEffectData());
            e.getDamager().remove();
            e.setCancelled(true);
            n.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, (int)Math.floor(e.getDamage()/5)));
        };
    }
}
