# Phase 4 — Build compilation and ghost preview

## Intended flow

1. Select an area and create or activate a saved intention plan.
2. Press **Generate preview** locally; server payloads can never start a paid API request.
3. The configured builder model receives the full active intention plus bounded relative world summaries.
4. It returns a strict compact palette and ordered relative block operations, not executable commands or absolute coordinates.
5. The client validates schema, limits, coordinates, duplicates and palette references.
6. A minimized geometry-only payload is sent to the Minecraft server. The server independently rechecks the current selection and world hash, parses every block state and rejects dangerous or stale output.
7. Only an accepted artifact is saved and rendered as a non-mutating ghost preview.
8. The player may accept it for placement, return to planning, regenerate it or abandon it. Hidden or wrong-dimension previews cannot be accepted; incomplete visual coverage requires an explicit second confirmation.

## Safety boundary

The model never receives permission to execute commands. CraftGPT deterministically translates each validated relative operation to a `/setblock` command representation for later placement. Phase 4 does not mutate the world.

Rejected blocks include fluids, fire, explosives, command/structure blocks, portal internals and other administrative or ephemeral states. Every operation must be unique, within the selected area and below both the local budget and the hard server cap.

The server receives only protocol version, selection ID, context hash, palette and relative operations. Project IDs, plan/version metadata, model and reasoning settings, timestamps and summaries remain client-local. Validation requests are rate-limited per connected player. The ghost renderer uses section culling, a 4,096-operation frame cap and allocation-light direct edge emission.

## API and cost controls

- separate builder model and builder reasoning level
- default builder model `gpt-5.4-nano`
- default builder reasoning `low`
- `store: false`
- strict `text.format` JSON schema
- compact `x,y,z,paletteIndex` operation strings
- bounded response streaming and no tools

The API key remains only in `config/craftgpt/client-secrets.properties`.

## Local files

Validated artifacts are stored below the world-scoped project:

```text
config/craftgpt/projects/<world-scope>/<project-id>/builds/
  <build-id>.json
  build-head.json
```

Build files are content-hashed and immutable. The mutable head records which artifact is active and whether the player accepted it for Phase 5.

## Phase boundary in version 0.4.0

Accepting a preview only recorded local consent in version `0.4.0`; that release had no block-placement or command-execution path. Version `0.5.0` implements the guarded Phase 5 placement and recovery path described in `PHASE5.md`.
