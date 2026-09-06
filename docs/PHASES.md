# CraftGPT implementation phases

## Phase 1 — Foundation and settings UI

- Fabric project structure
- separate common and client source sets
- `/craftgpt settings` client command
- local client configuration
- settings for endpoint, API key, planning model, builder model, reasoning effort and safety limits
- versioned release packaging

## Phase 2 — Area selection (implemented in 0.2.0)

- per-player, server-authoritative area state
- `/craftgpt setArea start|stop`
- `/craftgpt area info|clear`
- dimension and inclusive volume validation
- client synchronization through Fabric networking
- visible particle outline for the selected area

## Phase 3 — Intention planning (implemented in 0.3.0)

- bounded, server-authoritative world-context extraction without block-entity or player data
- complete block-state fingerprint plus compact relative layer and surface summaries
- local planning UI and direct Responses-compatible HTTPS client
- strict structured planning schema and local result validation
- immutable content-hashed plan versions and non-destructive, locally confirmed revert
- per-world/per-server project scopes, staged atomic version commits and orphan recovery
- selection IDs and context hashes to reject stale planning results

## Phase 4 — Build compilation and preview (implemented in 0.4.0)

- separate low-cost builder-model workflow with its own reasoning level
- compact relative block operations and deterministic `/setblock` translation
- strict local validation followed by authoritative server validation
- immutable preview artifacts and non-mutating ghost-block preview
- geometry-only server validation packets with per-player rate limiting
- explicit preview acceptance, incomplete-coverage confirmation, iteration or abandonment

## Phase 5 — Placement and recovery (implemented in 0.5.0)

- explicit accepted-preview placement from the local review UI
- authoritative server revalidation, permission checks and loaded-area checks
- bounded tick-batched placement with live progress
- immutable before-placement snapshots and crash-safe mutable progress state
- conflict-aware undo that preserves later player or mod changes
- interrupted-job recovery and latest-placement status after reconnect
- per-build lifecycle so a completed build never blocks the next selected area

## Phase 6 — Extended history and block-data support (implemented in 0.6.0)

- browsable coordinate-free placement history with up to 50 validated entries
- targeted, double-confirmed undo and redo of a selected journal
- server-side overlap ordering that protects newer active builds
- conflict-aware, idempotent redo using the same batch scheduler as placement and undo
- opt-in replacement of existing block entities only after bounded full-NBT snapshots
- generated block entities and model-provided NBT remain forbidden

## Phase 7 — Recovery operations and world linkage (implemented in 0.7.0)

- integrity-checked, server-local recovery export/import without networked snapshot or NBT data
- configurable 10/25/50 live-journal retention with archive-before-delete semantics
- merged live/archive history and non-overwriting journal restore
- active-preview linkage from history and a client-only particle recovery-area marker
- bounded maintenance/inspection protocol plus automated persistence and payload tests
- live-world placement, NBT and interruption checks remain an explicit manual release gate

## Phase 8 — CraftBook UX (implemented in 0.8.0)

- rare, glinting CraftBook item with recipe, creative-tab entry and `/craftgpt book`
- context-aware guided dashboard over the existing safe commands
- direct visual access to area selection, planning, versions, preview, placement, history and settings
- quality/balanced/budget model presets plus a preserved custom model-ID mode
- official model-catalog link and short in-game explanations of each model role
- full current reasoning selector from `none` through `max`
- page, selection, work, success, completion and error sound feedback
- closed server action protocol that cannot carry arbitrary command text

## Phase 9 — Exact and compressed area context (implemented in 0.9.0)

- explicit Compressed and Full context modes shared by planning and building
- palette-compressed exact non-air block states with relative coordinates
- explicit air semantics for all omitted in-bounds coordinates
- visible Full-mode block cap with strict refusal instead of silent truncation
- bounded exact-context payloads without NBT, inventory, entity or absolute-coordinate leakage
- backward-compatible context hashes and persisted projects

## Phase 10 — Plan review and transparent generation (implemented in 0.10.0)

- five-page human-readable review of every structured planning field
- explicit plan-review step between intention creation and preview compilation
- preflight token/cost ranges for built-in model presets without false precision
- actual input, cached, output and reasoning usage plus latency after API responses
- optional local usage sidecars that never become required for loading plans or builds
- explicit cancellation of active planning and builder requests
- preview completeness independent of the current camera frustum

## Phase 11 - Portable subscription workflow (implemented in 0.11.0)

- direct export of prompt plus exact relative area context without an API call
- reusable CraftGPT Building Skill that produces a structured plan and compact build draft
- self-contained local browser preview with existing, changed and final views
- result import bound to request, context, instruction and previous plan hashes
- existing local validators, server rescan, ghost preview, acceptance and recovery reused unchanged
- no API key, absolute coordinates, entity data or block-entity NBT in portable packages

## Phase 12 - One-click Codex workflow (implemented in 0.12.0)

- bundled building instructions and dynamic result schema inside the mod JAR
- direct background `codex exec` launch using saved ChatGPT authentication
- no skill installation, file upload, manual download or manual import
- non-blocking in-game progress screen with continue-playing and cancel actions
- CraftBook entry to reopen an active background generation
- automatic result import, server validation and ghost-preview delivery
- direct process invocation without a shell, API-key environment variables or auth-file access

## Phase 13 - Semantic Build Program

- component-based build plan with deterministic geometric primitives
- local expansion into the existing validated block-operation artifact
- bounded deterministic quality diagnostics and component-scoped repair
- compatibility path for existing schema-version-1 literal operations

## Phase 14 - Precise visual iteration (first slice implemented in 0.14.0)

- explicit current-view PNG capture for an unplaced Codex ghost build
- saved-session visual review with a fresh request and full replacement output
- shared builder quality bar for silhouette, palette, depth, roof, rhythm and function
- normal client and server validation before the visually reviewed preview replaces anything
- component filters and material-aware preview layers
- plan and block diffs between generated versions
- targeted region/component regeneration without rebuilding accepted sections
- entrance, preserve and reference-style anchors

## Later — Collaboration and scale

- optional server-admin policy for shared/team projects
- chunk-aware very large build streaming and resumable compilation
- provider presets and local proxy support without moving secret ownership to the server
