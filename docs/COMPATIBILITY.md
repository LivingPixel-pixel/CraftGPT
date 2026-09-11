# Minecraft versions and release policy

## Primary v1 development

CraftGPT v1 uses **Minecraft Java 26.1.2 with Fabric** as its primary development target. Features, small improvements and fixes are developed and released there. The current source version is `1.0.0`; it is a local release candidate, not a claim of completed live validation or a published download.

The [v1 validation record](V1_VALIDATION.md) lists the completed automated checks and remaining release checks.

Ports to other Minecraft versions are made **only for major releases**, such as v1 and v2. Regular v1.x updates do not trigger new ports. Existing ports remain attached to their major release source snapshot and are not promised to follow every primary change.

`fabric-mod/release-policy.json` selects the primary target. `versions.json` records explicit port dependencies. The default build and routine GitHub checks use the primary target. The manual **Build and test** workflow can enable **Check all Minecraft ports for a major release**. Packaging a non-primary target requires a major version such as `1.0.0`; `1.1.0` and `1.0.1` are primary-only packages.

## Major release port targets

These are separate JARs, not one universal download. Their code and automated checks must be refreshed when preparing each major release. Hands-on checks remain required before declaring any port ready for normal gameplay.

| Minecraft | Role | Java required by CraftGPT | Baseline Fabric Loader | Baseline Fabric API |
|---|---|---|---|---|
| **26.1.2** | **Primary v1 development** | **25** | **0.19.3** | **0.154.0+26.1.2** |
| 1.20.1 | Major release port | 21 | 0.18.4 | 0.92.12+1.20.1 |
| 1.21.1 | Major release port | 21 | 0.18.4 | 0.116.17+1.21.1 |
| 1.21.11 | Major release port | 21 | 0.18.4 | 0.141.6+1.21.11 |
| 26.1 | Major release port | 25 | 0.19.3 | 0.154.0+26.1.2 |
| 26.1.1 | Major release port | 25 | 0.19.3 | 0.154.0+26.1.2 |
| 26.2 | Major release port | 25 | 0.19.3 | 0.160.0+26.2 |

Minecraft 1.20.1 normally supports Java 17, but **CraftGPT requires Java 21** because its shared implementation uses Java 21 features. Select Java 21 in that instance's launcher settings. Test other installed mods with that runtime too.

Fabric API `0.154.0+26.1.2` declares compatibility with the 26.1 family, despite the patch number in its filename. The candidate's metadata requires the exact Minecraft version for each JAR. Install only that JAR and a compatible Fabric API in both the client and dedicated server, with matching CraftGPT versions. Forge, NeoForge and Bedrock are not targets of this implementation.

The `current` test profile pins Loader `0.19.5` for every target and API `0.155.3+26.1.2` for the 26.1 family; other API pins match the table. These are reproducible comparison profiles, not a promise about every future Fabric version. Routine CI exercises both profiles on the primary target; the manual major release matrix exercises both on all targets.

## Build and validate

Run Gradle with JDK 25. Also install JDK 21 when building or testing Minecraft 1.x targets. Gradle uses the target Java version for tests and bytecode. CI installs both where needed.

From `fabric-mod`, the normal development loop is:

```powershell
.\gradlew.bat test packageRelease
.\gradlew.bat test build "-PfabricProfile=current"
node tools/test-portable-viewer.cjs
```

The major release matrix is explicit:

```powershell
.\tools\build-matrix.ps1 -FabricProfile both
```

Or select one port:

```powershell
.\gradlew.bat test packageRelease "-PmcTarget=1.21.1"
.\gradlew.bat test build "-PmcTarget=1.21.1" "-PfabricProfile=current"
```

On Linux/macOS replace `.\gradlew.bat` with `./gradlew`. For the full matrix, use the manual GitHub workflow or run those commands for each key in `versions.json`.

Outputs are isolated under `build/<minecraft>/`, with current-profile outputs under `build/<minecraft>/current/`. Release JARs and individual SHA-256 files go into `../releases/<mod-version>/`. Only baseline dependencies may be packaged for release. `verifyReleaseJar` checks version metadata, entrypoints, Java bytecode versions, platform classes and legacy safeguard inclusion. Minecraft 1.x artifacts use Loom's remapped production JAR; 26.x artifacts use the unobfuscated format.

## Implementation boundaries

- Generation, provider calls, validation, placement permissions, history and undo use shared source.
- Native screen, texture, model and world rendering APIs live in `src/platform/`. Minecraft 26.2 uses queued geometry submissions; the older versions use their native Fabric render events.
- `gradle/backport.gradle` produces build-local source copies for API renames. Do not edit generated files under `build/`. Native adapters under `src/native/` are compiled without those transformations.
- Minecraft 1.20.1 and 1.21.1 use a bounded fragmented transport for larger context and preview messages. Receivers whitelist payload types and enforce message limits, ordered fragments, expiry, disconnect cleanup and a global reassembly memory cap.
- Those two versions also need scoped placement mixins to preserve the newer skip-side-effect behavior. Suppression applies only to the exact world and block position during CraftGPT placement. Normal block changes outside that scope keep vanilla callbacks. Required mixin injections fail startup if they cannot apply. Tests load the real Fabric mixin environment and check scope restoration and packet handling.
- Older renderers use their native line width, and the 1.21.11 Fabric render context does not expose an active frustum. Distance and per-frame limits remain active. Preview appearance can differ between versions.

## Live checks before shipping a major release

Automated compilation and tests do not verify the complete in-game workflow. For the primary version and each intended port:

1. Launch a fresh Fabric client, then a dedicated server with matching dependencies. Check startup and joining for registry, networking and mixin errors.
2. Get the CraftBook, open each settings screen, select an area, cancel menus and return to the world.
3. Generate a small draft, inspect its textures, rotate the preview and toggle preview options. Test default resources and one resource pack.
4. Send a large context/preview, disconnect during transfer and reconnect. Confirm subsequent requests work and malformed data cannot place blocks.
5. Place, undo and redo in a disposable world. Confirm permissions, bounds, unsafe block rejection and placement history.
6. Exercise rollback around existing block entities and verify inventory/NBT restoration and absence of duplicated drops. Test normal player block updates afterward.
7. Test the intended modpack, especially rendering mods and mods that alter chunk placement. Record exact Minecraft, Loader, API and Java versions with the result.

Keep the prior public release until the chosen candidate passes these checks. Publishing the candidate is a separate action from building it.
