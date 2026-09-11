# v1 candidate validation

Local checks on 2026-09-11 for CraftGPT `1.0.0`, built outside OneDrive with the included Gradle wrapper. This records automated checks, not a published release or completed in-game testing.

## Primary build

Minecraft **26.1.2** is the active development target. Both baseline Fabric dependencies and the pinned current profile passed compilation, tests and production JAR checks. Each profile ran **221 tests: 220 passed, one intentionally skipped**. The portable preview passed all **six** offline checks.

Routine CI is configured for these two primary profiles. The changed GitHub workflow has not yet run remotely.

## Prepared major release ports

| Minecraft | Baseline tests | Result |
|---|---:|---|
| 1.20.1 | 229 | 228 passed, one intentional skip |
| 1.21.1 | 229 | 228 passed, one intentional skip |
| 1.21.11 | 221 | 220 passed, one intentional skip |
| 26.1 | 221 | 220 passed, one intentional skip |
| 26.1.1 | 221 | 220 passed, one intentional skip |
| 26.2 | 221 | 220 passed, one intentional skip |

There were no test failures or errors. Every skipped test requires a private saved-incident replay fixture. The older ports' additional checks exercise large packet reassembly and rejection, scoped placement suppression, actual Fabric mixin application, and loading the packaged CraftBook recipe through Minecraft's own recipe parser. Recipe tests substitute a vanilla result item after asserting the packaged CraftBook identifier; they do not replace live item registration testing.

Baseline production JARs were built for all seven targets, with exact Minecraft and Java metadata and SHA-256 files. Legacy JARs use remapped production names; the remapped placement mixin targets were also inspected. The current Fabric comparison profiles for the six non-primary targets are configured for the manual major release matrix but were not completed in this local validation.

## Remaining release checks

Run the [live checklist](COMPATIBILITY.md#live-checks-before-shipping-a-major-release) on the primary build before publishing v1. Perform the same checks and the full Fabric profile matrix for each port intended for that major release. In-game UI, rendering, generation, dedicated-server play, resource packs, mixed modpacks and world rollback have not been verified by this automated pass.

Day-to-day updates continue on Minecraft 26.1.2. Other versions ship only at major releases, as defined in [the release policy](COMPATIBILITY.md#primary-v1-development).
