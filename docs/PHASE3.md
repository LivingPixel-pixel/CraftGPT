# Phase 3 — Intention planning and local versions

## Operational flow

1. `/craftgpt setArea start`
2. `/craftgpt setArea stop`
3. The server validates volume, axis length, chunk count and loaded chunks.
4. The server fingerprints every block state and extracts bounded material, layer and relative surface summaries plus a bounded palette-compressed exact non-air map. It never reads block-entity NBT, inventories, entities, player data or the world seed for model context.
5. The planning UI opens with a sanitized, relative context summary.
6. The player explicitly presses **Create plan** or **Create new version**.
7. The client calls the configured Responses-compatible HTTPS endpoint directly.
8. The result must match the strict plan schema and pass local bounds/budget checks.
9. Only a valid result is written as an immutable `vN.json` file.
10. A local chat message announces that generation is complete and names the saved version.

The planning field inside the screen is the recommended private path. The optional `/craftgpt prompt ...` and `/craftgpt discuss ...` shortcuts send their text through the Minecraft server only to prefill that screen; they never trigger the API request themselves.

## API request

The planning request uses:

- configured planning model
- configured reasoning level
- `store: false`
- a strict `text.format` JSON schema
- no tools and no server-side API proxy

The selected Phase 9 context mode controls the prompt body. `compressed` sends the summaries; `full` additionally sends every exact non-air block state and relative coordinate when the configured limit can be honored without truncation.

The system prompt prohibits commands, block operations, absolute coordinates and hidden reasoning. The API key is used only in the local Authorization header and is never included in networking payloads or project files.

The key is kept separately in `config/craftgpt/client-secrets.properties`; non-secret options live in `config/craftgpt/client.properties`.

## Version semantics

- Initial successful plan: `v1`
- Successful iteration: `v2`, `v3`, ...
- Failed or invalid API response: no version
- `revert v3`: append a new version containing the full intention from `v3`
- Existing version files are never edited or deleted
- Each version stores and verifies a SHA-256 content hash
- A server-requested `revert` only preselects a version; the player confirms it locally

Each version contains a complete intention and a self-contained `implementationBrief`. Phase 4 consumes that brief when compiling the plan into block operations.

## Local files

```text
config/craftgpt/projects/
  <hashed-world-or-server-scope>/
    index.json
    <project-uuid>/
      project.json
      versions/
        v1.json
        v2.json
```

Raw world paths and server addresses are not stored in the scope directory name. `project.json` is an atomic mutable head. Version files are written to a staged, synchronized temporary file and atomically committed with create-only semantics. A valid contiguous version left behind by a crash is recovered on the next load.
