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

## Kora's fork
This branch (`kora`) is a fork of Pylon 0.2.2 (tag `v0.2.2`, e58525d) for Kora, the owner's Minecraft 1.8.9 client,
under Pylon's own license, the GNU LGPL 3.0 only (`LICENSE.md`). It adds what a renderer other than OpenGL needs and
changes nothing while no such renderer is installed: the mod id stays `pylon`, and the fork and upstream Pylon can
replace each other.

Coordinates `gg.kora:pylon-kora:<upstream version>+kora.<n>` (`com.furiah.hestia:pylon-hestia:...+hestia.<n>` before the rebrand). Build with `gradlew build
publishToMavenLocal`; it needs a JDK 21 for the compiler (a Gradle toolchain) and makes the same jar on every machine.

Every change, newest last:
- 2026-10-18: build setup. LF line endings in every checkout (`.gitattributes`), JDK 21's compiler as upstream's release
  workflow uses (with both, the `v0.2.2` tag builds the published `pylon-0.2.2.jar` byte for byte), the Gradle wrapper
  9.8.1, the fork's coordinates (`gradle.properties`).
- 2026-10-18: GL call routing (`pl.tomgirl.pylon.render.GlRouting`, `GlRouteTransformer`, one line in
  `fabric/MixinTransformer`). A renderer installs a router once, before the classes that call OpenGL load; after the
  mixins, Pylon's Fabric transformer then rewrites each loaded class's static calls into `org/lwjgl/opengl` classes as
  the router says: to the same method in another class, to nothing, or to a report. With no router (the default) every
  class is loaded as before.
- 2026-10-18: a window for Vulkan (`pl.tomgirl.pylon.window.VulkanSurface`, `VulkanPresenter`). A `GpuSurface` that makes
  the SDL window for Vulkan instead of OpenGL, without a GL context, and hands presenting and VSync to the renderer's
  presenter; helpers for SDL's Vulkan surface calls on raw handles (no Vulkan bindings in Pylon). Installed through
  `DisplaySdl.setSurface`, which Pylon already had; without it Pylon makes its OpenGL window as before.
- 2026-10-19 (`+hestia.2`): the window made again on request (`DisplaySdl.recreateWindow`, `GpuSurface.recreateWindow`,
  `VulkanPresenter.windowReplaced`, `MouseSdl.windowReplaced`). Windowed only: a new SDL window with the same title, size,
  place and flags, handed to the presenter, shown and raised, then the old one destroyed; the mouse grab, text input,
  icon and maximized state carry over. Window events are taken from the current window only (its SDL id). An OpenGL
  window can't be made again (`GlSurface` keeps the default, which declines); nothing calls it unless a renderer does.
- 2026-11-19 (`+kora.2`): the client's rebrand (Hestia -> Kora): the coordinates `gg.kora:pylon-kora`, the branch `kora`,
  the comments. The code is `+hestia.2`'s; the `hestia` branch keeps that build's source.
