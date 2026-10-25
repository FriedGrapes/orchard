# Orchard 심볼 2차 시안 — 굵고 단순한 도형 몇 개로(설명하지 않기). 검정 단색, viewBox 256.
#   python logo_marks2.py <출력 폴더> <판>
import os, sys
sys.argv = sys.argv[:1] + [sys.argv[1]]  # logo_marks 가 OUT 을 읽게
from logo_marks import SVG

def circ(cx, cy, r, ccw=False):
    s = 0 if ccw else 1
    return f"M{cx + r:.2f},{cy:.2f} A{r},{r} 0 1 {s} {cx - r:.2f},{cy:.2f} A{r},{r} 0 1 {s} {cx + r:.2f},{cy:.2f} Z"


def capsule(x0, y0, x1, y1):
    r = (x1 - x0) / 2
    return f"M{x0},{y0 + r} A{r},{r} 0 0 1 {x1},{y0 + r} V{y1 - r} A{r},{r} 0 0 1 {x0},{y1 - r} Z"


def d_vesica():
    """겹친 자리의 잎: 두 원(한 쌍)이 겹친 렌즈가 비어 잎이 된다."""
    return SVG.format(f'<path fill-rule="evenodd" d="{circ(98, 128, 64)} {circ(158, 128, 64)}"/>')


def d_vesica_stems():
    """겹친 자리의 잎 + 바깥쪽 아래로 짧은 줄기(이어버드임을 살짝)."""
    return SVG.format(f'<path fill-rule="evenodd" d="{circ(98, 112, 60)} {circ(158, 112, 60)}"/>'
                      f'<path d="{capsule(38, 112, 70, 222)}"/><path d="{capsule(186, 112, 218, 222)}"/>')


def d_stem_o():
    """줄기 달린 O: 굵은 고리 오른쪽 가장자리에서 줄기가 곧게 내려온다."""
    return SVG.format(f'<path fill-rule="evenodd" d="{circ(116, 112, 80)} {circ(116, 112, 40)}"/>'
                      f'<path d="{capsule(160, 112, 196, 230)}"/>')


def d_open():
    """열리는 순간: 둥근 케이스가 위아래 두 조각으로 살짝 벌어진다."""
    W, H, R, x, y, gap, lift = 200, 150, 64, 28, 66, 14, -6
    top_h = 52
    top = (f"M{x},{y + top_h} V{y + R} A{R},{R} 0 0 1 {x + R},{y} H{x + W - R} A{R},{R} 0 0 1 {x + W},{y + R} "
           f"V{y + top_h} Z")
    by = y + top_h + gap
    bot = (f"M{x},{by} H{x + W} V{y + H + gap - R} A{R},{R} 0 0 1 {x + W - R},{y + H + gap} H{x + R} "
           f"A{R},{R} 0 0 1 {x},{y + H + gap - R} Z")
    return SVG.format(f'<g transform="rotate({lift} {x} {y + top_h})"><path d="{top}"/></g><path d="{bot}"/>')


def d_vesica_diag():
    """겹친 자리의 잎(대각선): 왼쪽 아래·오른쪽 위 두 원이 겹친 렌즈가 비스듬한 잎이 된다."""
    r, d = 64, 78                      # 반지름·두 원 사이 거리(r 보다 조금 멀게 → 길쭉한 잎꼴 렌즈)
    k = d / 2 / 2 ** .5
    return SVG.format(f'<path fill-rule="evenodd" d="{circ(128 - k, 128 + k, r)} {circ(128 + k, 128 - k, r)}"/>')


def d_leaf_o():
    """잎 구멍 O: 굵은 O 의 안쪽 구멍이 잎 모양."""
    import math
    cx, cy, R = 128, 128, 96
    L, Wd, ang = 112, 60, 40           # 잎 길이·폭·기울기(위쪽에서 시계 방향)
    a = math.radians(ang); ux, uy = math.sin(a), -math.cos(a); nx, ny = -uy, ux
    x0, y0 = cx - ux * L / 2, cy - uy * L / 2
    tx, ty = cx + ux * L / 2, cy + uy * L / 2
    def p(t, w): return x0 + ux * L * t + nx * Wd * w, y0 + uy * L * t + ny * Wd * w
    c1, c2, c3, c4 = p(.05, .62), p(.55, .66), p(.55, -.66), p(.05, -.62)
    leaf = (f"M{x0:.2f},{y0:.2f} C{c1[0]:.2f},{c1[1]:.2f} {c2[0]:.2f},{c2[1]:.2f} {tx:.2f},{ty:.2f} "
            f"C{c3[0]:.2f},{c3[1]:.2f} {c4[0]:.2f},{c4[1]:.2f} {x0:.2f},{y0:.2f} Z")
    return SVG.format(f'<path fill-rule="evenodd" d="{circ(cx, cy, R)} {leaf}"/>')


def d_stem_o_tilt():
    """줄기 달린 O(기울임): 실제 에어팟처럼 줄기가 비스듬히."""
    return SVG.format(f'<g transform="rotate(-24 128 140)"><path fill-rule="evenodd" d="{circ(112, 104, 72)} {circ(112, 104, 34)}"/>'
                      f'<path d="{capsule(152, 104, 184, 232)}"/></g>')


def d_open_hinge():
    """열리는 순간: 위 조각이 왼쪽 경첩을 축으로 살짝 들려 쐐기 모양 틈이 생긴다."""
    W, H, R, x, y = 204, 152, 66, 26, 70
    split = y + 58
    top = (f"M{x},{split} V{y + R} A{R},{R} 0 0 1 {x + R},{y} H{x + W - R} A{R},{R} 0 0 1 {x + W},{y + R} V{split} Z")
    bot = (f"M{x},{split} H{x + W} V{y + H - R} A{R},{R} 0 0 1 {x + W - R},{y + H} H{x + R} A{R},{R} 0 0 1 {x},{y + H - R} Z")
    return SVG.format(f'<g transform="translate(0 6)"><g transform="rotate(-11 {x} {split})"><path d="{top}"/></g><path d="{bot}"/></g>')


def rcapsule(cx, cy, w, length, ang):
    """(cx,cy)에서 ang(0=아래, 시계 반대 +)쪽으로 뻗는 캡슐(반원 끝). 숫자로 풀어 쓴 경로."""
    import math
    a = math.radians(ang); dx, dy = math.sin(a), math.cos(a); nx, ny = dy, -dx
    r = w / 2
    ex, ey = cx + dx * length, cy + dy * length
    p1 = (cx + nx * r, cy + ny * r); p2 = (ex + nx * r, ey + ny * r)
    p3 = (ex - nx * r, ey - ny * r); p4 = (cx - nx * r, cy - ny * r)
    return (f"M{p1[0]:.2f},{p1[1]:.2f} L{p2[0]:.2f},{p2[1]:.2f} A{r:.2f},{r:.2f} 0 0 1 {p3[0]:.2f},{p3[1]:.2f} "
            f"L{p4[0]:.2f},{p4[1]:.2f} A{r:.2f},{r:.2f} 0 0 1 {p1[0]:.2f},{p1[1]:.2f} Z")


def pair_with_stems(c1, c2, r, stems, extra=""):
    lens = f'<path fill-rule="evenodd" d="{circ(*c1, r)} {circ(*c2, r)}"/>'
    st = "".join(f'<path d="{rcapsule(*s)}"/>' for s in stems)
    return SVG.format(lens + st + extra)


def v_diag_down():
    """대각선 한 쌍 + 두 원 모두 뒤쪽(오른쪽) 가장자리에서 줄기가 곧게 아래로."""
    r = 52; c1 = (96, 120); c2 = (152, 76)
    w = 22
    return pair_with_stems(c1, c2, r, [(c1[0] + r - w / 2, c1[1], w, 108, 0), (c2[0] + r - w / 2, c2[1], w, 108, 0)])


def v_level_splay():
    """나란한 한 쌍(조금 어긋나게) + 바깥 아래로 살짝 벌어진 짧은 줄기 — 앞에서 본 에어팟 한 쌍."""
    r = 54; c1 = (100, 100); c2 = (156, 100)
    w = 24
    import math
    def edge(c, deg): t = math.radians(deg); return c[0] + (r - w / 2) * math.sin(t), c[1] + (r - w / 2) * math.cos(t)
    s1 = edge(c1, -40); s2 = edge(c2, 40)
    return pair_with_stems(c1, c2, r, [(s1[0], s1[1], w, 100, -14), (s2[0], s2[1], w, 100, 14)])


def v_diag_tilt():
    """대각선 한 쌍 + 실제 에어팟처럼 비스듬한 줄기(같은 방향)."""
    r = 52; c1 = (92, 116); c2 = (150, 74)
    w = 22
    import math
    def edge(c, deg): t = math.radians(deg); return c[0] + (r - w / 2) * math.sin(t), c[1] + (r - w / 2) * math.cos(t)
    s1 = edge(c1, 60); s2 = edge(c2, 60)
    return pair_with_stems(c1, c2, r, [(s1[0], s1[1], w, 112, 16), (s2[0], s2[1], w, 112, 16)])


def v_short_nubs():
    """대각선 한 쌍 + 아주 짧은 줄기 끝(점 같은 꼬리)만 — 가장 절제."""
    r = 58; c1 = (102, 134); c2 = (158, 92)
    w = 24
    import math
    def edge(c, deg): t = math.radians(deg); return c[0] + (r - w / 2) * math.sin(t), c[1] + (r - w / 2) * math.cos(t)
    s1 = edge(c1, 50); s2 = edge(c2, 50)
    return pair_with_stems(c1, c2, r, [(s1[0], s1[1], w, 58, 22), (s2[0], s2[1], w, 58, 22)])


def build2(out, v):
    for name, fn in [("g-diag-down", v_diag_down), ("g-level-splay", v_level_splay), ("g-diag-tilt", v_diag_tilt), ("g-short-nubs", v_short_nubs)]:
        open(os.path.join(out, f"{name}-{v}.svg"), "w", encoding="utf-8").write(fn())


if __name__ == "__main__":
    out = sys.argv[1]
    build2(out, os.environ.get("V", "v1"))
    print("ok")
