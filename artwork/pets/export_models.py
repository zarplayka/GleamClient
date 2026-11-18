"""Export Minecraft model data and textures from the editable Blockbench pet projects."""
import base64
import json
from pathlib import Path

SOURCE = Path(__file__).resolve().parent
ASSETS = SOURCE.parents[1] / "src/main/resources/assets/orion-client"
PETS = ASSETS / "cosmetics/pets"
TEXTURES = ASSETS / "textures/cosmetics/pets"


def vector(values):
    return [round(value, 5) for value in values]


def export(path):
    project = json.loads(path.read_text(encoding="utf-8"))
    assert project["meta"]["model_format"] == "modded_entity"
    assert project["meta"]["box_uv"] and len(project["textures"]) == 1
    elements = {cube["uuid"]: cube for cube in project["elements"]}
    groups = {bone["uuid"]: bone for bone in project["groups"]}

    def bone(node, parent_origin):
        group = groups[node["uuid"]]
        x, y, z = group["origin"]
        px, py, pz = parent_origin
        rx, ry, rz = group["rotation"]
        cubes, children = [], []
        for entry in node["children"]:
            if isinstance(entry, dict):
                children.append(bone(entry, (x, y, z)))
                continue
            cube = elements[entry]
            assert cube["box_uv"] and not any(cube["rotation"])
            assert not cube.get("mirror_uv") and not cube.get("inflate")
            x0, y0, z0 = cube["from"]
            x1, y1, z1 = cube["to"]
            cubes.append(cube["uv_offset"] + vector([x-x1, y-y1, z0-z, x1-x0, y1-y0, z1-z0]))
        return {
            "name": group["name"], "pivot": vector([px-x, py-y, z-pz]),
            "rotation": [-rx, -ry, rz], "cubes": cubes, "children": children,
        }

    def animation(clip):
        tracks = []
        for animator in clip["animators"].values():
            channels = {}
            for key in animator["keyframes"]:
                assert key["interpolation"] == "linear" and len(key["data_points"]) == 1
                channel = key["channel"]
                data = key["data_points"][0]
                assert channel in ("position", "rotation")
                y = -data["y"] if channel == "rotation" else data["y"]
                channels.setdefault(channel, []).append([key["time"], -data["x"], y, data["z"]])
            for channel, keys in channels.items():
                tracks.append({"bone": animator["name"], "target": channel, "keys": sorted(keys)})
        return {"duration": clip["length"], "tracks": tracks}

    idle, action = project["animations"]
    assert idle["loop"] == "loop" and action["loop"] == "once"
    model = {
        "width": project["resolution"]["width"], "height": project["resolution"]["height"],
        "bones": [bone(node, (0, 0, 0)) for node in project["outliner"]],
        "idle": animation(idle), "action": animation(action),
    }
    (PETS / (path.stem + ".json")).write_text(
        json.dumps(model, separators=(",", ":")) + "\n", encoding="utf-8"
    )
    (TEXTURES / (path.stem + ".png")).write_bytes(
        base64.b64decode(project["textures"][0]["source"].removeprefix("data:image/png;base64,"), validate=True)
    )


if __name__ == "__main__":
    for directory in (PETS, TEXTURES):
        directory.mkdir(parents=True, exist_ok=True)
    for source in sorted(SOURCE.glob("*.bbmodel")):
        export(source)
