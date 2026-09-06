# Phase 5 — Guarded placement and recovery

## Player flow

1. Select an area, create or activate a plan, generate a preview and accept it.
2. Open the review screen and press **Place build**. The button is unavailable until the current preview is explicitly accepted.
3. The server independently verifies permissions, selection identity, current area hash, bounds, loaded chunks, block states and hard limits.
4. Before changing the world, the server atomically stores an immutable before-snapshot and a mutable progress record in the world save.
5. Blocks are placed in bounded batches. The UI shows processed and total operations and reports completion as **Generation over**.
6. **Undo latest** walks the recorded changes backwards. It restores a block only if its current state still equals the state CraftGPT placed. Later player or mod changes remain untouched and are reported as conflicts.
7. The player may select another area and start a new generation. Placement state is associated with the build ID, so an older placed build does not block a new accepted preview.

The `/craftgpt place` and `/craftgpt undo` commands are navigation shortcuts. They open the local review screen; they do not start placement, undo or an API request without a local button press.

## Authority and safety rules

- Integrated singleplayer owners may place. On multiplayer, the player needs gamemaster/operator command permission.
- Placement requests carry only the random build ID and geometry needed for revalidation. Plans, prompts, models, reasoning settings, summaries and API keys stay client-local.
- A request is rejected if the selected area changed, a target chunk is unloaded, a coordinate is out of bounds or any operation is invalid.
- Fluids, fire, explosives, administrative blocks, portals, falling/physics blocks and block entities are refused.
- Immediate neighbor, on-place and block-entity side effects are suppressed during journaled writes so mutation stays within the recorded coordinates. Redstone or physics may react later when normal world updates reach the build.
- Placement and undo use a maximum of 128 operations per job per server tick and 512 operations globally per tick.
- Mutation requests are rate-limited. An emergency undo of an actively running placement is still allowed.
- Only one mutation job per player and one active owner per journal are allowed at a time.

## World-local recovery journal

The server stores recovery data inside the affected world save:

```text
<world>/craftgpt/placements/<player-uuid>/
  <placement-uuid>.snapshot.json
  <placement-uuid>.state.json
  head.json
```

The snapshot contains the exact before and after block states for each changed coordinate. It is content-hashed, immutable and written before the first mutation. State and head files are replaced atomically; progress is flushed after every batch. A busy job left by shutdown is marked `interrupted` and remains undoable after reconnect.

Version `0.5.0` exposed only the latest placement in the UI. Phase 6 adds a bounded history browser and targeted recovery while retaining the same immutable files.

## Conflict policy

Placement and undo are idempotent and compare the current world state with the journal:

- expected source state: apply the requested target state;
- already at target state: count as completed without rewriting;
- any other state: preserve it and count a conflict.

This policy avoids overwriting edits made after preview validation or after placement. A fully undone placement closes the recovery action; a conflicted undo stays available so the player can resolve conflicts and retry.

## Current limitations

- Version `0.5.0` refuses all block entities. Phase 6 keeps that default and adds a bounded opt-in for replacing existing block entities after snapshotting them; it still never generates new block entities or accepts model NBT.
- No gravity-affected blocks such as sand, gravel, anvils or concrete powder.
- Version `0.5.0` targets the latest placement only; Phase 6 adds history and redo.
- Automated tests cover protocol, storage, lifecycle and conflict rules. A live-world smoke test is still recommended before using a release on an important world backup.
