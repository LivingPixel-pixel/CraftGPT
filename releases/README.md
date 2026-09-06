# Release artifacts

Download the current JAR from [GitHub Releases](https://github.com/LivingPixel-pixel/CraftGPT/releases).

Local builds are stored in version-specific folders:

```text
releases/
  0.19.0-alpha.4/
    CraftGPT-0.19.0-alpha.4-mc26.1.2.jar
```

Run `gradlew.bat packageRelease` in `fabric-mod/` to build and copy the current version here. On Linux or macOS, use `./gradlew packageRelease`.

These generated folders are ignored by Git. Release notes live in `docs/`; downloadable JARs are attached to GitHub releases.
