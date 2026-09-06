# Phase 11: Portable subscription workflow

Phase 11 adds a complete file-based generation path beside the direct Responses API path. Minecraft never needs an API key for this path.

## Product intent

The player can use CraftGPT with an existing ChatGPT or Codex allowance instead of paying separately for API tokens. This does not mean unlimited free generation. Codex usage still follows the limits of the selected ChatGPT plan, including the available Free plan. The important boundary is that the mod makes no metered API call and stores no API key when the file workflow is used.

Official background:

- [Codex pricing and plan inclusion](https://learn.chatgpt.com/docs/pricing)
- [Building reusable ChatGPT and Codex skills](https://learn.chatgpt.com/docs/build-skills)

## Player flow

1. Mark the area with the CraftBook or `/craftgpt setArea start` and `/craftgpt setArea stop`.
2. Enter the build idea in the planning screen.
3. Choose **Export for Codex** instead of **Create with API**.
4. CraftGPT opens a local request folder containing:
   - `request.craftgpt.json` with the prompt and exact relative area context
   - `PROMPT.md` with short handoff instructions
   - `preview.html` with the local visual viewer
5. Attach `request.craftgpt.json` to a ChatGPT or Codex task and invoke `$craftgpt-building`.
6. Save the returned file as `result.craftgpt.json` in the same folder.
7. Open `preview.html`, load request and result, rotate or zoom the isometric view, and compare existing, generated, and final states.
8. Return to Minecraft and choose **Import Codex result**.
9. CraftGPT validates the result locally, creates a versioned plan, asks the server to validate current world state and every block operation, then shows the normal ghost preview.
10. The existing explicit accept, place, undo, redo, history, and recovery flow remains unchanged.

Power-user commands:

```text
/craftgpt export <idea>
/craftgpt exchange
/craftgpt import
```

## Portable request contract

The request contains:

- a random request ID;
- context and instruction hashes;
- the original user instruction;
- bounded dimensions and operation limit;
- material, layer, and surface summaries;
- the full palette-compressed relative block map;
- the previous structured plan when the request is an iteration.

It does not contain:

- API keys;
- absolute world coordinates;
- world seed;
- player identity or inventory;
- entities;
- block-entity NBT or inventories;
- a direct world-editing capability.

## Import safety

The importer accepts only results whose request ID, context hash, and instruction hash match the corresponding exported request. An iteration must also match the exact active plan content hash.

The imported plan passes the existing structured plan validator. The build passes the existing canonical block-state, palette, bounds, duplicate-coordinate, operation-count, and dangerous-state checks. The server then rescans the selected area and rejects stale context, unsafe existing blocks, unloaded chunks, invalid states, or operations outside the current selection.

The browser preview is a self-contained local HTML file. It has no external scripts, tracking, network requests, or write access. The player still reviews the authoritative in-game ghost preview before placement.

## Building Skill

The distributable release includes `craftgpt-building-skill/` with:

- a focused `SKILL.md` workflow;
- the exact request and result format reference;
- an offline result validator script.

The skill performs a design pass, a block compilation pass, and a final quality and safety audit. It returns a compact result file, not Minecraft commands and not direct world edits.

## Automated coverage

Phase 11 adds tests for:

- removal of absolute coordinates from exported packages;
- bundled prompt and local viewer generation;
- strict request/result binding;
- portable build compilation without API settings;
- preservation of the existing client and server validation path.
