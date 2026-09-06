# Implementation and limits

## Material ownership

The model supplies every palette entry and each component's paletteIndex. There is no local default floor, wall or roof palette and no random material substitution.

A fill represents a model-selected region, not a fixed house template. Two rooms can use different fills; borders and inlays can use smaller fills or explicit block overrides. Explicit operations win over components at the same coordinate. A door component uses the selected door material. A gable preserves the selected block type while orienting stairs for its slope.

The registry normalizer supplies omitted block properties and deduplicates equivalent states. It remaps operation indices together with the palette. It rejects unknown materials instead of replacing them with stone. Review patches merge by coordinate and resolved state, so palette index 0 in a patch does not overwrite every occurrence of index 0 in the old draft.

## Build and review contract

Initial generation returns a complete placeable draft. A plan alone is not sufficient. Components are expanded before bounds, duplicate, safety and operation-budget validation.

Effort 1 requests one initial creative turn. Effort N allows the initial turn and up to N-1 review rounds. The round allowance is captured when generation launches. A keep response can stop early. Validation repairs are independently bounded. An entire job allows at most two extra inspection requests; each can request one to four cropped views.

Every visual review receives the current build and its hash. It must return one of:

- keep: retain the current draft, with no replacement plan or build.
- repair: return a complete patch of coordinate replacements against that exact base. Air explicitly removes a prior block.
- inspect: request bounded relative crop boxes and one of four isometric orientations. No world commands are accepted.

Findings contain severity, relative location, problem, evidence and suggestion. The model is instructed not to invent defects to spend the effort budget. This is an instruction, not a guarantee of aesthetic improvement.

Imported candidates pass the existing independent server validator. Draft readiness does not mean public-release readiness or a measured visual quality score.

## Targeted changes

Aim at the draft, open the CraftBook and use Review > More options > Improve this part. Choose an edit radius from zero to five and enter the change. The displayed coordinates are relative to the selected area.

The intersection test selects an operation along the player's gaze, up to 128 blocks away. This first version selects an axis-aligned box around that cell, not a semantic object such as the whole roof. The importer rejects any expanded patch operation outside the approved box. All other coordinates and their materials remain unchanged.

The action requires the saved Codex session associated with the current workflow. It does not create an unrelated task or use a second API service.

## Visual evidence

Production capture reads Minecraft's baked block-state model geometry and sprite UV coordinates. Texture data comes from the active resource stack. CPU rendering and PNG writing run on a worker thread.

The default evidence is twelve views on three sheets: four exteriors, four middle cutaways and four structural sections. Additional camera requests are cropped, rotated isometric sections. They are not arbitrary perspective cameras inside the world.

The local Inspect solid model viewer does not call a model or place blocks. The in-world solid preview remains depth-tested against the existing world; removal regions may still be visually occluded by original terrain. Switch to the standalone inspection for an unobstructed view.

Rendering limitations:

- Neutral lighting and default tint rather than exact biome lighting, shaders, weather or dynamic shadows.
- First texture frame only; resource-pack animation metadata is not fully interpreted.
- No entity or special block-entity renderer.
- Missing geometry or textures use a labeled magenta placeholder, not a plausible replacement material.
- A bounded geometry budget can reject extremely complex models.
- Arbitrary modded resource packs and actual in-game rendering still need live verification.

The optional browser preview remains explicitly approximate, using material-family colors and cubes. It now expands component occupancy and combines review patches with their base. It is not used as the authoritative textured evidence or safety validator.

## Recovery and history

Candidate validation keeps a checkpoint of the current source and draft. Failure, cancellation or timeout restores it. Failed candidates remain in immutable audit history. Parent validation supports retry branches after recovery, and version numbers are not reused.

The existing plan and placement history remain available. A thumbnail-based generation gallery, visual version comparison and one-click restoration of any old build have not been implemented in this release.

The Codex launcher now releases run ownership before completing a future, so a completion callback can start the next round safely. Cleanup from an older run cannot clear a newer run's ownership.

## Validation coverage

Existing structural, hash, bounds, duplicate-position, block-state, permission, stale-context and safety checks remain. New final-overlay checks reject mismatched or missing door halves and doors without sturdy support, including removal of existing support.

This does not prove that every house is comfortable, fully roofed or navigable. General interior pathfinding, stair collision navigation, roof leakage analysis and a comprehensive functional scoring system are not implemented. Those remain visual-review and live-test concerns. Sculpture tasks are not forced to have house features.

Diagnostics can list independently detectable errors together. Dependent checks still require well-formed earlier data. Repair is bounded, not an infinite promise to fix all possible problems.

## Verification boundary

Automated checks run without launching Minecraft or making model requests. They cover compilation, unit tests, component geometry, material preservation, patch merging, scope restrictions, reviewer limits, history reload, door checks, registry normalization and synthetic rasterization.

The real GUI, graphics pipeline, Codex handoff, live generation quality and multiplayer placement have not been end-to-end tested here. No claim of lower model error rate or better output quality is made from these unit tests alone.
