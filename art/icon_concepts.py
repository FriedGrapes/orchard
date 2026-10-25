# Orchard 아이콘 시안 4가지(SVG). 크롬으로 PNG 로 바꾸고, 크기별·밝은/어두운 바탕 비교표를 만든다.
#   python icon_concepts.py <출력 폴더>
import math, os, subprocess, sys
from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
C = 512
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"


def pt(cx, cy, r, deg):  # 12시 기준 시계 방향 각도
    a = math.radians(deg)
    return cx + r * math.sin(a), cy - r * math.cos(a)


def arc(cx, cy, r, d0, d1):
    x0, y0 = pt(cx, cy, r, d0)
    x1, y1 = pt(cx, cy, r, d1)
    large = 1 if (d1 - d0) % 360 > 180 else 0
    return f"M{x0:.1f},{y0:.1f} A{r},{r} 0 {large} 1 {x1:.1f},{y1:.1f}"


def leaf(x, y, length, width, angle, bend=0.18):
    """잎: (x,y)에서 angle(도, 0=위, 시계방향)으로 뻗는 버들잎 모양."""
    a = math.radians(angle)
    ux, uy = math.sin(a), -math.cos(a)
    nx, ny = -uy, ux
    tx, ty = x + ux * length, y + uy * length

    def p(t, s):
        return x + ux * length * t + nx * width * s, y + uy * length * t + ny * width * s
    c1 = p(0.30, 0.95 + bend); c2 = p(0.75, 0.70 + bend)
    c3 = p(0.75, -0.70 + bend); c4 = p(0.30, -0.95 + bend)
    d = (f"M{x:.1f},{y:.1f} C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {tx:.1f},{ty:.1f} "
         f"C{c3[0]:.1f},{c3[1]:.1f} {c4[0]:.1f},{c4[1]:.1f} {x:.1f},{y:.1f} Z")
    return d, (x, y, tx, ty)


HEAD = '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">'


def concept_a():
    """링 리프: 배터리 링을 닮은 에메랄드 고리 끝에서 잎 하나가 돋는다. 검정 그라파이트 바탕."""
    r, w, cy = 205, 62, 536
    lf, (x0, y0, x1, y1) = leaf(*pt(C, cy, r, 0), 205, 62, 38)
    ring = arc(C, cy, r, 34, 360)
    vx, vy = x0 + (x1 - x0) * .78, y0 + (y1 - y0) * .78
    return HEAD + f'''
<defs>
 <radialGradient id="bg" cx="50%" cy="22%" r="95%"><stop offset="0" stop-color="#2B2F36"/><stop offset=".55" stop-color="#121418"/><stop offset="1" stop-color="#040506"/></radialGradient>
 <linearGradient id="ring" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#0A8F52"/><stop offset=".6" stop-color="#30D158"/><stop offset="1" stop-color="#A6F7C4"/></linearGradient>
 <linearGradient id="lf" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#2FCB6E"/><stop offset="1" stop-color="#C9FFD9"/></linearGradient>
 <filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="26"/></filter>
</defs>
<rect width="1024" height="1024" fill="url(#bg)"/>
<circle cx="{C}" cy="{cy}" r="{r}" fill="none" stroke="#fff" stroke-opacity=".07" stroke-width="{w}"/>
<path d="{ring}" fill="none" stroke="#30D158" stroke-opacity=".45" stroke-width="{w}" stroke-linecap="round" filter="url(#glow)"/>
<path d="{ring}" fill="none" stroke="url(#ring)" stroke-width="{w}" stroke-linecap="round"/>
<path d="{lf}" fill="url(#lf)"/>
<path d="M{x0:.1f},{y0:.1f} L{vx:.1f},{vy:.1f}" stroke="#0B7A44" stroke-opacity=".35" stroke-width="5" stroke-linecap="round"/>
</svg>'''


def concept_b():
    """펄: 밝은 진주빛 바탕 위 매끈한 흰 조약돌(케이스 느낌) + 새겨진 작은 잎."""
    lf, _ = leaf(C - 4, 600, 150, 46, 32)
    return HEAD + f'''
<defs>
 <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F7F6F3"/><stop offset="1" stop-color="#DCDDE0"/></linearGradient>
 <linearGradient id="peb" x1="0" y1="0" x2=".3" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset=".6" stop-color="#F3F4F6"/><stop offset="1" stop-color="#D5D8DE"/></linearGradient>
 <radialGradient id="hl" cx="38%" cy="22%" r="45%"><stop offset="0" stop-color="#fff" stop-opacity="1"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
 <linearGradient id="lf" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#0E9F5B"/><stop offset="1" stop-color="#5FE39A"/></linearGradient>
 <filter id="sh" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="30"/></filter>
 <filter id="in"><feGaussianBlur stdDeviation="3"/></filter>
</defs>
<rect width="1024" height="1024" fill="url(#bg)"/>
<rect x="282" y="346" width="460" height="400" rx="190" fill="#000" opacity=".22" filter="url(#sh)"/>
<rect x="282" y="312" width="460" height="400" rx="190" fill="url(#peb)"/>
<rect x="282" y="312" width="460" height="400" rx="190" fill="url(#hl)"/>
<path d="M300,470 H724" stroke="#C9CCD3" stroke-width="4"/>
<path d="{lf}" fill="#000" opacity=".18" filter="url(#in)" transform="translate(0,3)"/>
<path d="{lf}" fill="url(#lf)"/>
</svg>'''


def concept_c():
    """음파 잎: 둥근 막대(소리 파형)가 모여 잎 모양을 이룬다. 검정 바탕, 초록→라임."""
    bars = []
    n, gap, bw = 9, 46, 28
    for i in range(n):
        t = i / (n - 1)
        h = 70 + 330 * math.sin(math.pi * (0.08 + 0.84 * t)) ** 1.3
        x = C - (n - 1) * gap / 2 + i * gap
        y = 540 - h / 2 - 40 * (t - 0.5)
        bars.append(f'<rect x="{x - bw / 2:.1f}" y="{y:.1f}" width="{bw}" height="{h:.1f}" rx="{bw / 2}" fill="url(#g)"/>')
    b = "".join(bars)
    return HEAD + f'''
<defs>
 <radialGradient id="bg" cx="50%" cy="20%" r="95%"><stop offset="0" stop-color="#1C2420"/><stop offset="1" stop-color="#030504"/></radialGradient>
 <linearGradient id="g" gradientUnits="userSpaceOnUse" x1="300" y1="760" x2="720" y2="280"><stop offset="0" stop-color="#0A8F52"/><stop offset=".55" stop-color="#34D46A"/><stop offset="1" stop-color="#D4FF9E"/></linearGradient>
 <filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="24"/></filter>
</defs>
<rect width="1024" height="1024" fill="url(#bg)"/>
<g transform="rotate(-18 512 520)">
 <g opacity=".5" filter="url(#glow)">{b}</g>
 {b}
</g>
</svg>'''


def concept_d():
    """모노그램: 깊은 숲색 바탕 위 샴페인 골드 가는 O 고리, 위에 잎 두 장. 향수 브랜드 같은 고급감."""
    r, w, cy = 196, 30, 540
    lf, _ = leaf(*pt(C, cy, r, 0), 150, 44, 30)
    lf2, _ = leaf(*pt(C, cy, r, 0), 112, 34, -38)
    ring = arc(C, cy, r, 26, 334)
    return HEAD + f'''
<defs>
 <radialGradient id="bg" cx="50%" cy="25%" r="90%"><stop offset="0" stop-color="#1E4A38"/><stop offset=".6" stop-color="#0D2A1F"/><stop offset="1" stop-color="#051510"/></radialGradient>
 <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FBEBC5"/><stop offset=".45" stop-color="#D9B26F"/><stop offset=".7" stop-color="#F6E2B3"/><stop offset="1" stop-color="#A87F3E"/></linearGradient>
 <filter id="sh" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="14"/></filter>
</defs>
<rect width="1024" height="1024" fill="url(#bg)"/>
<path d="{ring}" fill="none" stroke="#000" stroke-opacity=".45" stroke-width="{w}" stroke-linecap="round" filter="url(#sh)" transform="translate(0,14)"/>
<path d="{ring}" fill="none" stroke="url(#gold)" stroke-width="{w}" stroke-linecap="round"/>
<path d="{lf}" fill="url(#gold)"/>
<path d="{lf2}" fill="url(#gold)" opacity=".85"/>
</svg>'''


CONCEPTS = [("a_ring_leaf", concept_a), ("b_pearl", concept_b), ("c_wave_leaf", concept_c), ("d_monogram", concept_d)]


def render(svg_text, png, size=1024):
    svg = png[:-4] + ".svg"
    open(svg, "w", encoding="utf-8").write(svg_text)
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000",
                    f"--screenshot={png}", f"--window-size={size},{size}", "file:///" + svg.replace("\\", "/")],
                   check=True, capture_output=True)


def squircle_mask(S, n=5.0):
    m = Image.new("L", (S, S), 0)
    px = m.load(); r = S / 2
    for y in range(S):
        for x in range(S):
            if (abs(x - r + .5) / r) ** n + (abs(y - r + .5) / r) ** n <= 1:
                px[x, y] = 255
    return m


def sheet(names):
    mask = squircle_mask(1024)
    icons = []
    for n in names:
        im = Image.open(os.path.join(OUT, n + ".png")).convert("RGBA").resize((1024, 1024))
        im.putalpha(mask)
        icons.append(im)
    W = len(icons) * 340 + 40
    rows = []
    for bgc in [(242, 242, 247), (24, 24, 26)]:
        o = Image.new("RGB", (W, 470), bgc)
        d = ImageDraw.Draw(o)
        for i, ic in enumerate(icons):
            x = 40 + i * 340
            for size, (dx, dy) in [(300, (0, 20)), (96, (0, 350)), (48, (120, 374)), (24, (190, 386))]:
                s = ic.resize((size, size), Image.LANCZOS)
                o.paste(s, (x + dx, dy), s)
            d.text((x + 260, 420), "ABCD"[i], fill=(128, 128, 128))
        rows.append(o)
    out = Image.new("RGB", (W, 940))
    out.paste(rows[0], (0, 0)); out.paste(rows[1], (0, 470))
    return out


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, fn in CONCEPTS:
        render(fn(), os.path.join(OUT, name + ".png"))
    sheet([n for n, _ in CONCEPTS]).save(os.path.join(OUT, "sheet.png"))
    print("ok")
