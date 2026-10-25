# A안(한 쌍이 만든 잎 + 줄기) 두 가지 줄기 길이 × 색 세 가지를 앱 아이콘 타일에 올려 비교한다.
#   python logo_color_preview.py <출력 폴더>
import os, sys
sys.argv = sys.argv[:2]
from logo_marks2 import circ, rcapsule
from icon_concepts import render
from PIL import Image, ImageDraw
import math

OUT = sys.argv[1]


def shape(kind):
    """(원1, 원2, 반지름, 줄기들, 맞춤 변환)"""
    if kind == "long":
        r, c1, c2, w, L = 52, (92, 116), (150, 74), 22, 112
        place = "translate(128 124) scale(0.858) translate(-133.7 -138.6)"
        ang_edge, ang = 60, 16
    else:
        r, c1, c2, w, L = 58, (102, 134), (158, 92), 24, 58
        place = "translate(-7.45 -7.6)"
        ang_edge, ang = 50, 22

    def edge(c):
        t = math.radians(ang_edge)
        return c[0] + (r - w / 2) * math.sin(t), c[1] + (r - w / 2) * math.cos(t)
    stems = [rcapsule(*edge(c), w, L, ang) for c in (c1, c2)]
    return c1, c2, r, stems, place


PALETTES = {
    "graphite": dict(tile="#0E0F11", mark="#F5F5F7", leaf="#34C759", label="그라파이트"),
    "white": dict(tile="#F5F5F7", mark="#121314", leaf="#1DB954", label="화이트"),
    "green": dict(tile="#14704A", mark="#F7F8F5", leaf=None, label="오차드 그린"),
}


def icon_svg(kind, pal):
    c1, c2, r, stems, place = shape(kind)
    p = PALETTES[pal]
    lens = "" if p["leaf"] is None else (
        f'<clipPath id="k"><circle cx="{c1[0]}" cy="{c1[1]}" r="{r}"/></clipPath>'
        f'<circle cx="{c2[0]}" cy="{c2[1]}" r="{r}" fill="{p["leaf"]}" clip-path="url(#k)"/>')
    mark = (f'<g transform="translate(128 128) scale(0.74) translate(-128 -128)"><g transform="{place}" fill="{p["mark"]}">'
            f'<path fill-rule="evenodd" d="{circ(*c1, r)} {circ(*c2, r)}"/>' + "".join(f'<path d="{s}"/>' for s in stems) +
            lens + '</g></g>')
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="1024" height="1024">'
            f'<rect width="256" height="256" fill="{p["tile"]}"/>{mark}</svg>')


def squircle(S, n=5.0):
    m = Image.new("L", (S, S), 0); px = m.load(); rr = S / 2
    for y in range(S):
        for x in range(S):
            if (abs(x - rr + .5) / rr) ** n + (abs(y - rr + .5) / rr) ** n <= 1: px[x, y] = 255
    return m


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    mask = squircle(1024)
    rows = []
    for kind in ("long", "short"):
        tiles = []
        for pal in PALETTES:
            png = os.path.join(OUT, f"tile-{kind}-{pal}.png")
            render(icon_svg(kind, pal), png)
            im = Image.open(png).convert("RGBA").resize((1024, 1024)); im.putalpha(mask)
            tiles.append(im)
        rows.append(tiles)
    W, H = 3 * 330 + 40, 2 * 330 + 60
    for bg, nm in [((242, 242, 247), "light"), ((28, 28, 30), "dark")]:
        o = Image.new("RGB", (W, H), bg); d = ImageDraw.Draw(o)
        for j, tiles in enumerate(rows):
            for i, im in enumerate(tiles):
                x, y = 20 + i * 330, 20 + j * 340
                big = im.resize((220, 220), Image.LANCZOS); o.paste(big, (x, y), big)
                for k, sz in enumerate((64, 32, 16)):
                    sm = im.resize((sz, sz), Image.LANCZOS); o.paste(sm, (x + 236, y + [10, 90, 140][k]), sm)
        o.save(os.path.join(OUT, f"color-preview-{nm}.png"))
    a = Image.open(os.path.join(OUT, "color-preview-light.png")); b = Image.open(os.path.join(OUT, "color-preview-dark.png"))
    c = Image.new("RGB", (a.width * 2 + 10, a.height), "white"); c.paste(a, (0, 0)); c.paste(b, (a.width + 10, 0))
    c.save(os.path.join(OUT, "color-preview.png"))
    print("ok")
