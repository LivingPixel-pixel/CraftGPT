# Release artifacts

Download the current JAR from [GitHub Releases](https://github.com/LivingPixel-pixel/CraftGPT/releases).

Local builds are stored in version-specific folders:

```text
releases/
  1.0.0/
    CraftGPT-1.0.0-mc26.1.2.jar
    CraftGPT-1.0.0-mc26.1.2.jar.sha256
```

Run `gradlew.bat packageRelease` in `fabric-mod/` to build and copy the current version here. On Linux or macOS, use `./gradlew packageRelease`.

Minecraft 26.1.2 is the primary v1 build. Regular releases update this target. Other Minecraft versions receive ports only for major releases, using an explicit `-PmcTarget` or the manual full CI matrix. See [compatibility and release policy](../docs/COMPATIBILITY.md).

The local 1.0.0 files are release candidates until live checks are complete and a release is explicitly published. The build task never uploads them automatically.

These generated folders are ignored by Git. Release notes live in `docs/`; downloadable JARs are attached to GitHub releases.
