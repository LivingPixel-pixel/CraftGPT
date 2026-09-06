# CraftGPT 0.19.0-alpha.4

## Changes

- Added GPT-6 Astra (`gpt-6-astra`) to the Codex model picker and both API model pickers. Existing saved models and defaults are unchanged.
- API settings skip unsupported `none` reasoning for Astra and switch it to `low` when selecting Astra. Request validation also rejects unsupported combinations before sending.
- Added standard short-context Astra API price estimates. These estimates are not Codex subscription charges and do not model long-context pricing multipliers.
- Added one shared functional-survival rule to planning, direct API building, portable Codex requests and visual review. A requested bed means a real usable bed with paired head/foot states, not wool or carpet furniture. Requested storage and smelting must also be functional.
- Unsupported utilities must be identified as unfulfilled rather than silently replaced. Decorative replicas are allowed only when explicitly requested as nonfunctional.

The Astra identifier, supported reasoning levels and pricing were checked against the [official model documentation](https://developers.openai.com/api/docs/models/gpt-6-astra) on 2026-09-06. Model access depends on the account.

## Important limitation

This is a model-selection and prompting change, not expanded block placement support. The existing validator still rejects block entities, including beds, chests and furnaces. No safety check was removed. A bed-only request can therefore still be rejected as unsupported; the prompt must not invent a fake bed to satisfy it. Functional block-entity support needs separate placement, preview and undo handling.

Prompt instructions reduce misleading substitutions but do not guarantee model compliance. Server validation remains authoritative.

## Installation and testing

Minecraft 26.1.2, Fabric Loader 0.19.3 and Fabric API 0.154.0+26.1.2.

Replace the old CraftGPT JAR with `CraftGPT-0.19.0-alpha.4-mc26.1.2.jar`. Do not load two CraftGPT versions together.

Verified offline:

- Java build completed.
- 221 tests across 57 suites, zero failures, errors or skips, including saved-result replay.
- Six portable preview occupancy checks passed.
- Regression coverage checks Astra presets, reasoning validation, standard pricing and shared survival instructions in exported requests.

Live Minecraft UI and Astra generation have not been tested. No paid API calls were made.

Suggested live checks:

1. Choose Astra in Codex settings, save and reopen settings to confirm persistence.
2. Choose Astra separately for API planning and building. Confirm reasoning cycles without `none`.
3. Generate a simple build using an account with Astra access.
4. Ask for a functional bed or furnished survival shelter. Confirm unsupported utilities are disclosed and no wool/slab bed is claimed to work.
5. Ask explicitly for a decorative bed sculpture to check the intentional-replica exception.
