# 0.19.0-alpha.2: manual iteration import fix

## Cause and fix

The reported generation completed successfully. CraftGPT rejected its valid repair patch as
`unexpected_review_result` because manual "Request changes" runs had a visual-review counter of zero.
That counter is no longer an import permission check.

Both manual and automatic repair imports now resolve against the immutable active build.
The request base hash and review base hash must both match that exact artifact. The merge uses
live block states, not the exported base copy. Changed coordinates use the patch palette;
untouched coordinates retain their original materials. Local code does not choose a new palette.

Keep replies are accepted as no-ops without adding a plan version or another server validation.
Camera requests outside visual review receive a specific corrective error and can use the
existing bounded automatic retry path. Stale patches are rejected, never silently rebased.

The bundled prompt explicitly explains initial generation, manual patches, keep and visual inspection.
Import errors now show the actual validation code, cause and corrective action, with detailed
diagnostics retained in the existing local log. Independent plan, bounds, operation-budget,
block-state, scope, world-state and server-permission checks remain enabled.

## Install and recover the saved result

1. Close Minecraft. Replace the previous CraftGPT JAR in the mods folder with the alpha.2 JAR.
   Keep only one CraftGPT version installed.
2. Reopen the same world and draft. If the area, context and active draft are unchanged,
   open CraftGPT Planning Tools and choose "Import Codex result".
3. Review the resulting ghost preview before placing it.
4. If a stale-base or context error appears, do not change IDs or hashes by hand. Restore the
   original draft/selection if available, or request changes again from the current preview.

No player save, request file or installed Minecraft mod was modified during development.

## Verification

- Offline Gradle test and build completed with Java 25.
- 202 tests across 52 suites passed, zero failures, errors or skipped tests in the incident run.
- The saved failed repair was replayed read-only against its real stored artifact. All changed
  and unchanged coordinate states matched, and the plan and merged draft passed client validation.
  The replay reconstructs relative context for validation; it does not validate a live world.
- Six portable-viewer occupancy checks passed.
- Regression cases cover manual repair, stale request/review hashes, missing base, keep,
  legacy full builds, exported/player scopes, material preservation and unsupported inspection.
- Optional incident replay reads CRAFTGPT_REPLAY_REQUEST and CRAFTGPT_REPLAY_ARTIFACT environment
  variables. It skips when these are not supplied. Private player data is not included in source.

Live checks still required: import the saved result, place/undo, request changes twice in succession,
automatic visual repair, keep without an extra version, and rejection after switching drafts.
No live Minecraft test or paid model generation was run.

## Unchanged limitation

Functional beds, chests and furnaces are still restricted by the existing block-entity safety rules.
This patch does not claim decorative substitutes are functional furniture. Supporting those blocks
needs safe placement and undo support in a separate change.
