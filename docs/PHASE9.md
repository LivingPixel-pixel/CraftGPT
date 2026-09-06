# Phase 9 — Exact and compressed area context

Phase 9 makes world visibility an explicit quality/cost decision instead of one fixed scan strategy.

## Context modes

### Compressed

The default mode sends:

- dimensions, volume and occupancy counts
- the bounded material-frequency palette
- one non-air count and dominant block per relative Y layer
- up to a 32 by 32 grid of relative top-surface samples

It is the recommended mode for empty terrain, ordinary outdoor builds and lower token use.

### Full

Full mode sends the same summaries plus an exact palette-compressed map of every loaded non-air block in the selected area. Each entry is `x,y,z,paletteIndex`, relative to the area's minimum corner. Every coordinate not listed inside the declared dimensions is explicitly `minecraft:air`.

The full snapshot includes canonical block-state properties but never block-entity NBT, inventories, entities, player data, absolute world coordinates or the world seed.

## Limits and failure behavior

- The exact local/network representation is capped at 1,000,000 characters.
- The user controls `maxFullContextBlocks`; the default is 8,192 and the hard maximum is 32,768.
- If exact encoding is unavailable or the non-air count exceeds the configured limit, no API request is made.
- CraftGPT never labels a truncated snapshot as Full context.
- Compressed mode remains available for the same selection.

The server still fingerprints every loaded block state. Exact prompt data is excluded from the stable project-context hash because the existing world-state fingerprint already commits to all coordinates and states; this preserves compatibility with projects created before Phase 9.

## Configuration

`config/craftgpt/client.properties` now includes:

```properties
contextMode=compressed
maxFullContextBlocks=8192
```

Both values are editable in the in-game settings screen. The selected mode is visible on the planning screen before generation.
