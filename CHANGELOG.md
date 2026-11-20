# Changelog

## 1.0.0+1.21.8 — first release of the Fabric fork

The first release of GLASS for Fabric (Minecraft 1.21.8), a fork of iChun's original Forge mod for 1.12.2.
Needs Fabric Loader 0.17+ and Fabric API, on both the client and the server.

### From the original, ported

- GLASS Terminal, Projector, Extension and Wireless Extension: walls of glass become screens showing what a
  far-away terminal sees, spreading across connected glass or floating between wireless extensions.
- No more iChunUtil: the view is a second world pass from the terminal, shown by the glass in screen space.

### New

- **Channels** with owners and passwords (salted, hashed, rate-limited): one camera per channel, search by name or
  owner, the owner can move the camera, change the password or delete the channel. Server settings in
  `config/generallaymansaestheticspyingscreen.json`.
- **Cloak**: a projector in cloak mode shows on its glass what lies behind it, hiding the glass and up to 32 blocks
  behind it. Four wireless extensions in a rectangle raise a **cloak field**: walls and a roof hiding everything inside,
  the ground underneath rebuilt from its surroundings. Cloak amplifiers raise the size limits.
- Cloaks stay hidden at every distance up to the render distance, but cost less further away (smaller, less often,
  only where they are on screen, a few per frame).
- **Scene Selector**: switches a screen between channels, with a wipe across the glass.
- **Hologram kit**: Holo Scanner, Projection Crystal, Holo Projector and Holo Emitters (classic, blue or solid look).
- **Holo Cube**: digitizes the ball of the world around it (fifteen blocks across: blocks, chest contents, signs,
  banners, beds, frames, armor stands, mobs, light, time and weather) and projects it as a walk-in miniature in a
  glowing bubble. The real world is never touched; you can stand on the scan's ground where the world is empty, walk
  through the rest, and look into the scanned chests.
- A sci-fi look for every GLASS screen, glowing edges and a nano grid on screen backs and cloak field insides, GLASS
  sounds, and Blockbench block models.
- **Sodium and Iris**: screens and cloaks are drawn with Sodium's renderer too (Sodium 0.7, Iris without a shader
  pack). With a shader pack on, or another Sodium version, they are not drawn and you are told why; nothing crashes.

### Safety and performance

- The server checks every GLASS packet: reach, channel access, sizes and indices.
- Wrong passwords and new passwords are limited per minute (also across logging out and in).
- A wireless extension only links to a projector within 64 blocks whose channel you may see.
- Holo cube scans are capped in size and sent split up, so a chest full of books cannot kick players; wiped scans
  are deleted from the world's save.
- Leaving a world or server frees every screen's image on the graphics card.

### Known limits

- Gadgets (tablet, hard light, motion sensor, map table, decoy, optical camo) are still in development and switched
  off in this release.
- With an Iris shader pack on, screens show a glow and cloaks plain glass.
