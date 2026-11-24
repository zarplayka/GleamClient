"""Export the authored Blockbench tail meshes and their idle rotation tracks."""

from base64 import b64decode
import json
from math import sqrt
from pathlib import Path

ARTWORK = Path(__file__).resolve().parent
ASSETS = ARTWORK.parents[1] / "src/main/resources/assets/orion-client"
MODELS = ASSETS / "cosmetics/tails"
TEXTURES = ASSETS / "textures/cosmetics/tails"
TAILS = ("ember_fox_tail", "verdigris_stinger", "amethyst_drake_tail")


def relative(point, origin):
    return [x - y for x, y in zip(point, origin)]


def triangle(mesh, face, keys, pivot, size):
    positions = [mesh["vertices"][key] for key in keys]
    a, b = (relative(p, positions[0]) for p in positions[1:])
    normal = [a[1]*b[2] - a[2]*b[1],
              a[2]*b[0] - a[0]*b[2],
              a[0]*b[1] - a[1]*b[0]]
    length = sqrt(sum(x*x for x in normal))
    if length < 1e-7:
        raise ValueError(f"Degenerate tail face: {mesh['name']}")
    data = [axis / length for axis in normal]
    # The renderer mirrors Y to convert Blockbench coordinates to the player model.
    for i in (0, 2, 1):
        data.extend(relative(positions[i], pivot))
        data.extend(uv / extent for uv, extent in zip(face["uv"][keys[i]], size))
    return [round(value, 6) for value in data]


def export(name):
    project = json.loads((ARTWORK / f"{name}.bbmodel").read_text(encoding="utf-8"))
    if project["meta"]["model_format"] != "free" or len(project["textures"]) != 1:
        raise ValueError(f"Expected a Generic Model with one texture: {name}")
    texture, = project["textures"]
    size = (texture["uv_width"], texture["uv_height"])
    (clip,) = [animation for animation in project["animations"] if animation["name"] == "idle"]
    meshes = {mesh["uuid"]: mesh for mesh in project["elements"]}
    groups = {group["uuid"]: group for group in project["groups"]}

    def bone(node, parent_pivot):
        group = groups[node["uuid"]]
        pivot = group["origin"]
        if any(group["rotation"]):
            raise ValueError(f"Expected bind rotations in vertex coordinates: {group['name']}")
        track = clip["animators"][node["uuid"]]["keyframes"]
        rotations = []
        for i, key in enumerate(track):
            if (key["channel"] != "rotation" or key["interpolation"] != "catmullrom"
                    or abs(key["time"] - i * clip["length"] / (len(track) - 1)) > 1e-6):
                raise ValueError(f"Expected uniform Catmull-Rom rotations: {group['name']}")
            value, = key["data_points"]
            rotations.append([value[axis] for axis in "xyz"])
        if rotations[0] != rotations[-1]:
            raise ValueError(f"The idle cycle must close: {group['name']}")

        faces = []
        children = []
        for child in node["children"]:
            if isinstance(child, dict):
                children.append(bone(child, pivot))
                continue
            mesh = meshes[child]
            if mesh["type"] != "mesh" or any(mesh["origin"]) or any(mesh["rotation"]):
                raise ValueError(f"Expected mesh vertices in world coordinates: {mesh['name']}")
            for face in mesh["faces"].values():
                keys = face["vertices"]
                if len(keys) not in (3, 4) or face["texture"] != 0:
                    raise ValueError(f"Expected textured triangles and quads: {mesh['name']}")
                for index in range(1, len(keys) - 1):
                    faces.extend(triangle(mesh, face, [keys[0], keys[index], keys[index+1]], pivot, size))
        return {"pivot": relative(pivot, parent_pivot), "rotation": rotations,
                "triangles": faces, "children": children}

    (root,) = project["outliner"]
    model = {"duration": clip["length"], "root": bone(root, (0, 0, 0))}
    (MODELS / f"{name}.json").write_text(json.dumps(model, separators=(",", ":")) + "\n", encoding="utf-8")
    png = b64decode(texture["source"].removeprefix("data:image/png;base64,"), validate=True)
    (TEXTURES / f"{name}.png").write_bytes(png)


if __name__ == "__main__":
    for path in (MODELS, TEXTURES):
        path.mkdir(parents=True, exist_ok=True)
    for tail in TAILS:
        export(tail)