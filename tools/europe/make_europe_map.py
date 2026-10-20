"""Writes the files of the Europe map: the continents and borders for the game, and the shapes for the user interface.

The shapes are cut from europe.svg, a map of the countries of Europe from SimpleMaps (https://simplemaps.com/resources/svg-europe),
which is free to use under the conditions on https://simplemaps.com/license: check them before publishing anything that uses it.
The map has one shape for each country, so a few are put together or divided here: Kosovo is added to Serbia, Corsica and
Sardinia are one territory, and the United Kingdom is divided into Scotland, England and Wales, with Northern Ireland going to
Ireland. Countries that are not territories (Belarus, Turkey, Switzerland, Luxembourg, Liechtenstein, Andorra) are in the map, but in no
region, so the user interface shows them as inactive, in grey.

The borders of the game are found from the shapes: two territories border each other if their shapes meet, that is, have at least
two corners in common (the shapes come from borders that are drawn once, so the corners agree). Only the connections across sea are
written by hand, in SEA_LINKS.

The label of a territory (its number of armies, with its name below) is placed automatically, and can be moved by hand: in the
application, choose Edit labels in the settings, drag the labels, and switch it off again to save the displacements in
label-offsets.json next to this script, which is read here and added to the automatic places.

Edit the lists below and run it again (it reads the svg next to it):

    python3 -I tools/europe/make_europe_map.py
"""
import json
import math
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

# the countries of the svg that make up each territory, and the rest of the lists are below
SIMPLE = {
    "Iceland": "IS", "Norway": "NO", "Sweden": "SE", "Finland": "FI", "Denmark": "DK", "Estonia": "EE", "Latvia": "LV",
    "Lithuania": "LT", "Portugal": "PT", "Spain": "ES", "Belgium": "BE", "Netherlands": "NL", "Germany": "DE", "Poland": "PL",
    "Czechia": "CZ", "Slovakia": "SK", "Austria": "AT", "Hungary": "HU", "Ukraine": "UA", "Moldova": "MD", "Romania": "RO",
    "Bulgaria": "BG", "Slovenia": "SI", "Croatia": "HR", "Bosnia": "BA", "Montenegro": "ME", "North Macedonia": "MK",
    "Albania": "AL", "Greece": "GR",
}
# shapes that are on the map but not in the game: they are in no region, and the user interface shows them as inactive
INACTIVE = {"Belarus": "BY", "Turkey": "TR", "Switzerland": "CH", "Luxembourg": "LU", "Liechtenstein": "LI", "Andorra": "AD"}

CONTINENTS = [
    ("British Isles", 2, ["Ireland", "Scotland", "England", "Wales"]),
    ("Norden", 3, ["Iceland", "Norway", "Sweden", "Finland", "Denmark"]),
    ("Baltics", 2, ["Estonia", "Latvia", "Lithuania"]),
    ("Western Europe", 4, ["Portugal", "Spain", "France", "Belgium", "Netherlands", "Germany"]),
    ("Central and Eastern Europe", 6, ["Poland", "Czechia", "Slovakia", "Austria", "Hungary", "Ukraine", "Moldova", "Romania", "Bulgaria"]),
    ("Southern Europe", 6, ["Italy", "Slovenia", "Croatia", "Bosnia", "Montenegro", "Serbia", "North Macedonia", "Albania", "Greece",
                            "Mediterranean Isles"]),
]

# the connections across sea, which are the only borders to write: those over land are found from the shapes, where they meet
SEA_LINKS = """
Ireland-Scotland Ireland-Wales Scotland-Iceland Scotland-Norway England-France England-Belgium England-Netherlands
Iceland-Norway Norway-Denmark Sweden-Denmark Finland-Estonia Sweden-Latvia Italy-Croatia Italy-Albania
Mediterranean_Isles-France Mediterranean_Isles-Spain Mediterranean_Isles-Italy
"""

SIMPLIFY_TOLERANCE = 0.8  # in the units of the svg (about a pixel on the screen at the size of the whole map): points that change the outline less are left out
TOO_SMALL = 8.0  # the shapes of islands smaller than this are left out, since they are specks on the map
MARGIN = 10.0
HIDE_TURKEY_EAST_OF = 800.0  # of Turkey, only the part next to Europe is shown


# the svg

def parse_path(d):
    """The rings (lists of points) of an svg path with the commands M, m, L, l, H, h, V, v, Z and z."""
    tokens = re.findall(r"[A-Za-z]|-?\d*\.?\d+(?:e-?\d+)?", d)
    rings, current, command, i = [], None, None, 0
    x = y = start_x = start_y = 0.0
    while i < len(tokens):
        if tokens[i].isalpha():
            command = tokens[i]
            i += 1
            if command in "Zz":
                if current:
                    rings.append(current)
                current = None
                x, y = start_x, start_y
                continue
        if command in "Mm":
            nx, ny = float(tokens[i]), float(tokens[i + 1])
            i += 2
            x, y = (x + nx, y + ny) if command == "m" else (nx, ny)
            start_x, start_y = x, y
            if current:
                rings.append(current)
            current = [(x, y)]
            command = "l" if command == "m" else "L"
        elif command in "Ll":
            nx, ny = float(tokens[i]), float(tokens[i + 1])
            i += 2
            x, y = (x + nx, y + ny) if command == "l" else (nx, ny)
            current.append((x, y))
        elif command in "Hh":
            nx = float(tokens[i])
            i += 1
            x = x + nx if command == "h" else nx
            current.append((x, y))
        elif command in "Vv":
            ny = float(tokens[i])
            i += 1
            y = y + ny if command == "v" else ny
            current.append((x, y))
        else:
            raise ValueError("The path command " + command + " is not supported")
    if current:
        rings.append(current)
    return rings


def load_countries():
    text = (HERE / "europe.svg").read_text(encoding="utf8")
    countries = {}
    for match in re.finditer(r'<path\s+d="([^"]+)"([^>]*)>', text):
        attributes = dict(re.findall(r'(\w+)="([^"]*)"', match.group(2)))
        countries.setdefault(attributes["id"], []).extend(parse_path(match.group(1)))
    return countries


# geometry

def area(ring):
    return sum(ring[i][0] * ring[(i + 1) % len(ring)][1] - ring[(i + 1) % len(ring)][0] * ring[i][1] for i in range(len(ring))) / 2


def centroid(ring):
    return (sum(p[0] for p in ring) / len(ring), sum(p[1] for p in ring) / len(ring))


def contains(ring, point):
    x, y = point
    inside = False
    for i in range(len(ring)):
        (x1, y1), (x2, y2) = ring[i], ring[(i + 1) % len(ring)]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            inside = not inside
    return inside


def split(ring, start, end, chord):
    """Divides a ring in two at the vertices nearest to start and end, which are joined by the chord (points between them).
    Both pieces get the same points on the chord, so they fit. Returns the piece from start to end, and the piece from end to start."""
    first = min(range(len(ring)), key=lambda k: math.dist(ring[k], start))
    second = min(range(len(ring)), key=lambda k: math.dist(ring[k], end))
    n = len(ring)
    forward = [ring[(first + k) % n] for k in range((second - first) % n + 1)]
    backward = [ring[(second + k) % n] for k in range((first - second) % n + 1)]
    return forward + list(reversed(chord)), backward + list(chord)


def keep_the_piece_with(pieces, probe):
    chosen = [piece for piece in pieces if contains(piece, probe)]
    assert len(chosen) == 1, f"the point {probe} is in {len(chosen)} of the pieces"
    return chosen[0], [piece for piece in pieces if piece is not chosen[0]][0]


def significant(rings):
    return [ring for ring in rings if abs(area(ring)) >= TOO_SMALL]


def merge_rings(rings, digits=1):
    """Joins rings that share a stretch of border into one, such as Kosovo and the rest of Serbia, so there is no line between them.
    The shared edges are those that two rings have, in opposite directions: they are removed, and what is left is chained into rings."""
    keys = [[(round(x, digits), round(y, digits)) for x, y in ring] for ring in rings]
    edges = {}
    for ring in keys:
        for i, start in enumerate(ring):
            end = ring[(i + 1) % len(ring)]
            if start != end:
                edges[(start, end)] = edges.get((start, end), 0) + 1
    outgoing = {}
    for (start, end), count in edges.items():
        if (end, start) not in edges:
            outgoing.setdefault(start, []).append(end)
    if len(outgoing) == sum(len(ring) for ring in keys):
        return rings  # nothing is shared
    merged = []
    while outgoing:
        start = next(iter(outgoing))
        ring, current = [start], start
        while True:
            following = outgoing[current].pop()
            if not outgoing[current]:
                del outgoing[current]
            if following == start:
                break
            ring.append(following)
            current = following
        merged.append(ring)
    return merged


NAME_ROW_OFFSET = 14.0  # how far below the number of armies the name is, in the units of the svg, at the size of text in the application
LABEL_ROW_MARGIN = 6.0  # half the height of a row of text


def row_intervals(ring, y):
    """The stretches of a horizontal line at y that are inside a ring, as (from, to)."""
    crossings = sorted(ring[i][0] + (y - ring[i][1]) * (ring[(i + 1) % len(ring)][0] - ring[i][0]) / (ring[(i + 1) % len(ring)][1] - ring[i][1])
                       for i in range(len(ring)) if (ring[i][1] > y) != (ring[(i + 1) % len(ring)][1] > y))
    return list(zip(crossings[::2], crossings[1::2]))


def key(point):
    return (round(point[0], 1), round(point[1], 1))


def simplified_chain(chain):
    """The Douglas-Peucker simplification of a chain of points, with the ends kept."""
    keep = [False] * len(chain)
    keep[0] = keep[-1] = True
    stack = [(0, len(chain) - 1)]
    while stack:
        first, last = stack.pop()
        (x1, y1), (x2, y2) = chain[first], chain[last]
        length = math.hypot(x2 - x1, y2 - y1)
        worst, worst_distance = -1, SIMPLIFY_TOLERANCE
        for i in range(first + 1, last):
            x, y = chain[i]
            distance = (math.hypot(x - x1, y - y1) if length == 0
                        else abs((x2 - x1) * (y1 - y) - (x1 - x) * (y2 - y1)) / length)
            if distance > worst_distance:
                worst, worst_distance = i, distance
        if worst >= 0:
            keep[worst] = True
            stack.append((first, worst))
            stack.append((worst, last))
    return [p for p, kept in zip(chain, keep) if kept]


def simplify_all(*groups):
    """Leaves out points that hardly change the outlines, so the shapes are smaller to draw and to send to a browser.
    A border that two shapes share is simplified once, the same way for both, so they still fit and still share corners:
    each ring is cut into chains where the set of shapes that have an edge changes, and each chain is simplified on its own."""
    owners = {}
    for group in groups:
        for name, rings in group.items():
            for ring in rings:
                for point in ring:
                    owners.setdefault(key(point), set()).add(name)
    cache = {}

    def chain_result(chain):
        # the same result from both sides, which see the chain in opposite directions
        forward = key(chain[0]) <= key(chain[-1])
        oriented = chain if forward else chain[::-1]
        cache_key = tuple(key(p) for p in oriented)
        if cache_key not in cache:
            cache[cache_key] = simplified_chain(oriented)
        result = cache[cache_key]
        return result if forward else result[::-1]

    def simplified(name, ring):
        n = len(ring)
        edge_owners = [frozenset(owners[key(ring[i])] & owners[key(ring[(i + 1) % n])]) for i in range(n)]
        junctions = [i for i in range(n) if edge_owners[i - 1] != edge_owners[i]]
        if not junctions:
            # all edges are the same: an island, or a border all around, cut in two at the points farthest from each other
            far = max(range(n), key=lambda i: math.dist(ring[0], ring[i]))
            junctions = [0, far]
        result = []
        for a, b in zip(junctions, junctions[1:] + [junctions[0] + n]):
            chain = [ring[i % n] for i in range(a, b + 1)]
            result.extend(chain_result(chain)[:-1])
        return result if len(result) >= 3 else ring

    return tuple({name: [simplified(name, ring) for ring in rings] for name, rings in group.items()} for group in groups)


def label_point(rings):
    """Where to show the number of armies, with the name below it: the point of the largest part with the most room around it for
    both, which is the one where the narrowest of the rows of the number and the name is widest, with the point in the middle of each
    row. Of equally good points, the one nearest the middle of the part."""
    ring = max(rings, key=lambda r: abs(area(r)))
    middle = centroid(ring)
    rows = {}

    def room(x, y):
        """Twice the distance to the nearest edge along the row, if x is inside the shape in it, otherwise 0."""
        key = round(y)
        if key not in rows:
            rows[key] = row_intervals(ring, key + 0.01)
        for start, end in rows[key]:
            if start <= x <= end:
                return 2 * min(x - start, end - x)
        return 0.0

    best, best_score = middle, -1.0
    top, bottom = int(min(p[1] for p in ring)), int(max(p[1] for p in ring)) + 1
    for y in range(top, bottom + 1):
        for start, end in row_intervals(ring, y + 0.01):
            x = start + 1.0
            while x < end:
                score = min(room(x, y + offset) for offset in
                            (-LABEL_ROW_MARGIN, 0.0, NAME_ROW_OFFSET, NAME_ROW_OFFSET + LABEL_ROW_MARGIN))
                score -= 0.03 * math.dist((x, y), middle)
                if score > best_score:
                    best, best_score = (x, y), score
                x += 2.0
    return best


def clip_east(ring, limit):
    """The part of a ring with x <= limit."""
    result = []
    for i, p in enumerate(ring):
        q = ring[(i + 1) % len(ring)]
        if p[0] <= limit:
            result.append(p)
        if (p[0] <= limit) != (q[0] <= limit):
            t = (limit - p[0]) / (q[0] - p[0])
            result.append((limit, p[1] + t * (q[1] - p[1])))
    return result


# the territories

def territories():
    countries = load_countries()
    shapes = {}
    for name, code in SIMPLE.items():
        shapes[name] = significant(countries[code])
    # Norway has Svalbard far away, which is left out
    norway = max(countries["NO"], key=lambda r: abs(area(r)))
    xs, ys = [p[0] for p in norway], [p[1] for p in norway]
    shapes["Norway"] = [r for r in significant(countries["NO"]) if min(ys) - 40 < centroid(r)[1] < max(ys) + 40
                        and min(xs) - 40 < centroid(r)[0] < max(xs) + 40]
    shapes["Serbia"] = merge_rings(significant(countries["RS"] + countries["XK"]))
    # Corsica and Sardinia are one territory, and are taken from France and Italy, which has Sicily
    france = significant(countries["FR"])
    corsica = [r for r in france if centroid(r)[0] > 500 and centroid(r)[1] > 515]
    shapes["France"] = [r for r in france if r not in corsica]
    italy = significant(countries["IT"])
    sardinia = [r for r in italy if centroid(r)[0] < 530 and centroid(r)[1] > 540]
    shapes["Italy"] = [r for r in italy if r not in sardinia]
    shapes["Mediterranean Isles"] = corsica + sardinia
    assert len(corsica) == 1 and len(sardinia) == 1, (len(corsica), len(sardinia))
    # the island of Ireland, with the north, which is in the United Kingdom
    gb = significant(countries["GB"])
    mainland = max(gb, key=lambda r: abs(area(r)))
    others = [r for r in gb if r is not mainland]
    north_of_ireland = [r for r in others if centroid(r)[0] < 345 and 322 < centroid(r)[1] < 350]
    shapes["Ireland"] = significant(countries["IE"]) + north_of_ireland
    # Great Britain is divided: Scotland is north of a line from the Solway Firth to Berwick, and Wales west of the border from the Dee
    # to the Severn. The pieces are cut at the vertices of the coast nearest to these points.
    scotland_part, rest = keep_the_piece_with(split(mainland, (360.8, 340.5), (381.7, 328.7), []), (350, 300))
    wales_part, england_part = keep_the_piece_with(split(rest, (364.7, 363.3), (370.8, 389.5),
                                                         [(365.8, 370.0), (367.0, 378.3), (368.3, 383.3)]), (356, 376))
    islands = [r for r in others if r not in north_of_ireland]
    shapes["Scotland"] = [scotland_part] + [r for r in islands if centroid(r)[1] < 335]
    shapes["Wales"] = [wales_part] + [r for r in islands if 345 < centroid(r)[0] < 358 and 355 < centroid(r)[1] < 372]
    shapes["England"] = [england_part] + [r for r in islands if centroid(r)[1] >= 335 and r not in shapes["Wales"]
                                          and not (centroid(r)[0] < 345 and centroid(r)[1] < 350)]
    neutral = {}
    for name, code in INACTIVE.items():
        rings = significant(countries[code])
        if code == "TR":
            rings = [clip_east(r, HIDE_TURKEY_EAST_OF) for r in rings if centroid(r)[0] < HIDE_TURKEY_EAST_OF + 60]
        if rings:  # Liechtenstein and Andorra are too small to show
            neutral[name] = rings
    return shapes, neutral


def touching(shapes, tolerance=0.9):
    """The pairs of territories whose shapes have at least two corners (nearly) in common, within the tolerance in the units of the svg.
    One corner is not enough, since shapes that only meet in a point don't border each other."""
    counts = {}
    # the corners are put in the cells of a grid, so each is only compared to those in the cells around it
    corner_cells = {}
    for name, rings in shapes.items():
        for ring in rings:
            for x, y in ring:
                corner_cells.setdefault((int(x // tolerance), int(y // tolerance)), []).append((name, x, y))
    for name, rings in shapes.items():
        for ring in rings:
            for x, y in ring:
                cx, cy = int(x // tolerance), int(y // tolerance)
                found = set()
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        for other, ox, oy in corner_cells.get((cx + dx, cy + dy), []):
                            if other != name and math.dist((x, y), (ox, oy)) <= tolerance:
                                found.add(other)
                for other in found:
                    counts.setdefault(tuple(sorted((name, other))), set()).add((round(x, 1), round(y, 1)))
    return {pair for pair, points in counts.items() if len(points) >= 2}


def path_string(rings, shift):
    return " ".join("M " + " L ".join("%.1f,%.1f" % (x - shift[0], y - shift[1]) for x, y in ring) + " Z" for ring in rings)


def read_label_offsets(names):
    """The displacements of labels by hand, from the file that the application saves: name -> (dx, dy) in the units of the svg."""
    path = HERE / "label-offsets.json"
    if not path.exists():
        return {}
    offsets = json.loads(path.read_text(encoding="utf8"))
    unknown = set(offsets) - set(names)
    assert not unknown, "label-offsets.json has labels for something that is not a territory: %s" % sorted(unknown)
    return {name: (float(dx), float(dy)) for name, (dx, dy) in offsets.items()}


def main():
    shapes, neutral = territories()
    before = sum(len(ring) for rings in list(shapes.values()) + list(neutral.values()) for ring in rings)
    shapes, neutral = simplify_all(shapes, neutral)
    after = sum(len(ring) for rings in list(shapes.values()) + list(neutral.values()) for ring in rings)
    print("points in the shapes: %d, simplified to %d" % (before, after))
    label_offsets = read_label_offsets(shapes)
    members = [t for _, _, ts in CONTINENTS for t in ts]
    assert sorted(members) == sorted(shapes), set(members) ^ set(shapes)
    sea = {tuple(sorted(pair.replace("_", " ").split("-"))) for pair in SEA_LINKS.split()}
    # the borders over land are the pairs of territories whose shapes meet
    land = touching(shapes)
    meet = land
    assert not (land & sea), "these sea links are also land borders: %s" % sorted(land & sea)
    borders = {name: set() for name in shapes}
    for a, b in land | sea:
        borders[a].add(b)
        borders[b].add(a)

    everything = list(shapes.values()) + list(neutral.values())
    all_x = [x for rings in everything for ring in rings for x, _ in ring]
    all_y = [y for rings in everything for ring in rings for _, y in ring]
    shift = (min(all_x) - MARGIN, min(all_y) - MARGIN)

    core = ROOT / "core" / "src" / "main" / "resources" / "maps" / "europe"
    app = ROOT / "ui" / "src" / "main" / "resources" / "maps" / "europe"
    core.mkdir(parents=True, exist_ok=True)
    app.mkdir(parents=True, exist_ok=True)
    (core / "continents.json").write_text(json.dumps(
        [{"name": n, "territories": sorted(ts), "bonusPoints": b} for n, b, ts in CONTINENTS], indent=2) + "\n")
    (core / "borders.json").write_text(json.dumps({n: sorted(bs) for n, bs in sorted(borders.items())}, indent=2) + "\n")
    entries = []
    for name, rings in neutral.items():
        x, y = label_point(rings)
        entries.append({"id": "".join(ch if ch.isalnum() else "_" for ch in name.lower()), "name": name, "path": path_string(rings, shift),
                        "label": [round(x - shift[0], 1), round(y - shift[1], 1)]})
    for name, rings in shapes.items():
        x, y = label_point(rings)
        dx, dy = label_offsets.get(name, (0.0, 0.0))
        entries.append({
            "id": "".join(ch if ch.isalnum() else "_" for ch in name.lower()), "path": path_string(rings, shift),
            # where the label is, and where it would be without the displacement by hand, which the application edits
            "label": [round(x + dx - shift[0], 1), round(y + dy - shift[1], 1)],
            "labelBase": [round(x - shift[0], 1), round(y - shift[1], 1)],
            # the shapes that meet need no line between them, only those across sea
            "touches": sorted(other for a, b in meet for other in (a, b) if name in (a, b) and other != name)})
    (app / "map.json").write_text(json.dumps(entries, indent=1) + "\n")
    degrees = sorted(((len(bs), n) for n, bs in borders.items()), reverse=True)
    print(len(shapes), "territories,", sum(len(b) for b in borders.values()) // 2, "borders; most borders:", degrees[:5])


if __name__ == "__main__":
    main()
