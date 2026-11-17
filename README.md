# GeneralLaymansAestheticSpyingScreen (fork)

This is a modified fork of **GeneralLaymansAestheticSpyingScreen** ("GLASS"), originally a Minecraft Forge mod
for 1.12.2. This fork ports it to **Fabric for Minecraft 1.21.8**.

GLASS turns walls of glass into screens. Name a channel on a **GLASS Terminal**, tune a **GLASS Projector** to it,
give the projector a redstone signal, and the screen spreads across the connected **GLASS Extensions** and shows
what the terminal sees. **GLASS Wireless Extensions** let a projector draw a floating screen between them.

## Original author and credit

- **Original author:** iChun
- **Original repository:** https://github.com/iChun/GeneralLaymansAestheticSpyingScreen
- **Original mod page:** http://ichun.me/mods/general-laymans-aesthetic-spying-screen/

All credit for the original mod, its design, code, textures and sounds goes to iChun.
This fork is not affiliated with or endorsed by iChun. Please report problems
with this fork here, not to the original author.

## License

The original project is licensed under the **GNU Lesser General Public License v3.0**
(see [`COPYING.LESSER`](COPYING.LESSER), which builds on the GNU GPL v3 in [`COPYING`](COPYING)).
This fork, including all modifications, is released under the same license.

What this means for this fork:

- The license files (`COPYING`, `COPYING.LESSER`) and any existing copyright notices must stay in place.
  The built jar includes both files.
- Changes made in this fork are recorded in the Git history and summarized below.
- Anyone who receives a build (`.jar`) of this fork must be able to get the matching source code;
  this public repository serves that purpose.
- Modified versions must also be released under LGPL-3.0 (or GPL-3.0).

## Changes from the original

- Added this README with attribution and license information.
- Ported from Forge 1.12.2 to Fabric 1.21.8 (Java package `ryzlar.glass`, mod ID unchanged).
- No longer needs iChunUtil. The parts that came from it were rewritten:
  - The "world portal" view is now rendered as a second world pass from the terminal into an offscreen image,
    which the glass shows in screen space (`SpyViewRenderer`).
  - Networking uses Fabric's networking API instead of iChunUtil's packet channel.
- The one GLASS block with three metadata variants is now three blocks: GLASS Extension, GLASS Projector and
  GLASS Wireless Extension. Recipes are the same.
- The server now checks GLASS screen packets: the player must be able to reach the block, may only use public
  channels or their own, and can only reorder or remove a projector's existing wireless extensions.
- The wireless order preview in the GUI is drawn as flat lines, because 1.21 GUIs no longer draw in 3D.
- Added an in-game client test (`src/gametest`).

## Requirements

- Minecraft 1.21.8
- [Fabric Loader](https://fabricmc.net/) 0.17 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)

The mod is needed on both the client and the server.

## Building

Gradle has to run on **JDK 25** (Fabric Loom 1.18 needs it); the mod itself targets Java 21.

```bash
./gradlew build
```

On Windows use `gradlew.bat build`. The built jar is placed in `build/libs/`.

To run the in-game test, which builds a screen in a test world and saves screenshots to
`build/run/clientGameTest/screenshots`:

```bash
./gradlew runClientGameTest
```
