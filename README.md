# GeneralLaymansAestheticSpyingScreen (fork)

This is a modified fork of **GeneralLaymansAestheticSpyingScreen** ("GLASS"), a Minecraft Forge mod.

## Original author and credit

- **Original author:** iChun
- **Original repository:** https://github.com/iChun/GeneralLaymansAestheticSpyingScreen
- **Original mod page:** http://ichun.me/mods/general-laymans-aesthetic-spying-screen/

All credit for the original mod, its code, textures and sounds goes to iChun.
This fork is not affiliated with or endorsed by iChun. Please report problems
with this fork here, not to the original author.

## License

The original project is licensed under the **GNU Lesser General Public License v3.0**
(see [`COPYING.LESSER`](COPYING.LESSER), which builds on the GNU GPL v3 in [`COPYING`](COPYING)).
This fork, including all modifications, is released under the same license.

What this means for this fork:

- The license files (`COPYING`, `COPYING.LESSER`) and any existing copyright notices must stay in place.
- Changes made in this fork are recorded in the Git history and summarized below.
- Anyone who receives a build (`.jar`) of this fork must be able to get the matching source code;
  this public repository serves that purpose.
- Modified versions must also be released under LGPL-3.0 (or GPL-3.0).

## Changes from the original

- Added this README with attribution and license information.

<!-- Add a line here for each notable modification made in this fork. -->

## Requirements

Same as the original mod:

- Minecraft 1.12.2
- Minecraft Forge (built against 1.12.2-14.23.0.2493)
- [iChunUtil](https://github.com/iChun/iChunUtil) (separate mod by iChun, with its own license)

## Building

```bash
./gradlew build
```

On Windows use `gradlew.bat build`. The built jar is placed in `build/libs/`.
