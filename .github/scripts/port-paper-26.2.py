#!/usr/bin/env python3
"""Idempotent source migration for FoxyMachines on Paper 26.2+.

This intentionally only rewrites Bukkit/Paper API names that were removed or
renamed in modern 1.21.x / 26.2 APIs. It does not alter Slimefun item IDs,
recipes, storage data, or gameplay balance.
"""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "src" / "main" / "java"

# Repairs for an early migration pass that used prefix replacement. Keeping
# these here makes the script safe and idempotent for any branch based on it.
TOKEN_REPAIRS = {
    "PotionEffectType.INSTANT_HEALTHTH_BOOST": "PotionEffectType.HEALTH_BOOST",
    "PotionEffectType.SLOWNESS_FALLING": "PotionEffectType.SLOW_FALLING",
    "PotionEffectType.SLOWNESSNESS": "PotionEffectType.SLOWNESS",
    "PotionEffectType.SLOWNESS_DIGGING": "PotionEffectType.MINING_FATIGUE",
    "PotionEffectType.JUMP_BOOST_BOOST": "PotionEffectType.JUMP_BOOST",
    "Enchantment.LUCK_OF_THE_SEA_OF_THE_SEA": "Enchantment.LUCK_OF_THE_SEA",
}

REPLACEMENTS = {
    # Enchantments: legacy Bukkit aliases -> modern registry names.
    "Enchantment.PROTECTION_ENVIRONMENTAL": "Enchantment.PROTECTION",
    "Enchantment.PROTECTION_FIRE": "Enchantment.FIRE_PROTECTION",
    "Enchantment.PROTECTION_FALL": "Enchantment.FEATHER_FALLING",
    "Enchantment.PROTECTION_EXPLOSIONS": "Enchantment.BLAST_PROTECTION",
    "Enchantment.PROTECTION_PROJECTILE": "Enchantment.PROJECTILE_PROTECTION",
    "Enchantment.OXYGEN": "Enchantment.RESPIRATION",
    "Enchantment.WATER_WORKER": "Enchantment.AQUA_AFFINITY",
    "Enchantment.DAMAGE_ALL": "Enchantment.SHARPNESS",
    "Enchantment.DAMAGE_UNDEAD": "Enchantment.SMITE",
    "Enchantment.DAMAGE_ARTHROPODS": "Enchantment.BANE_OF_ARTHROPODS",
    "Enchantment.LOOT_BONUS_MOBS": "Enchantment.LOOTING",
    "Enchantment.DIG_SPEED": "Enchantment.EFFICIENCY",
    "Enchantment.DURABILITY": "Enchantment.UNBREAKING",
    "Enchantment.LOOT_BONUS_BLOCKS": "Enchantment.FORTUNE",
    "Enchantment.ARROW_DAMAGE": "Enchantment.POWER",
    "Enchantment.ARROW_KNOCKBACK": "Enchantment.PUNCH",
    "Enchantment.ARROW_FIRE": "Enchantment.FLAME",
    "Enchantment.ARROW_INFINITE": "Enchantment.INFINITY",
    "Enchantment.LUCK": "Enchantment.LUCK_OF_THE_SEA",

    # Potion effect aliases removed from modern Bukkit.
    "PotionEffectType.SLOW": "PotionEffectType.SLOWNESS",
    "PotionEffectType.FAST_DIGGING": "PotionEffectType.HASTE",
    "PotionEffectType.SLOW_DIGGING": "PotionEffectType.MINING_FATIGUE",
    "PotionEffectType.INCREASE_DAMAGE": "PotionEffectType.STRENGTH",
    "PotionEffectType.HEAL": "PotionEffectType.INSTANT_HEALTH",
    "PotionEffectType.HARM": "PotionEffectType.INSTANT_DAMAGE",
    "PotionEffectType.JUMP": "PotionEffectType.JUMP_BOOST",
    "PotionEffectType.CONFUSION": "PotionEffectType.NAUSEA",
    "PotionEffectType.DAMAGE_RESISTANCE": "PotionEffectType.RESISTANCE",

    # Attribute aliases removed from modern Bukkit.
    "Attribute.GENERIC_MAX_HEALTH": "Attribute.MAX_HEALTH",
    "Attribute.GENERIC_FOLLOW_RANGE": "Attribute.FOLLOW_RANGE",
    "Attribute.GENERIC_KNOCKBACK_RESISTANCE": "Attribute.KNOCKBACK_RESISTANCE",
    "Attribute.GENERIC_MOVEMENT_SPEED": "Attribute.MOVEMENT_SPEED",
    "Attribute.GENERIC_FLYING_SPEED": "Attribute.FLYING_SPEED",
    "Attribute.GENERIC_ATTACK_DAMAGE": "Attribute.ATTACK_DAMAGE",
    "Attribute.GENERIC_ATTACK_KNOCKBACK": "Attribute.ATTACK_KNOCKBACK",
    "Attribute.GENERIC_ATTACK_SPEED": "Attribute.ATTACK_SPEED",
    "Attribute.GENERIC_ARMOR": "Attribute.ARMOR",
    "Attribute.GENERIC_ARMOR_TOUGHNESS": "Attribute.ARMOR_TOUGHNESS",
    "Attribute.GENERIC_LUCK": "Attribute.LUCK",
    "Attribute.HORSE_JUMP_STRENGTH": "Attribute.JUMP_STRENGTH",
    "Attribute.ZOMBIE_SPAWN_REINFORCEMENTS": "Attribute.SPAWN_REINFORCEMENTS",

    # Particle enum rename.
    "Particle.VILLAGER_HAPPY": "Particle.HAPPY_VILLAGER",
    "Particle.VILLAGER_ANGRY": "Particle.ANGRY_VILLAGER",
}


def replace_exact_java_token(text: str, old: str, new: str) -> str:
    # Do not replace prefixes of longer enum constants such as HEAL in
    # HEALTH_BOOST, SLOW in SLOW_FALLING, or LUCK in LUCK_OF_THE_SEA.
    return re.sub(re.escape(old) + r"(?![A-Za-z0-9_])", new, text)


def modernize_potion_mixer(text: str) -> str:
    """Replace deprecated PotionData reconstruction with PotionType effects."""
    text = text.replace("import org.bukkit.potion.PotionData;\n", "")

    method_pattern = re.compile(
        r"    @Nonnull\n"
        r"    protected PotionEffect\[\] getCustomEffectsFromBaseData\(PotionData potionData, boolean lingering\) \{.*?"
        r"\n    \}\n\n"
        r"    @Nullable\n"
        r"    protected MachineRecipe findNextRecipe",
        re.DOTALL,
    )

    replacement = '''    @Nonnull
    protected PotionEffect[] getCustomEffectsFromBaseType(@Nullable PotionType type, boolean lingering) {
        if (type == null) {
            return new PotionEffect[0];
        }

        int durationDivisor = lingering ? 4 : 1;
        return type.getPotionEffects().stream()
                .map(effect -> durationDivisor == 1
                        ? effect
                        : effect.withDuration(Math.max(1, effect.getDuration() / durationDivisor)))
                .toArray(PotionEffect[]::new);
    }

    @Nullable
    protected MachineRecipe findNextRecipe'''

    text, count = method_pattern.subn(replacement, text)
    if count > 1:
        raise RuntimeError("PotionMixer migration matched more than one helper method")

    text = text.replace(
        "getCustomEffectsFromBaseData(potionMeta.getBasePotionData(), lingering)",
        "getCustomEffectsFromBaseType(potionMeta.getBasePotionType(), lingering)",
    )
    text = text.replace(
        "getCustomEffectsFromBaseData(potion2Meta.getBasePotionData(), lingering)",
        "getCustomEffectsFromBaseType(potion2Meta.getBasePotionType(), lingering)",
    )
    text = text.replace(
        "potionMeta.setBasePotionData(new PotionData(PotionType.UNCRAFTABLE, false, false));",
        "potionMeta.setBasePotionType(PotionType.WATER);",
    )
    return text


def main() -> None:
    changed = []
    for path in sorted(JAVA_ROOT.rglob("*.java")):
        original = path.read_text(encoding="utf-8")
        updated = original

        for bad, good in TOKEN_REPAIRS.items():
            updated = updated.replace(bad, good)

        for old, new in REPLACEMENTS.items():
            updated = replace_exact_java_token(updated, old, new)

        if path.name == "PotionMixer.java":
            updated = modernize_potion_mixer(updated)

        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed.append(path.relative_to(ROOT).as_posix())

    if changed:
        print("Paper 26.2 source migration updated:")
        for path in changed:
            print(f"  - {path}")
    else:
        print("Paper 26.2 source migration: no changes needed")


if __name__ == "__main__":
    main()
