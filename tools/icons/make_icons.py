"""Makes the icon font of the application from network.svg, and the icons of the game from app-icon.svg:

    pip install fonttools     (once, in a virtual environment)
    python3 -I tools/icons/make_icons.py

Needs rsvg-convert (librsvg) for the images. The results are in the repository, so this is only run when an icon is changed:
  - ui/src/main/resources/fonts/riskfx-icons.ttf, the font, which has the glyph of network.svg as the character U+E900
  - ui/src/main/resources/app-icon.png, the icon of the window
  - mobile/src/android/res/mipmap-*/ic_launcher.png, the launcher icons of the Android app (the Gluon plugin copies the res folder
    of the source folder of the target into the Android project, over the default icons)

A glyph is the outline of a shape, so the lines and circles of the SVG are turned into closed shapes, which are drawn in the same direction,
so that a place where they overlap is filled, as it is with the lines and circles. The glyph fills the height of the font (the ascent is
the full size and there is no descent), so that an icon of this font is as large as the size says.
"""
import math
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
EM = 1000
CODE = 0xE900
FAMILY = "RiskFX Icons"


def shapes(svg_file):
    """The circles and lines of the SVG as contours, which are lists of points in font units (y up), in the same direction."""
    tree = ET.parse(svg_file).getroot()
    view = [float(v) for v in tree.get("viewBox").split()]
    scale = EM / view[2]

    def point(x, y):
        return ((x - view[0]) * scale, (view[3] - (y - view[1])) * scale)

    contours = []
    for element in tree:
        tag = element.tag.rsplit("}", 1)[-1]
        if tag == "circle":
            cx, cy, r = (float(element.get(a)) for a in ("cx", "cy", "r"))
            contours.append(("circle", point(cx, cy), r * scale))
        elif tag == "line":
            x1, y1, x2, y2, w = (float(element.get(a)) for a in ("x1", "y1", "x2", "y2", "stroke-width"))
            contours.append(("line", point(x1, y1), point(x2, y2), w * scale))
    return contours


def draw(pen, contours):
    for contour in contours:
        if contour[0] == "circle":
            (cx, cy), r = contour[1], contour[2]
            # a closed contour of off-curve points only, on the octagon around the circle, which TrueType takes as the curve through
            # the middles of its sides; clockwise, as the outer shape of a glyph is
            outer = r / math.cos(math.pi / 8)
            points = [(cx + outer * math.cos(math.radians(22.5 - 45 * k)), cy + outer * math.sin(math.radians(22.5 - 45 * k))) for k in range(8)]
            pen.qCurveTo(*points, None)
            pen.closePath()
        else:
            (x1, y1), (x2, y2), w = contour[1], contour[2], contour[3]
            length = math.hypot(x2 - x1, y2 - y1)
            nx, ny = -(y2 - y1) / length * w / 2, (x2 - x1) / length * w / 2
            # a rectangle around the line, clockwise
            pen.moveTo((x1 + nx, y1 + ny))
            pen.lineTo((x2 + nx, y2 + ny))
            pen.lineTo((x2 - nx, y2 - ny))
            pen.lineTo((x1 - nx, y1 - ny))
            pen.closePath()


def make_font(target):
    glyphs = {}
    notdef = TTGlyphPen(None)
    glyphs[".notdef"] = notdef.glyph()
    pen = TTGlyphPen(None)
    draw(pen, shapes(HERE / "network.svg"))
    glyphs["network"] = pen.glyph()
    builder = FontBuilder(EM, isTTF=True)
    builder.setupGlyphOrder([".notdef", "network"])
    builder.setupCharacterMap({CODE: "network"})
    builder.setupGlyf(glyphs)
    builder.setupHorizontalMetrics({".notdef": (EM, 0), "network": (EM, 0)})
    builder.setupHorizontalHeader(ascent=EM, descent=0)
    # the full name and the PostScript name are needed too, or JavaFX does not take the font
    builder.setupNameTable({"familyName": FAMILY, "styleName": "Regular", "uniqueFontIdentifier": FAMILY + " 1.0",
                            "fullName": FAMILY, "psName": FAMILY.replace(" ", "") + "-Regular", "version": "Version 1.0"})
    builder.setupOS2(sTypoAscender=EM, sTypoDescender=0, usWinAscent=EM, usWinDescent=0)
    builder.setupPost()
    target.parent.mkdir(parents=True, exist_ok=True)
    builder.save(target)
    print("wrote", target.relative_to(ROOT))


def png(size, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), str(HERE / "app-icon.svg"), "-o", str(target)], check=True)
    print("wrote", target.relative_to(ROOT))


if __name__ == "__main__":
    make_font(ROOT / "ui/src/main/resources/fonts/riskfx-icons.ttf")
    png(256, ROOT / "ui/src/main/resources/app-icon.png")
    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        png(size, ROOT / f"mobile/src/android/res/mipmap-{density}/ic_launcher.png")
