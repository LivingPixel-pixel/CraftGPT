# 0.19.0-alpha.3: focused UI overhaul

## Main changes

- CraftBook: one contextual next action, plus Tools, Settings, Help and Close. Duplicate plan,
  preview, version and history shortcuts no longer compete on the landing screen.
- Prompt editor: more room for the intention, one Build with Codex action, and a compact area label.
  API planning, version tools and file import are secondary destinations. The API price estimate
  is shown in the API details, not as if it were the price of a Codex generation.
- Approval: Place, Inspect and Request changes are the prominent actions. Tools and Details
  contain the remaining controls. Incomplete-preview warnings and explicit confirmation remain.
- Preview tools: Display, Improve, History and recovery, and Activity. Secondary action menus
  show at most four actions per page. Paid API rebuilds are explicit and confirmed.
- Progress: current phase, elapsed time and a phase indicator replace the large checklist.
  No percentage or completion estimate is invented. Long silence is still flagged. Chat, full
  status, logs, the request folder and the Codex-app action remain accessible.
- Completion does not automatically replace the progress screen with another screen. Review is
  a user action. Existing completion notifications still come from the controller.
- Settings: a hub separates Codex from optional API configuration and area/recovery controls.
  Advanced configuration has API, Models, Area and Recovery tabs. Unsaved text survives
  tab changes and widget rebuilds. API keys remain masked and are only saved with Done.
- Codex settings: one model selector, a custom model field only when needed, reasoning and effort.
  Descriptions and tooltips provide detail without duplicating explanatory paragraphs.
- Plan review: three primary footer actions replace five narrow competing buttons.
- Chat: model build JSON is summarized as title, summary and review findings. Raw output remains
  available with a toggle; logs and saved results are unchanged. Raw tool chatter is hidden only
  in the readable view. Malformed output is retained rather than silently discarded.

## Interaction and presentation

The focused screens use a shared dark panel, consistent margins, quieter text colors and a fixed
footer within the supported 320 x 240 logical GUI minimum. Long display text is bounded; full
preview/status content remains available in Details. Action labels have tooltips. English and
German copy and format placeholders are checked.

Back, Close and Continue playing do not cancel generation. Cancellation, discarding a preview and
clearing an existing area selection are distinct confirmed actions. These menus do not pause
the integrated server. No block palette, model selection, generation policy or safety rule was changed.

## Install

Close Minecraft and replace the old CraftGPT JAR with
`CraftGPT-0.19.0-alpha.3-mc26.1.2.jar`. Keep only one version installed.
This development run did not change your installed mod, world, settings or saved requests.

To reuse a saved generation, use the prompt screen's Tools, then Import & files, then Import Codex
result. The existing area and draft matching requirements still apply.

## Verification and remaining live checks

Offline Java 25 / Gradle test and build succeeded: 217 tests across 55 suites, no failures or skips
in the run with the optional saved-result replay enabled. Six portable-viewer occupancy checks passed.
New tests cover action geometry at 320 x 240, 426 x 240 and larger GUI sizes; menu/settings overlap;
translation coverage; and readable-chat fallbacks. These are structural checks, not Minecraft screenshots.

Minecraft was not launched and no model call was made. Please check:

1. Open the CraftBook at your usual GUI scale, then at a larger scale. Check English or German
   labels, tooltips, keyboard focus, and the dark panel behind controls.
2. Enter a prompt, visit Tools and Codex settings, then return. The prompt should remain.
3. Edit API fields, switch tabs and resize the window. Cancel should not save; Done should save.
4. Start a build. Close progress and keep playing; reopening it should show the current phase.
   Check that completion does not take over whichever other screen you opened.
5. Review, inspect, request changes and place. Confirm that partial-preview warnings, disabled
   placement conditions and server errors remain visible and understandable.
6. Open Activity and Details for a failure. Read the complete message and log.
7. Toggle readable/raw chat and return to older pages. Original output must remain available.
8. Visit preview Display, Improve and History tools. Cancel a discard/reset confirmation and
   verify nothing changed. Run placement/undo tests as usual before treating this as release-ready.
