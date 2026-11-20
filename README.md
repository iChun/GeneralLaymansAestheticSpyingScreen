# GLASS for Fabric

**General Laymans Aesthetic Spying Screen**, rebuilt for Fabric and Minecraft 1.21.8 by **ryzlar**,
and grown far beyond the original.

Turn walls of glass into live screens of far-away places, make your base vanish behind a cloak, capture places as
holograms, and walk into a scanned memory of a spot. Everything is explained in the game itself, in the **GLASS Tome**.

![Minecraft 1.21.8](https://img.shields.io/badge/Minecraft-1.21.8-3c8527) ![Fabric](https://img.shields.io/badge/loader-Fabric-dbd0b4)
![License LGPL-3.0](https://img.shields.io/badge/license-LGPL--3.0-blue) ![Sodium](https://img.shields.io/badge/works%20with-Sodium-5aa3d8)

> GLASS was created by **iChun** for Forge and Minecraft 1.12.2. This is a fork: a Fabric port with new features.
> It is not made or endorsed by iChun; please report problems with it here, not to iChun. See
> [Credits and license](#credits-and-license).

## What it does

### Screens and channels
- A **GLASS Terminal** is a camera. Make a **channel** at it: a name and a password.
- Put a **GLASS Projector** in a wall of **GLASS Extensions**, choose a channel, give it a redstone signal, and the
  screen builds itself out of the glass, pixel by pixel, showing what the terminal sees, live.
- **Wireless Extensions** make a floating screen between them, without glass in between.
- The **Scene Selector** switches a screen between up to 8 channels, by hand or with a redstone pulse, wiping the new
  picture over the old one.
- Channels belong to their maker. Other players need the password once; the owner can change it, move the camera or
  delete the channel.

### Cloak
- In **cloak mode** a projector's glass shows what lies behind it: the glass, and up to 32 blocks behind it, disappear.
- Four wireless extensions in the corners of a rectangle raise a **cloak field**: walls and a roof that hide
  everything inside, from every side and from above. The ground underneath is rebuilt from its surroundings.
- **Cloak Amplifiers** next to the projector let a cloak grow bigger (up to 64x64 and 32 high).
- Cloaks stay hidden at any distance up to your render distance, and cost less the further away they are.

### Hologram kit
- The **Holo Scanner** captures an area of up to 32x32x32 blocks into a **Projection Crystal** (one amethyst shard a scan).
- The **Holo Projector** projects it as a hologram (classic, blue or solid look, any scale, any rotation);
  **Holo Emitters** around it light up its sides.

### Holo Cube
- Set the cube down and it **digitizes** the ball of the world around it, fifteen blocks across: the blocks, what is in
  the chests, signs and banners, frames and armor stands, the animals and mobs, and even the time and weather.
- Set it down anywhere else and a glowing **bubble** grows out of it with the scan inside, as a walk-in miniature
  under the scanned sky. Stand on its ground, walk through the rest, look into its chests. The real world is never
  touched.

### GLASS Tome
- An in-game guide to all of the above, given to every player once, the first time they join. Live 3D scenes show how
  each thing is set up and what it does (drag to turn them), with recipes and links between pages.
- Press **G** over a GLASS item in your inventory to open its page.

All GLASS items are in their own creative tab.

## Recipes

| Item | Made from |
|---|---|
| GLASS Tome | book, glass, redstone (any shape) |
| GLASS Terminal | obsidian, glass, eye of ender, redstone |
| GLASS Projector | glass, ender pearl, beacon, daylight detector |
| GLASS Extension | glass, gold ingot, ender pearl, redstone |
| GLASS Wireless Extension | GLASS Extension + end crystal (any shape) |
| Scene Selector | stone buttons, glass panes, comparator, polished blackstone |
| Cloak Amplifier | amethyst blocks, diamond, GLASS Extensions, eye of ender, iron blocks, redstone block |
| Holo Scanner | amethyst shard, glass, observer, iron ingots |
| Holo Projector | glass, amethyst shards, diamond, iron ingots |
| Holo Emitter (x4) | amethyst shard, end rod, iron nugget |
| Holo Cube | iron ingots, amethyst shards, glass, observer |

The exact grids are in the GLASS Tome (and in any recipe viewer).

## Installing

- Minecraft **1.21.8** with [Fabric Loader](https://fabricmc.net/) 0.17 or newer and
  [Fabric API](https://modrinth.com/mod/fabric-api).
- Needed on **both the client and the server**.
- **Sodium** 0.7 works, screens and cloaks included. **Iris** works too, but while a shader pack is on, screens show a
  glow and cloaks plain glass (you are told when you join); everything else works.

### Server settings

`config/generallaymansaestheticspyingscreen.json`:

| Setting | Default | What it does |
|---|---|---|
| `maxChannelsPerPlayer` | 10 | Channels one player may own |
| `failedPasswordsPerMinute` | 5 | Wrong channel passwords before a player has to wait a minute |
| `newPasswordsPerMinute` | 5 | New channels and changed passwords a player may make per minute |

## Credits and license

**GLASS (General Laymans Aesthetic Spying Screen)** was created by **iChun**, who came up with the mod, its idea of
glass screens fed by terminals, and wrote the original for Forge 1.12.2.

- Original repository: https://github.com/iChun/GeneralLaymansAestheticSpyingScreen
- Original mod page: http://ichun.me/mods/general-laymans-aesthetic-spying-screen/

This fork is by **ryzlar**: the Fabric 1.21.8 port and everything added since (see below). From the original it keeps
the mod's name and idea, the design of its screens, terminals and wireless extensions, parts of its code (rewritten
for Fabric), its ambience sound and two of its GUI textures. The other textures, the block models (sources in
`blockbench/`), the other sounds, the icon and the tome were made for this fork.

GLASS is licensed under the **GNU Lesser General Public License v3.0**, and so is this fork, with all its changes:

- The license texts are in [`COPYING.LESSER`](COPYING.LESSER) and [`COPYING`](COPYING) (the GPL v3 it builds on),
  and both ship inside the mod's jar.
- The full source of this fork is in this repository; every change is in its Git history and summed up below and in
  [`CHANGELOG.md`](CHANGELOG.md).
- Modified versions of it must stay under the LGPL v3 (or the GPL v3) and keep these notices.

Built with [Fabric API](https://github.com/FabricMC/fabric) (Apache 2.0), which is not included in the jar.
[Sodium](https://github.com/CaffeineMC/sodium) and [Iris](https://github.com/IrisShaders/Iris) are supported but not
included.

## Changes from the original

- Ported from Forge 1.12.2 to Fabric 1.21.8 (Java package `ryzlar.glass`, mod ID unchanged). No longer needs
  iChunUtil: a terminal's view is drawn as a second pass of the world into an image that the glass shows, with Sodium
  as well as vanilla rendering, and networking uses Fabric's API.
- The one GLASS block with three variants is now three blocks (Extension, Projector, Wireless Extension), with the
  same recipes.
- Channels with owners and salted, hashed passwords replace public and per-player channels; the server checks every
  GLASS packet (reach, access, sizes) and limits password guesses.
- New: cloak mode and cloak fields with amplifiers, the scene selector, the hologram kit, the holo cube, the GLASS
  Tome, a sci-fi look for every GLASS screen, screen backs, new sounds, Blockbench block models and a creative tab.
- New in-game client tests and dedicated-server tests (`src/gametest`).

## Building

Gradle has to run on **JDK 25** (Fabric Loom 1.18 needs it); the mod itself targets Java 21.

```bash
./gradlew build
```

On Windows use `gradlew.bat build`. The jar ends up in `build/libs/`.

Tests:

- `./gradlew runGameTest`: tests on a dedicated server (the mod without any client code).
- `./gradlew runClientGameTest`: builds screens, cloaks and holograms in a test world and checks them, with
  screenshots in `build/run/clientGameTest/screenshots`. `GLASS_TEST_ONLY=cloak` or `tome` runs only that part;
  `GLASS_TEST_ONLY=stress` runs the stress test (many cloaks, a dedicated server joined twice, a scan bigger than one
  packet).
- `./gradlew prodServer` / `./gradlew prodClient`: the built jar as players get it, on a real server or client.
- Add `-PwithSodium` or `-PwithIris` to a client run to try it with Sodium or Iris.

The GLASS Tome's pages are written by `tools/tome_pages.py`, the icon and the tome's texture by
`tools/make_icon.py` and `tools/make_tome_texture.py`.
