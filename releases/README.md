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

Version 1.0.0 is an experimental prerelease published without extensive in-game testing. Local packaging never uploads files automatically. The [release notes](../docs/RELEASE_1.0.0.md) describe the validation limits and exact downloads.

These generated folders are ignored by Git. Release notes live in `docs/`; downloadable JARs are attached to GitHub releases.
