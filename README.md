<img alt="Pylon logo" src="src/main/resources/assets/pylon/icon.png" width="128px" />

# Pylon
Pylon allows legacy Minecraft versions to run with LWJGL 3. It replaces the outdated windowing system with SDL 3, and serves as a library for mods.

## Downloads
* [Latest Release](https://github.com/notdevcody/pylon/releases/latest)
* [Latest Nightly](https://nightly.link/notdevcody/pylon/workflows/nightly/main/pylon-nightly.zip)

## Features
### Fixes
- Lag from high-polling-rate mice
- Keys and mouse buttons getting stuck
- Crash when resizing the window on macOS

### Improvements
- System IME support
- More precise mouse input
- Proper clipboard handling
- Reduced display / input overhead
- Dark mode for the window title bar
- Better fullscreen and window resizing

## Developers
Pylon is published to the Clover Client Maven, and can be added as a dependency like so:
```kts
repositories {
    maven("https://maven.cloverclient.com/releases")
}

dependencies {
    modImplementation("pl.tomgirl:pylon:${version}")
}
```

## Hestia's fork
This branch (`hestia`) is a fork of Pylon 0.2.2 (tag `v0.2.2`, e58525d) for Kora, the Hestia Minecraft 1.8.9 client,
under Pylon's own license, the GNU LGPL 3.0 only (`LICENSE.md`). It adds what a renderer other than OpenGL needs and
changes nothing while no such renderer is installed: the mod id stays `pylon`, and the fork and upstream Pylon can
replace each other.

Coordinates `com.furiah.hestia:pylon-hestia:<upstream version>+hestia.<n>`. Build with `gradlew build
publishToMavenLocal`; it needs a JDK 21 for the compiler (a Gradle toolchain) and makes the same jar on every machine.

Every change, newest last:
- 2026-10-18: build setup. LF line endings in every checkout (`.gitattributes`), JDK 21's compiler as upstream's release
  workflow uses (with both, the `v0.2.2` tag builds the published `pylon-0.2.2.jar` byte for byte), the Gradle wrapper
  9.8.1, the fork's coordinates (`gradle.properties`).
