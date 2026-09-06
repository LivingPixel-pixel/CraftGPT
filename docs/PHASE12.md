# Phase 12: One-click Codex workflow

Phase 12 turns the portable Phase 11 handoff into a background workflow controlled entirely from Minecraft.

## Player workflow

1. Select an area and enter the building intention.
2. Choose **Build with Codex**.
3. CraftGPT opens a progress screen and starts Codex locally.
4. Choose **Continue playing** at any time. Closing the screen does not cancel the job.
5. Reopen the CraftBook to see the active Codex job.
6. When Minecraft finishes validation, inspect the ghost build.
7. Iterate, abandon, accept, place, or return to an earlier version.

No CraftGPT skill installation, browser upload, result download, or manual import is required.

## Bundled worker

Each request directory is created automatically with:

- `request.craftgpt.json`: coordinate-free exact area context, instruction, and previous plan
- `PROMPT.md`: the complete bundled design, compilation, quality, and safety rules
- `result.schema.json`: a dynamic schema that binds the result to the exact request hashes
- `preview.html`: the optional local browser inspector
- `result.craftgpt.json`: written by Codex when the job completes

The schema constrains the final response to the existing plan and compact build-draft records. Minecraft still performs its independent Java validation after structured output succeeds.

## Local Codex bridge

CraftGPT launches `codex exec` as a direct child process without a shell. The invocation uses:

- the request directory as the only working root
- `read-only` sandbox permissions
- a locally saved Codex session that can be reopened after the run stops
- ignored user config and execution rules for predictable isolation
- JSON event output for safe progress states
- `--output-schema` for structured output
- `-o result.craftgpt.json` for automatic saving

The process inherits saved Codex authentication but CraftGPT removes `OPENAI_API_KEY` and `CODEX_API_KEY`. A short preflight accepts only a Codex CLI session signed in through ChatGPT. CraftGPT never opens or reads the Codex authentication file.

## Background lifecycle

The Minecraft client tracks these states:

1. Refreshing the selected area
2. Starting
3. Thinking
4. Writing or repairing
5. Importing
6. Validating
7. Ready, failed, or cancelled

The worker runs on a virtual background thread. Screen closure has no effect on it. Area changes, world disconnection, explicit cancellation, or a stale context invalidate the generation and prevent later import.

Successful output is imported only if request ID, context hash, instruction hash, previous plan hash, area bounds, operation count, palette, and block states all remain valid. The server rescans the world before accepting the preview. No block is placed until the player explicitly accepts and places the ghost build.

## Requirements and fallback

- Codex CLI installed through the Codex desktop app or available through `PATH`
- one-time `codex login` with ChatGPT
- active internet connection
- available ChatGPT plan allowance

The direct Responses API path and manual export/import commands remain available as fallbacks.

## Version 0.12.1 discovery fix

Minecraft launchers can inherit a stale `PATH` even while the Codex desktop app is installed and
running. CraftGPT now checks `PATH` first, then discovers the newest installed binary under the
versioned `%LOCALAPPDATA%\OpenAI\Codex\bin\<version>\codex.exe` directory. The app does not need to
remain visibly open while the background CLI worker runs.

## Version 0.12.2 progress clarity

The waiting screen distinguishes completed work from the currently running step. The active step
uses an animated yellow marker, and the screen reports elapsed time, received Codex updates, and
the age of the latest update. This makes a long planning interval visibly active and helps identify
a process that has stopped producing events. The background-play explanation now wraps across
lines at narrow GUI scales.

## Version 0.12.3 diagnostics and session inspection

Each request now stores `codex-run.log`, `codex-events.jsonl`, and, after the first Codex thread
event, `codex-session.txt`. The first file is a readable timeline with executable discovery, login
preflight, process ID, event summaries, exit status, cancellation, and result creation. The JSONL
file preserves every complete line emitted by `codex exec --json` for detailed diagnosis.

The waiting screen counts only real Codex JSON events. It offers a live paginated log viewer and
shows a possible-stall warning after two minutes without an event. Runs are no longer launched
with `--ephemeral`. Once a run has stopped, the saved non-interactive session can be reopened in
the Codex CLI through Windows Terminal. Resuming is kept unavailable while the original process is
active to avoid two processes writing to one session.

## Version 0.12.4 stdin fix and live chat

Version 0.12.3 diagnostics exposed the actual blocker: Codex printed `Reading additional input from
stdin...` and then waited because Java still held the child input pipe open. CraftGPT now closes the
process output stream immediately after startup. The complete prompt remains a normal command
argument, and closing the unused pipe delivers the end-of-file signal Codex requires.

The generation screen now offers a chat-style, read-only transcript built from the visible JSONL
events. It shows the player instruction, Codex messages and reasoning summaries, tool activity,
errors, and completion state without presenting hidden reasoning. After `thread.started` supplies a
technical thread ID, a desktop deep link can open the same local chat through
`codex://threads/<thread-id>`.

## Version 0.12.5 session ownership UX

The Codex desktop app recognizes a saved non-interactive thread while CraftGPT is generating, but
the active CLI process still owns that session. Opening the deep link at that time produces a
session-in-use screen instead of a useful live view. CraftGPT now keeps the desktop action disabled
until the child process exits. The in-game transcript remains live during generation, and the same
button becomes available as soon as the saved session can be opened safely in the desktop app.

## Version 0.12.6 embedded input stream

The Codex worker previously received file names and attempted to read them with PowerShell. The
local Codex command policy can reject that shell invocation even in a read-only workspace. The mod
now reads the bounded export files itself and embeds the worker rules, untrusted request JSON, and
result schema in one prompt. `codex exec` receives `-` as its prompt argument, CraftGPT writes the
complete UTF-8 prompt to stdin, and the stream is closed immediately afterward. The worker is told
not to use shell, file, web, or other tools because every required input is already present.

The existing read-only sandbox and schema-constrained output remain enabled. Input files are
validated as regular files inside the request directory and bounded before reading. The expected
`Reading additional input from stdin...` status is represented as normal protected-context progress
in the Minecraft chat instead of a failure.

## Version 0.12.7 canonical planning candidates

A valid generated cottage exposed a client-only mismatch. The build palette allowed safe canonical
states, but planning material-role candidates accepted only bare identifiers. A roof candidate such
as `minecraft:brick_slab[type=bottom,waterlogged=false]` therefore caused
`invalid_material_roles` before server validation despite an otherwise valid result.

Planning material candidates now use the same bounded, canonical, dangerous-state-aware validation
as the build palette. The worker prompt still requests bare identifiers in planning metadata to keep
plans concise. When any future client import fails, CraftGPT appends a structured
`craftgpt.client_error` event and a readable failure line to the request diagnostics.

## Version 0.12.8 fresh iteration context and automatic repair

One-click generation now requests a fresh, read-only server scan of the current selection before
creating the Codex package. This prevents a placed preview from leaving the client with an older
area hash. The new scan includes every block changed by the previous placement, while preserving
the current project and plan-version history for the next instruction.

Automatic imports are tied to the exact request directory returned by the Codex worker. Another
newer result on disk can no longer be selected accidentally. When the exact result fails the plan,
build-draft, canonical-state, bounds, duplicate-coordinate, operation-count, or portable-pair
validator, CraftGPT writes the precise phase and validation code to both diagnostic views. It then
resumes the same saved Codex session with that trusted feedback and the previous result, asks for a
complete replacement JSON object, and validates the replacement through the full pipeline again.
Model-dependent server rejections, including invalid block states, unsafe blocks, palette errors,
out-of-bounds operations, duplicate coordinates, and empty builds, use the same feedback loop.
Selection, unloaded-chunk, rate-limit, and stale-world failures are not repair requests because the
model cannot safely correct those conditions without a new trusted world snapshot.

The repair loop is intentionally bounded to two additional Codex turns. The waiting screen shows a
dedicated repair state, and the in-game Codex chat reports when Minecraft sends feedback and when a
replacement arrives. Server validation remains independent and still has final authority before a
ghost preview becomes available.

## Version 0.12.9 focused preview approval

The final ghost-preview screen now emphasizes the actual decision instead of exposing every tool at
once. It shows a short build summary, validation state, validated change count, preview coverage, and
one full-width action that advances from acceptance to placement. The only adjacent actions are
requesting changes and continuing to play.

Regeneration, ghost visibility, abandon, undo, placement history, usage details, Codex chat, and the
Codex desktop link remain available under **More options**. Safety behavior is unchanged: acceptance
and placement remain separate explicit actions, and incomplete preview coverage still requires an
additional confirmation.

## Version 0.13.0 Codex selection, strict builds, and UI polish

CraftGPT now stores a separate model and reasoning selection for the local one-click Codex path.
The focused **Settings > Codex** screen offers readable Sol, Terra, and Luna presets, a custom model
ID field, and low through maximum reasoning. Every initial run passes that model and reasoning to
`codex exec`; repairs resume with the exact same selection. API planner and builder settings remain
independent.

The build contract is now explicit at every layer. Clicking Build requires a complete placeable
object with a non-empty palette and safe in-bounds operations that produce at least one actual world
change. A plan-only, empty, or unchanged result is rejected. The bounded repair prompt then tells the
same Codex session that the user requested Build rather than Plan and requires a full replacement.
Client and server validation still decide whether the replacement is safe to preview.

The planning screen now gives the chosen Codex model one full-width primary action. Manual import,
the local viewer, and version utilities are grouped under **More tools**. CraftBook displays the API
and Codex model selections separately, and the main workflow screens share the same gold heading and
action hierarchy.
