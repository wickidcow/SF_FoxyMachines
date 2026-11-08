# FoxyMachines Chunk Loader / Slimefun Legacy policy

The FoxyMachines Chunk Loader reads the same setting as the Slimefun Legacy Resonance Beacon:

`plugins/Slimefun/configSFLAddons.yml`

```yaml
SlimefunLegacyAddition:
  PoweredBeacon:
    chunk-loading-mode: ONLINE
```

| Mode | FoxyMachines behavior |
| --- | --- |
| ONLINE (default) | Foxy-owned plugin chunk tickets exist only while a player is connected to the server |
| ALWAYS | Foxy-owned plugin chunk tickets remain while the server runs without players |
| OFF | New Chunk Loader placement is rejected, no Foxy-owned tickets are held and the Chunk Loader is hidden from the Slimefun guide |

The setting is edited only in Slimefun Legacy. The FoxyMachines companion addon checks for changes within two seconds; `/beacon mode online|always|off` changes the same file.

**Preservation:** The existing FoxyMachines `CHUNK_LOADER` item/recipe/research identifier and placed-world block data remain registered. OFF hides the guide entry; it does not delete crafted or placed items, reset GPS requirements, owner PDC, or storage. The plugin now uses reversible, per-plugin Paper chunk tickets for **new placements**, never globally toggling vanilla force-loaded flags for those blocks. When an owner is offline when a loader is broken, its quota decrement is queued and applied to the player PDC at their next login.

## Historical forced chunks (important)

Older FoxyMachines used Minecraft's persistent `Chunk#setForceLoaded(true)` rather than plugin tickets. Vanilla does **not** record which plugin or administrator owns a force-loaded flag, so blindly clearing those flags could break other systems.

1. Back up world files and the FoxyMachines data directory.
2. Start the updated companion addon once. It reports the number of **verified** old `CHUNK_LOADER` beacons in existing force-loaded chunks but does not modify their flags.
3. Audit possible external administrative/third-party users of the same chunks. If the historical flags belong to FoxyMachines, set `migrate-legacy-force-loaded-chunks: true` in `plugins/FoxyMachines/config.yml` and restart.
4. Only beacons with an exact Slimefun `CHUNK_LOADER` block ID and a valid owner UUID are imported into `chunk-loader-positions.properties` and have their old force-loaded flag cleared. All other chunks remain untouched. Then return the migration option to `false`.
5. Check `/forceload query`, restart, verify zero-player ONLINE and OFF behavior, and confirm previously placed loaders resume when players reconnect.

Without that optional, verified migration, old persistent vanilla force-load flags may keep chunks loaded even when the new shared policy is ONLINE with zero players or OFF. The plugin intentionally does not silently clear flags of unknown ownership. Never delete the recorded-position or offline-decrement property files to try to repair ownership.

## Validation gates

New placement, two-loader overlap, owner quota, online/offline ticket release/recovery, OFF hiding, block removal by unrelated tools, no forced flags on new placements, Paper 1.21.11/26.2/26.3 native restart, and the historical one-time migration are required before treating this as release-ready. All published FoxyMachines and Slimefun Legacy tags remain immutable.
