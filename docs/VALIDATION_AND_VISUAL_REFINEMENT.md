# CraftGPT validation and visual refinement

## Multi-problem diagnostics

CraftGPT no longer stops at the first independently detectable model error. One validation pass can report up to 64 findings. Every finding contains:

- `code`: stable machine-readable failure category
- `location`: exact plan, palette, or operation path
- `cause`: why Minecraft rejected the value
- `suggestion`: a concrete correction for the next Codex turn

The same structured findings are used in four places: the readable request log, the raw event log, the in-game Codex chat, and the trusted automatic repair prompt. The repair prompt requires Codex to fix every listed item and then audit the complete result again.

Some checks are necessarily dependent. If the root JSON cannot be parsed, CraftGPT cannot inspect its palette or operations yet. If a palette entry cannot be parsed, CraftGPT cannot compare operations using that entry against live block states. After each repair, the full validation pass runs again so newly reachable checks are still enforced.

## Three visual improvement steps

The visual improvement button performs three sequential rounds in the saved Codex session:

1. Architecture: massing, silhouette, roof geometry, foundation, incoherent objects
2. Function: entrance, circulation, windows, support, proportions, recognizable features
3. Polish: facade rhythm, material coherence, restrained details, cleanup, player-eye-level appearance

Each round uses three automatic contact sheets rendered from the current exact context and ghost-build operations. Together they contain twelve views:

- four exterior corner views
- center cutaways from south, east, north, and west
- a roofless upper overview
- lower and upper interior sections
- an isolated vertical center core

A returned result must pass client and server validation before it becomes the input for the next set. This means round 2 sees the accepted result of round 1, and round 3 sees the accepted result of round 2.

The process does not move the player, manipulate the camera, or place blocks. Visible block faces use textures from the active Minecraft resource-pack stack, including player-selected packs. Missing resources retain deterministic material-color fallbacks. Coordinate occupancy and texture identity are authoritative. Complex block-model geometry, biome tint, entities, and Minecraft lighting remain simplified, so Codex combines the images with the exact block states in the structured request.

The three PNG files are attached to the same saved Codex session in one review turn. CraftGPT uses a fixed twelve-view evidence contract instead of allowing an unconstrained camera-request loop. This keeps the workflow deterministic, prevents repeated image requests, and still gives every improvement round fresh images of the newly accepted build.

The Generation Effort slider controls the planned creative chain:

- Effort 1: one initial build, no automatic image review
- Effort 2: one initial build plus one 12-view review
- Effort 3: one initial build plus two sequential 12-view reviews
- Effort 4 through 7: the same pattern, up to six visual reviews

Each accepted review becomes the source for the next rendered image set. The previous fixed behavior maps to Effort 4 and remains the default for existing and new configurations. Validator-driven repair turns are bounded separately and do not count as creative effort. The manual visual action runs exactly one additional review later. While a visible ghost preview is active, CraftGPT suppresses the bright area-boundary particles and uses a thinner, less opaque wireframe so inspection remains readable.
