# Orchard 앱 아이콘 — "한 쌍이 만든 잎": 이어버드 두 알이 겹친 자리가 잎이 된다. 두 OS 공통 원본.
# (graphic-design-rules / logo-design 절차로 정함. 시안·과정은 NAS Orchard/_docs/그래픽/앱아이콘/)
#   python make_icon.py <출력 폴더>
# 결과: orchard_mark.svg(심볼 원본), icon_1024.png(타일), orchard.ico·orchard_256.png(윈도우),
#       fg_432.png·bg_432.png·mono_432.png(안드로이드 적응형 아이콘, 108dp 캔버스의 xxxhdpi)
import math, os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icon_concepts import render  # Chrome 으로 SVG → PNG

OUT = sys.argv[1] if len(sys.argv) > 1 else "."

TILE = ("#FBFBFC", "#EEEFF2")   # 위·아래 — 거의 평평한 흰 타일
MARK = "#141517"
LEAF = "#2FB55A"

# 심볼 기하(256 칸, 바로 쓸 좌표). 원 둘 · 줄기 둘 · 겹친 렌즈.
R = 58.0
C1 = (94.55, 126.4)
C2 = (150.55, 84.4)
STEM_W, STEM_L, EDGE_DEG, STEM_DEG = 24.0, 58.0, 50.0, 22.0


def circle(c, r):
    x, y = c
    return f"M{x + r:.2f},{y:.2f} A{r:.2f},{r:.2f} 0 1 1 {x - r:.2f},{y:.2f} A{r:.2f},{r:.2f} 0 1 1 {x + r:.2f},{y:.2f} Z"


def stem(c):
    """원 가장자리(아래 오른쪽)에서 비스듬히 뻗는 줄기(끝이 둥근 막대)."""
    t = math.radians(EDGE_DEG)
    cx, cy = c[0] + (R - STEM_W / 2) * math.sin(t), c[1] + (R - STEM_W / 2) * math.cos(t)
    a = math.radians(STEM_DEG); dx, dy = math.sin(a), math.cos(a); nx, ny = dy, -dx
    r = STEM_W / 2
    ex, ey = cx + dx * STEM_L, cy + dy * STEM_L
    p1, p2 = (cx + nx * r, cy + ny * r), (ex + nx * r, ey + ny * r)
    p3, p4 = (ex - nx * r, ey - ny * r), (cx - nx * r, cy - ny * r)
    return (f"M{p1[0]:.2f},{p1[1]:.2f} L{p2[0]:.2f},{p2[1]:.2f} A{r:.2f},{r:.2f} 0 0 1 {p3[0]:.2f},{p3[1]:.2f} "
            f"L{p4[0]:.2f},{p4[1]:.2f} A{r:.2f},{r:.2f} 0 0 1 {p1[0]:.2f},{p1[1]:.2f} Z")


def lens():
    """두 원이 겹친 렌즈(잎)를 원호 두 개로."""
    (x1, y1), (x2, y2) = C1, C2
    d = math.hypot(x2 - x1, y2 - y1)
    a = d / 2; h = math.sqrt(R * R - a * a)
    mx, my = x1 + (x2 - x1) * a / d, y1 + (y2 - y1) * a / d
    px, py = mx + h * (y2 - y1) / d, my - h * (x2 - x1) / d
    qx, qy = mx - h * (y2 - y1) / d, my + h * (x2 - x1) / d
    return (f"M{px:.2f},{py:.2f} A{R:.2f},{R:.2f} 0 0 1 {qx:.2f},{qy:.2f} "
            f"A{R:.2f},{R:.2f} 0 0 1 {px:.2f},{py:.2f} Z")


def mark_group(scale, cx, cy, fill=MARK, leaf=LEAF):
    """심볼을 (cx,cy) 가운데에 scale 배로. leaf=None 이면 렌즈는 빈 구멍."""
    bx0, by0, bx1, by1 = 36.6, 26.4, 219.4, 221.6          # 심볼 테두리 상자(256 칸, svg_audit 로 잰 값)
    ox, oy = (bx0 + bx1) / 2, (by0 + by1) / 2
    body = (f'<path fill-rule="evenodd" d="{circle(C1, R)} {circle(C2, R)}"/>'
            f'<path d="{stem(C1)}"/><path d="{stem(C2)}"/>')
    lf = f'<path fill="{leaf}" d="{lens()}"/>' if leaf else ""
    return (f'<g transform="translate({cx:.2f} {cy:.2f}) scale({scale:.4f}) translate({-ox:.2f} {-oy:.2f})" fill="{fill}">'
            f'{body}{lf}</g>')


def svg(inner, size=1024, vb=256):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vb} {vb}" width="{size}" height="{size}">{inner}</svg>'


def tile_bg():
    return (f'<defs><linearGradient id="t" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{TILE[0]}"/>'
            f'<stop offset="1" stop-color="{TILE[1]}"/></linearGradient></defs><rect width="256" height="256" fill="url(#t)"/>')


# 심볼이 타일 폭의 약 52%를 차지하고, 눈으로 본 가운데(기하 가운데보다 살짝 위)에 놓인다
MARK_SCALE = 0.73
OPTICAL_Y = 125.0


def squircle(S, n=5.0):
    m = Image.new("L", (S, S), 0); px = m.load(); r = S / 2
    for y in range(S):
        for x in range(S):
            if (abs(x - r + .5) / r) ** n + (abs(y - r + .5) / r) ** n <= 1: px[x, y] = 255
    return m


def main():
    os.makedirs(OUT, exist_ok=True)
    p = lambda n: os.path.join(OUT, n)
    open(p("orchard_mark.svg"), "w", encoding="utf-8").write(
        svg('<title>Orchard</title>' + mark_group(1.0, 128, 124), 256))

    # 타일 완성본(윈도우·스토어용)
    render(svg(tile_bg() + mark_group(MARK_SCALE, 128, OPTICAL_Y)), p("icon_1024.png"))
    full = Image.open(p("icon_1024.png")).convert("RGBA").resize((1024, 1024), Image.LANCZOS)
    sq = full.copy(); sq.putalpha(squircle(1024))
    sq.resize((256, 256), Image.LANCZOS).save(p("orchard_256.png"))
    sq.resize((512, 512), Image.LANCZOS).save(p("orchard_512.png"))
    sq.resize((256, 256), Image.LANCZOS).save(p("orchard.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])

    # 안드로이드 적응형: 108dp 캔버스 중 보이는 곳은 가운데 72dp. 타일과 같은 비율이 되게 심볼을 72/108 로 줄인다.
    k = 72 / 108
    render(svg(mark_group(MARK_SCALE * k, 128, 128 + (OPTICAL_Y - 128) * k)), p("fg_1024.png"))
    render(svg(tile_bg()), p("bg_1024.png"))
    render(svg(mark_group(MARK_SCALE * k, 128, 128 + (OPTICAL_Y - 128) * k, fill="#FFFFFF", leaf=None)), p("mono_1024.png"))
    for n in ("fg", "bg", "mono"):
        Image.open(p(f"{n}_1024.png")).convert("RGBA").resize((432, 432), Image.LANCZOS).save(p(f"{n}_432.png"))
    print("ok")


if __name__ == "__main__":
    main()
