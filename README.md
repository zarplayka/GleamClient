<div align="center">
  <img src="src/main/resources/assets/orion-client/textures/cosmetics/orion_badge.png" width="112" alt="GleamClient">

  # GleamClient

  **Your Minecraft. Your interface. Your style.**

  A client-side Minecraft mod with a customizable interface, visual effects,
  cosmetics, and useful features for your game.

  [Download](https://github.com/zarplayka/GleamClient/releases) · [Installation](#installation) · [Features](#features) · [Controls](#controls)

  `Minecraft 1.21.11 / 26.1.2 / 26.2 / 26.3` &nbsp; `Fabric` &nbsp; `Java 21 / 25`
</div>

---

## Features

### Interface & convenience

- **ClickGUI** — browse modules with search, favorites, and multiple themes, including Paradox.
- **HUD** — see client information, keystrokes, inventory contents, and nearby players.
- **Zoom** — zoom in smoothly and adjust the zoom temporarily with the mouse wheel.
- **ChatFilter & ChatHighlight** — hide or highlight messages using your own keywords.
- **ProtectName & LockSlot** — hide your username and protect selected inventory slots.
- **MediaPlayer & Discord** — see your music in a widget and share your activity on Discord.

### Combat & effects

- **KillEffect** — animated 3D effects and sounds after a kill, with configurable targets.
- **TargetESP** — add a visual effect to your target.
- **HitColor & HitParticles** — customize the feedback from your hits.
- **DamageIndicators & ArmorIndicator** — display damage and other players' armor.
- **PvpSafe** — get a warning when trying to leave during combat.

### World & exploration

- **Waypoints** — create and edit markers in the menu; names, colors, and coordinates are saved.
- **DeathPoint** — mark your latest death location.
- **JumpCircle, Breadcrumbs & ProjectileTrail** — add jump rings and movement trails.
- **WorldChanger, CustomTime & WorldParticles** — customize the atmosphere of your world.
- **BlockHighlight & ItemPhysics** — highlight blocks and change the appearance of dropped items.

### Cosmetics & mini-games

Try on a **halo**, a **hat** or **angel and dragon wings** in the wardrobe before equipping them. Other players using a compatible version of GleamClient can see synchronized cosmetics. The client also includes **basketball** and **football**.

### Friends & chat

Sign in with your GleamClient account to manage friend requests and open private conversations with accepted friends.

> Cosmetic synchronization requires access to its online service. Availability may vary by region and network.

## Installation

1. Install **Minecraft 1.21.11, 26.1.2, 26.2 or 26.3** and [Fabric Loader](https://fabricmc.net/use/installer/).
2. Download a compatible version of [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods` folder.
3. Download the official GleamClient JAR from [Releases](https://github.com/zarplayka/GleamClient/releases) and place it in the same folder.
4. Launch your Fabric profile and press **Right Shift** to open the GleamClient menu.

Check the Minecraft version in the release description: a published release may be older than the current repository.

## Controls

| Action | Default control |
| --- | --- |
| Open ClickGUI | **Right Shift** |
| Zoom in | Hold **C** |
| Adjust zoom temporarily | Scroll while holding the zoom key |
| Toggle a module | Left-click the module |
| Open module settings | Right-click the module |

Keys can be changed in Minecraft's controls or in the corresponding module's settings.

## Building from source

The standard Windows build requires **Java 25** and **.NET SDK 8.0.421**:

```powershell
.\gradlew.bat build
```

Stonecutter 0.9.8 builds **every supported Minecraft version**, regardless of the active IDE version. Release and source JARs are collected in `build/libs/` with explicit game-version names:

- `gleamclient-0.2.3-mc1.21.11.jar` (Java 21 or newer)
- `gleamclient-0.2.3-mc26.1.2.jar` (Java 25)
- `gleamclient-0.2.3-mc26.2.jar`
- `gleamclient-0.2.3-mc26.3.jar`
- Matching `gleamclient-0.2.3-mc<version>-sources.jar` for each target.

`gleamclient.jar` is a compatibility alias for the **26.3** release. `build/supported-versions.json` records the verified release artifacts. Each JAR requires its exact Minecraft version and the matching Fabric API.

To build or launch a single target:

```powershell
.\gradlew.bat :1.21.11:build
.\gradlew.bat :26.2:build
.\gradlew.bat :26.3:build
.\gradlew.bat :1.21.11:runClient
.\gradlew.bat :26.2:runClient
.\gradlew.bat :26.3:runClient
```

Individual builds write to `versions/<minecraft>/build/libs/`. The root `runClient` launches 26.3. Stonecutter's `Set active project to 26.2` / `Set active project to 26.3` tasks switch the active IDE sources; 26.3 is the version used for source control. Keep shared changes in `src/` and express API differences with Stonecutter conditions. Shaders are processed with the same conditions.

To add support for another Minecraft version, register it alongside the existing versions in `settings.gradle`, create `versions/<minecraft>/gradle.properties` with `minecraft_version` and `fabric_version`, and adapt any changed APIs. The root build and CI discover the added target automatically. Registering a version alone does not port incompatible code.

Fabric Loader 0.19.3 remains supported. Builds use Loader 0.19.5, while the Java compile classpath pins Fabric Mixin 0.17.3 so injector annotations also work with the MixinExtras 0.5.4 bundled in older launch profiles. The mixin audit rejects incompatible `@Redirect.at` array encoding. Runtime Mixin libraries are supplied by the launcher and are not bundled or replaced by the mod.

Build with Java 25, Gradle 9.6 and Loom 1.17.21. The 1.21.11 target emits Java 21 bytecode and uses the remapping Loom plugin with official Mojang mappings; its release JAR is `remapJar`, while 26.x uses `jar`. Use the collected release from root `build/libs`, not a development JAR. The 1.21.11 dependency is Fabric API 0.141.6+1.21.11. Media helpers are built once for all targets, with their sources and checksums included in every release. For helper prerequisites and reproducible build verification, see [native/media/README.md](native/media/README.md).

## License

© 2026 ZARPLAY. All rights reserved. The terms for using official releases and source code are described in [LICENSE](LICENSE). Third-party components retain their respective licenses.
