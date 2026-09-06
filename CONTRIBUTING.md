# Contributing to CraftGPT

Thanks for taking a look. Small bug fixes, reproducible failure reports and live Minecraft testing are particularly useful at this stage.

## Before changing code

For a larger change, open an issue first so we can agree on the scope. Please keep unrelated cleanup out of a bug fix.

The main constraints are:

- A generated draft never places itself. The player confirms placement.
- Server-side bounds, permissions and block safety checks stay authoritative.
- The model owns material choices. Do not hide invalid output by replacing blocks with generic materials.
- Failed iterations should preserve the last valid draft.
- No API keys, private request packages, world saves or player data in commits or test fixtures.

## Build and test

Use JDK 25 and the included Gradle wrapper:

```sh
cd fabric-mod
./gradlew test build
node tools/test-portable-viewer.cjs
```

On Windows, use `gradlew.bat` instead. Node.js is only needed for the portable-preview check.

The saved-incident replay test is intentionally skipped unless local fixture paths are supplied. Do not commit those private fixtures to make it pass.

For UI, rendering, placement and recovery changes, also use the [manual checklist](docs/LIVE_TESTS_0.19.0-alpha.1.md) in a disposable world. Automated tests alone do not establish that the mod works in-game.

If a synced folder interferes with Gradle, build a separate local copy and copy the finished JAR back.

## Reporting a bug

Include:

- CraftGPT, Minecraft, Fabric Loader and Fabric API versions.
- Operating system, selected model, reasoning level and effort.
- A small prompt and area size that reproduce the issue.
- Expected behavior, actual behavior and any relevant error text.
- A screenshot if the problem is visual.

Check screenshots, logs and exported files for secrets and personal information. Never attach `client-secrets.properties` or a whole Minecraft config directory.

## Pull requests

Explain the problem and the change in plain language. Note which tests you ran and which live checks still need to be done. New fixes should include regression tests where possible.
