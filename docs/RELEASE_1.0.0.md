# CraftGPT v1.0.0: experimental prerelease

**Released without extensive in-game testing.** Automated compilation and tests passed, but real gameplay, dedicated servers, resource packs, mixed modpacks and world rollback have not been extensively verified. Start in a disposable world and keep backups. The v1 number marks the development baseline, not a stability guarantee.

CraftGPT lets you describe a Minecraft build, review a draft in the CraftBook, request changes, inspect a preview and confirm placement. Generation uses your locally installed, signed-in Codex CLI or a configured API provider. Account limits and API charges apply.

## What changed

- Established Minecraft **26.1.2** as the primary v1 development target.
- Added separate experimental Fabric builds for **1.20.1, 1.21.1, 1.21.11, 26.1, 26.1.1 and 26.2**.
- Added version-specific menus, model/rendering adapters, older-version packet transport and scoped placement safeguards.
- Adapted the CraftBook recipe for older Minecraft formats and checked it with Minecraft's recipe parser.
- Added explicit dependency metadata, production JAR verification and SHA-256 checksums.
- Regular updates focus on the primary build. Other Minecraft versions receive ports at major releases only.

## Choose your download

Install exactly one CraftGPT JAR matching your Minecraft version, together with the matching Fabric API. Remove older CraftGPT JARs first. For dedicated servers, install matching CraftGPT versions and dependencies on both the server and client.

| Minecraft | CraftGPT download | Java | Fabric Loader minimum | Fabric API baseline |
|---|---|---|---|---|
| **26.1.2, primary** | [CraftGPT-1.0.0-mc26.1.2.jar](https://github.com/LivingPixel-pixel/CraftGPT/releases/download/v1.0.0/CraftGPT-1.0.0-mc26.1.2.jar) | **25** | **0.19.3** | **0.154.0+26.1.2** |
| 1.20.1 | [CraftGPT-1.0.0-mc1.20.1.jar](https://github.com/LivingPixel-pixel/CraftGPT/releases/download/v1.0.0/CraftGPT-1.0.0-mc1.20.1.jar) | 21 | 0.18.4 | 0.92.12+1.20.1 |
| 1.21.1 | [CraftGPT-1.0.0-mc1.21.1.jar](https://github.com/LivingPixel-pixel/CraftGPT/releases/download/v1.0.0/CraftGPT-1.0.0-mc1.21.1.jar) | 21 | 0.18.4 | 0.116.17+1.21.1 |
| 1.21.11 | [CraftGPT-1.0.0-mc1.21.11.jar](https://github.com/LivingPixel-pixel/CraftGPT/releases/download/v1.0.0/CraftGPT-1.0.0-mc1.21.11.jar) | 21 | 0.18.4 | 0.141.6+1.21.11 |
| 26.1 | [CraftGPT-1.0.0-mc26.1.jar](https://github.com/LivingPixel-pixel/CraftGPT/releases/download/v1.0.0/CraftGPT-1.0.0-mc26.1.jar) | 25 | 0.19.3 | 0.154.0+26.1.2 |
| 26.1.1 | [CraftGPT-1.0.0-mc26.1.1.jar](https://github.com/LivingPixel-pixel/CraftGPT/releases/download/v1.0.0/CraftGPT-1.0.0-mc26.1.1.jar) | 25 | 0.19.3 | 0.154.0+26.1.2 |
| 26.2 | [CraftGPT-1.0.0-mc26.2.jar](https://github.com/LivingPixel-pixel/CraftGPT/releases/download/v1.0.0/CraftGPT-1.0.0-mc26.2.jar) | 25 | 0.19.3 | 0.160.0+26.2 |

**The 1.20.1 build also requires Java 21**, even though Minecraft 1.20.1 normally supports Java 17. These are Fabric builds for Minecraft Java Edition, not Forge, NeoForge or Bedrock builds.

Every JAR has an attached `.sha256` file. `SHA256SUMS.txt` lists all seven JAR checksums.

## What was tested

- All seven targets passed local automated builds, tests and production JAR checks.
- Primary 26.1.2: 221 tests per Fabric profile, with 220 passing and one intentional private-fixture skip. Both baseline and pinned current Fabric profiles passed.
- 1.20.1 and 1.21.1: 229 tests each, with 228 passing and one intentional skip.
- Other targets: 221 tests each, with 220 passing and one intentional skip.
- Six portable-preview checks passed.

The non-primary targets were tested with their baseline Fabric dependencies. Their current Fabric comparison profiles have not completed the full matrix. See the [validation record](https://github.com/LivingPixel-pixel/CraftGPT/blob/v1.0.0/docs/V1_VALIDATION.md) for scope and remaining checks.

## Known limitations

Generated block entities, including working beds, chests and furnaces, remain unsupported. Preview rendering can differ from the in-game result, especially with resource packs or rendering mods. Undo is not a substitute for a world backup.

Prompts, selected block context and visual-review images are sent through the generation provider you select. Keep API keys and private request files out of bug reports.

Report problems through [GitHub Issues](https://github.com/LivingPixel-pixel/CraftGPT/issues), including Minecraft, Fabric Loader, Fabric API, Java and CraftGPT versions, plus a small reproduction example.
