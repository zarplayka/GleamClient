<div align="center">
  <img src="src/main/resources/assets/orion-client/textures/cosmetics/orion_badge.png" width="112" alt="GleamClient">

  # GleamClient

  **Your Minecraft. Your interface. Your style.**

  A client-side Minecraft mod with a customizable interface, visual effects,
  cosmetics, and useful features for your game.

  [Download](https://github.com/zarplayka/GleamClient/releases) · [Installation](#installation) · [Features](#features) · [Controls](#controls)

  `Minecraft 26.3` &nbsp; `Fabric` &nbsp; `Java 25`
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
- **HitColor, HitSound & HitParticles** — customize the feedback from your hits.
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

1. Install **Minecraft 26.3** and [Fabric Loader](https://fabricmc.net/use/installer/).
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

The output is placed in `build/libs/`. For details about the media helpers and reproducible build verification, see [native/media/README.md](native/media/README.md).

## License

© 2026 ZARPLAY. All rights reserved. The terms for using official releases and source code are described in [LICENSE](LICENSE). Third-party components retain their respective licenses.
