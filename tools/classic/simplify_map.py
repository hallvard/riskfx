"""Simplifies the shapes of the classic map, which are traced from a drawing and have many more points than can be seen: a point
is left out if it changes the outline by less than the tolerance, which is under a pixel at the size of the whole map.
The curves are cut in line pieces, with their end points, which is how the drawing was traced, so the outlines look the same.

The original is in map-original.json, which this reads, and the simplified map is written to the resources of the application:

    python3 -I tools/classic/simplify_map.py
"""
import json
import math
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "ui" / "src" / "main" / "resources" / "map.json"
TOLERANCE = 0.6  # in the units of the drawing


def rings(path):
    """The rings of a path of M, L, C and Z, with only the end point of each curve, as lists of points."""
    result, current = [], None
    tokens = path.split()
    i = 0
    while i < len(tokens):
        token = tokens[i].upper()
        i += 1
        if token == "Z":
            if current:
                result.append(current)
            current = None
            continue
        count = 3 if token == "C" else 1
        points = [tuple(float(v) for v in tokens[i + k].split(",")) for k in range(count)]
        i += count
        if token == "M":
            if current:
                result.append(current)
            current = [points[0]]
        elif token in ("L", "C"):
            current.append(points[-1])
        else:
            raise ValueError("The path command " + token + " is not supported")
    if current:
        result.append(current)
    return result


def simplified(ring):
    """Douglas-Peucker on a ring, cut in two at the point farthest from the first, so both ends are kept."""
    if len(ring) > 1 and ring[0] == ring[-1]:
        ring = ring[:-1]
    far = max(range(len(ring)), key=lambda i: math.dist(ring[0], ring[i]))
    result = []
    for chain in (ring[:far + 1], ring[far:] + [ring[0]]):
        keep = [False] * len(chain)
        keep[0] = keep[-1] = True
        stack = [(0, len(chain) - 1)]
        while stack:
            first, last = stack.pop()
            (x1, y1), (x2, y2) = chain[first], chain[last]
            length = math.hypot(x2 - x1, y2 - y1)
            worst, worst_distance = -1, TOLERANCE
            for i in range(first + 1, last):
                x, y = chain[i]
                distance = math.hypot(x - x1, y - y1) if length == 0 else abs((x2 - x1) * (y1 - y) - (x1 - x) * (y2 - y1)) / length
                if distance > worst_distance:
                    worst, worst_distance = i, distance
            if worst >= 0:
                keep[worst] = True
                stack.append((first, worst))
                stack.append((worst, last))
        result.extend(p for p, kept in zip(chain[:-1], keep) if kept)
    return result if len(result) >= 3 else ring


def main():
    shapes = json.loads((HERE / "map-original.json").read_text(encoding="utf8"))
    before = after = 0
    for shape in shapes:
        original = rings(shape["path"])
        simple = [simplified(ring) for ring in original]
        before += sum(len(r) for r in original)
        after += sum(len(r) for r in simple)
        shape["path"] = " ".join("M " + " L ".join("%.1f,%.1f" % p for p in ring) + " Z" for ring in simple)
    OUT.write_text(json.dumps(shapes, indent=1) + "\n", encoding="utf8")
    print("points: %d, simplified to %d (%d shapes)" % (before, after, len(shapes)))


if __name__ == "__main__":
    main()
