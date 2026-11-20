"""Writes the GLASS Tome's pages (assets/<mod>/tome/*.json) from the text below: python tools/tome_pages.py

A page: a title, the items it is about (the tome's look-up key opens it from them), an optional 3D scene, and its body:
paragraphs (with [[page|text]] links) and {"recipe": "<item>"} blocks."""
import json
import os

MOD = "generallaymansaestheticspyingscreen"
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", MOD, "tome")

CHAPTERS = [
    ("Getting started", ["welcome"]),
    ("Screens", ["terminal", "channels", "projector", "extensions", "wireless", "scene_selector"]),
    ("Cloak", ["cloak", "cloak_field", "amplifier"]),
    ("Hologram kit", ["holo_scanner", "holo_projector"]),
    ("Holo Cube", ["holo_cube", "holo_cube_bubble"]),
]

PAGES = {
    "welcome": dict(
        title="Welcome to GLASS",
        items=["glass_tome"],
        scene="screen",
        body=[
            "GLASS turns walls of glass into screens. A [[terminal|GLASS Terminal]] is a camera; a [[projector|GLASS Projector]] "
            "in a wall of [[extensions|GLASS Extensions]] shows what it sees, as soon as it gets a redstone signal.",
            "The same glass can also [[cloak|cloak]] what is behind it, so your base disappears, and the hologram kit and the "
            "[[holo_cube|Holo Cube]] capture places and project them back.",
            "Use the index on the left, or search. Pressing the look-up key (G) over a GLASS item in your inventory opens its page. "
            "Drag a scene to turn it.",
            {"recipe": "glass_tome"},
        ]),
    "terminal": dict(
        title="GLASS Terminal",
        items=["glass_terminal"],
        scene="screen",
        body=[
            "The terminal is the camera. It looks towards where you stood when you placed it (place it from above or below "
            "to make it look down or up).",
            "Right-click it to make a [[channels|channel]] with this terminal as its camera: give the channel a name and a "
            "password. A channel has one camera; the owner can move it to another terminal later.",
            "Breaking the terminal takes the channel's camera offline, but the channel itself stays. It is as tough as obsidian.",
            {"recipe": "glass_terminal"},
        ]),
    "channels": dict(
        title="Channels",
        items=[],
        scene=None,
        body=[
            "Every screen shows a channel. Channel names are unique on the server, and each belongs to the player who made it.",
            "Your own channels are always yours to use. Someone else's needs its password once: after that you can show it on "
            "your projectors until the owner changes the password.",
            "The owner can change the password, move the camera to another terminal, or delete the channel. Passwords are stored "
            "hashed, and too many wrong guesses lock you out for a minute.",
            "Server owners can set how many channels a player may own in config/" + MOD + ".json.",
        ]),
    "projector": dict(
        title="GLASS Projector",
        items=["glass_projector"],
        scene="screen",
        body=[
            "Put the projector in a wall of [[extensions|GLASS Extensions]] (or on its own) and right-click it to choose a "
            "[[channels|channel]]. Give it a redstone signal and the screen builds itself out of the glass, starting at the projector.",
            "Switching to another channel while it is on wipes the new picture over the old one. Taking the signal away switches it off.",
            "The same screen also holds the [[cloak|cloak]] settings.",
            {"recipe": "glass_projector"},
        ]),
    "extensions": dict(
        title="GLASS Extensions",
        items=["glass_extension"],
        scene="screen",
        body=[
            "Extensions are the glass the picture spreads over. Any extensions touching the projector, or touching each other, "
            "become one screen, up to 40 blocks from the projector.",
            "Punch a screen and it ripples. Seen from behind, a screen shows its glowing back instead of the picture.",
            {"recipe": "glass_extension"},
        ]),
    "wireless": dict(
        title="Wireless Extensions",
        items=["glass_wireless_extension"],
        scene="wireless",
        body=[
            "Wireless extensions make a floating screen between them, without any glass in between. Right-click one to link it "
            "to a projector within 64 blocks (only to a projector whose channel you may see).",
            "The projector's WIRELESS screen shows the extensions in order and lets you reorder or remove them while it is off. "
            "Four of them in the corners of a rectangle can also raise a [[cloak_field|cloak field]].",
            {"recipe": "glass_wireless_extension"},
        ]),
    "scene_selector": dict(
        title="Scene Selector",
        items=["glass_scene_selector"],
        scene="selector",
        body=[
            "The scene selector switches a screen between channels. Place it within 16 blocks of a projector, right-click it, "
            "link it, and add up to 8 scenes.",
            "Pick a scene on its panel, or give it a redstone pulse to show the next one. The new picture wipes over the old one.",
            {"recipe": "glass_scene_selector"},
        ]),
    "cloak": dict(
        title="Cloak",
        items=["glass_projector"],
        scene="cloak",
        body=[
            "In cloak mode a projector needs no channel: its glass shows what lies behind it, as if the blocks right behind the "
            "glass were not there. Turn it on in the projector's screen and set how many blocks deep it hides.",
            "Give it a redstone signal and the glass, and whatever is behind it, disappears from every side you can see it from. "
            "The ground under the hidden part is rebuilt from its surroundings.",
            "A cloak has limits (how much glass, how deep); [[amplifier|cloak amplifiers]] raise them.",
        ]),
    "cloak_field": dict(
        title="Cloak field",
        items=["glass_wireless_extension"],
        scene="field",
        body=[
            "Put four [[wireless|wireless extensions]] in the corners of a rectangle on one level (at least two blocks apart each "
            "way) and link all four to a projector in cloak mode. Turn FIELD on and set its height.",
            "Powered, walls and a roof rise out of the ground between them and hide everything inside, from every side and from "
            "above. Step inside and you see the inside of the field as glowing edges.",
        ]),
    "amplifier": dict(
        title="Cloak Amplifier",
        items=["cloak_amplifier"],
        scene="amplifier",
        body=[
            "A cloak may only be so big. Each amplifier touching the projector (up to two) raises its limits:",
            "No amplifier: fields up to 32x32 and 16 high, 256 glass blocks, 16 deep. One: 48x48, 24 high, 576 glass, 24 deep. "
            "Two: 64x64, 32 high, 1024 glass, 32 deep.",
            "Take an amplifier away from a running cloak that no longer fits and it switches off.",
            {"recipe": "cloak_amplifier"},
        ]),
    "holo_scanner": dict(
        title="Holo Scanner",
        items=["holo_scanner", "projection_crystal"],
        scene="hologram",
        body=[
            "The scanner captures an area of blocks into a Projection Crystal. Right-click it to set where the area is (up to 32 "
            "blocks away) and how big (up to 32 each way), and give the projection a name.",
            "Each scan costs one amethyst shard and gives you a crystal. Put it in a [[holo_projector|Holo Projector]] to see it.",
            {"recipe": "holo_scanner"},
        ]),
    "holo_projector": dict(
        title="Holo Projector",
        items=["holo_projector", "holo_emitter"],
        scene="hologram",
        body=[
            "Use a Projection Crystal on the projector to put it in (sneak + right-click with an empty hand takes it out). "
            "Powered by redstone, it projects the crystal's blocks above it.",
            "Each side of a hologram only shows where the projector or an emitter can see it, so put Holo Emitters around it "
            "(up to 16, within 16 blocks) and link them from the projector's screen.",
            "The screen also sets the look (classic, blue or solid), the scale and the rotation.",
            {"recipe": "holo_projector"},
            {"recipe": "holo_emitter"},
        ]),
    "holo_cube": dict(
        title="Scanning",
        items=["holo_cube"],
        scene="cube_scan",
        body=[
            "Set the cube down and right-click it: it rises, a grid grows out of it, and the ball of the world around it, fifteen "
            "blocks across, is digitized: the blocks, what is in the chests, signs, banners and beds, frames and armor stands, "
            "the animals and mobs, and the time and weather.",
            "Sneak + right-click (or hit it) to pick it up with its scan. Sneak + right-click in the air with it wipes the scan.",
            {"recipe": "holo_cube"},
        ]),
    "holo_cube_bubble": dict(
        title="The bubble",
        items=["holo_cube"],
        scene="cube_bubble",
        body=[
            "Set a scanned cube down anywhere and right-click it: a glowing bubble grows out of it with the scan inside, shrunk "
            "to ten blocks across. From outside you see it in blue; step in and it has its real colours, under the scanned sky.",
            "It is only a picture over the real world, which is never touched. Where the real world is empty you can stand on the "
            "scan's ground; you walk straight through the rest. Use a scanned chest to look inside it.",
            "Right-click again to switch it off.",
        ]),
}


def main():
    os.makedirs(os.path.join(OUT, "pages"), exist_ok=True)
    index = {"chapters": [{"title": t, "pages": p} for t, p in CHAPTERS]}
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(index, f, indent=2)
        f.write("\n")
    for page in [p for _, ps in CHAPTERS for p in ps]:
        data = dict(PAGES[page])
        data["items"] = [MOD + ":" + i for i in data["items"]]
        data["body"] = [b if isinstance(b, str) else {"recipe": MOD + ":" + b["recipe"]} for b in data["body"]]
        if data["scene"] is None:
            del data["scene"]
        with open(os.path.join(OUT, "pages", page + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
    print("wrote", sum(len(p) for _, p in CHAPTERS), "pages")


if __name__ == "__main__":
    main()
