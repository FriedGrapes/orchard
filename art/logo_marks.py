# Orchard 심볼 시안(검정 단색, viewBox 256) — graphic-design-rules / logo-design 절차의 Phase 4.
#   python logo_marks.py <출력 폴더>
import math, os, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "."


def bud_path(head_r=34.0, stem_w=26.0, stem_len=86.0):
    """이어버드 한 개의 바깥선(로컬 좌표: 머리 가운데 0,0 · 줄기는 +y).
    줄기를 머리 오른쪽(뒤쪽) 가장자리에 붙여 오른쪽 선이 머리에서 줄기로 끊김 없이 이어진다 — 숟가락처럼 안 보이게."""
    r = head_r
    x1 = r                      # 줄기 오른쪽 = 머리 오른쪽 끝
    x0 = r - stem_w             # 줄기 왼쪽
    cr = stem_w / 2             # 줄기 끝 둥글기
    yb = stem_len - cr          # 줄기 끝 반원 중심 y
    yi = math.sqrt(r * r - x0 * x0)   # 줄기 왼쪽 선이 머리와 만나는 곳
    return (f"M{x1:.2f},0 L{x1:.2f},{yb:.2f} A{cr:.2f},{cr:.2f} 0 0 1 {x0:.2f},{yb:.2f} "
            f"L{x0:.2f},{yi:.2f} A{r:.2f},{r:.2f} 0 1 1 {x1:.2f},0 Z")


def place(d, x, y, rot=0.0, s=1.0, mirror=False):
    """로컬 경로를 실제 좌표로 옮긴다(변환 없이 숫자로 박아 넣음 — 마지막 파일에 transform이 남지 않게)."""
    import re
    toks = re.findall(r"[MLAZ]|-?\d+\.?\d*", d)
    out, i, cmd = [], 0, None
    a = math.radians(rot)
    ca, sa = math.cos(a), math.sin(a)
    mx = -1 if mirror else 1

    def tp(px, py):
        px *= mx
        return x + s * (px * ca - py * sa), y + s * (px * sa + py * ca)
    while i < len(toks):
        t = toks[i]
        if t in "MLAZ":
            cmd = t; out.append(t); i += 1
            if t == "Z":
                continue
        if cmd in "ML":
            px, py = tp(float(toks[i]), float(toks[i + 1])); out.append(f"{px:.2f},{py:.2f}"); i += 2
        elif cmd == "A":
            rx, ry, rotx, large, sweep = toks[i:i + 5]
            sweep = int(sweep) if not mirror else 1 - int(sweep)
            px, py = tp(float(toks[i + 5]), float(toks[i + 6]))
            out.append(f"{float(rx) * s:.2f},{float(ry) * s:.2f} {rotx} {large} {sweep} {px:.2f},{py:.2f}"); i += 7
    return " ".join(out)


def leaf_path(x, y, length, width, angle):
    """버들잎: (x,y)에서 angle(0=위, 시계방향)으로. 끝이 뾰족하고 아래가 둥근 두 곡선."""
    a = math.radians(angle)
    ux, uy = math.sin(a), -math.cos(a)
    nx, ny = -uy, ux
    tx, ty = x + ux * length, y + uy * length

    def p(t, w):
        return x + ux * length * t + nx * width * w, y + uy * length * t + ny * width * w
    c1, c2, c3, c4 = p(.18, .62), p(.62, .62), p(.62, -.62), p(.18, -.62)
    return (f"M{x:.2f},{y:.2f} C{c1[0]:.2f},{c1[1]:.2f} {c2[0]:.2f},{c2[1]:.2f} {tx:.2f},{ty:.2f} "
            f"C{c3[0]:.2f},{c3[1]:.2f} {c4[0]:.2f},{c4[1]:.2f} {x:.2f},{y:.2f} Z")


SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">{}</svg>'


def circle_d(cx, cy, r):
    return f"M{cx + r:.2f},{cy:.2f} A{r},{r} 0 1 1 {cx - r:.2f},{cy:.2f} A{r},{r} 0 1 1 {cx + r:.2f},{cy:.2f} Z"


# 이어버드 한 개(로컬: 머리 가운데 근처 0,0 · 귀 쪽 앞은 -x · 줄기는 +y). 머리는 앞쪽이 더 볼록한 달걀꼴,
# 줄기는 머리 뒤쪽(+x)에 붙어 뒤쪽 선이 머리→줄기로 끊김 없이 이어진다. 한 번에 이어진 매끈한 선 하나.
BUD = ("M30,4 C30,-22 12,-34 -6,-34 C-30,-34 -44,-16 -44,4 C-44,22 -32,34 -14,34 "
       "C-4,34 4,31 8,27 L8,82 A11,11 0 0 0 30,82 Z")
BUD_TIP = (19.0, 93.0)


def bud_shapes(x, y, rot=0.0, s=1.0, mirror=False):
    return [place_c(BUD, x, y, rot, s, mirror)]


def place_c(d, x, y, rot=0.0, s=1.0, mirror=False):
    """place() 에 C(3차 곡선)도 지원."""
    import re
    toks = re.findall(r"[MLACZ]|-?\d+\.?\d*", d)
    a = math.radians(rot); ca, sa = math.cos(a), math.sin(a); mx = -1 if mirror else 1
    def tp(px, py):
        px *= mx
        return x + s * (px * ca - py * sa), y + s * (px * sa + py * ca)
    out, i, cmd = [], 0, None
    while i < len(toks):
        t = toks[i]
        if t in "MLACZ":
            cmd = t; out.append(t); i += 1
            if t == "Z": continue
        n = {"M": 1, "L": 1, "C": 3}.get(cmd)
        if n:
            for _ in range(n):
                px, py = tp(float(toks[i]), float(toks[i + 1])); out.append(f"{px:.2f},{py:.2f}"); i += 2
        elif cmd == "A":
            rx, ry, rotx, large, sweep = toks[i:i + 5]
            sweep = (1 - int(sweep)) if mirror else int(sweep)
            px, py = tp(float(toks[i + 5]), float(toks[i + 6]))
            out.append(f"{float(rx) * s:.2f},{float(ry) * s:.2f} {rotx} {large} {sweep} {px:.2f},{py:.2f}"); i += 7
    return " ".join(out)


def bud_to_tip(tx, ty, rot, s=1.0, mirror=False):
    """줄기 끝이 (tx,ty)에 오도록 머리 위치를 계산해 놓는다."""
    a = math.radians(rot); px, py = BUD_TIP
    px *= -1 if mirror else 1
    dx, dy = s * (px * math.cos(a) - py * math.sin(a)), s * (px * math.sin(a) + py * math.cos(a))
    return bud_shapes(tx - dx, ty - dy, rot, s, mirror)


GRILLE = (-14.0, -12.0, 9.0, 6.5, -20.0)   # 머리 위 스피커 구멍(가운데 x,y · 반지름 · 기울기)


def grille(x, y, rot, s=1.0, mirror=False, fill="#fff"):
    gx, gy, rx, ry, gr = GRILLE
    gx *= -1 if mirror else 1
    a = math.radians(rot)
    cx, cy = x + s * (gx * math.cos(a) - gy * math.sin(a)), y + s * (gx * math.sin(a) + gy * math.cos(a))
    tilt = rot + (-gr if mirror else gr)
    return f'<ellipse cx="{cx:.2f}" cy="{cy:.2f}" rx="{rx * s:.2f}" ry="{ry * s:.2f}" transform="rotate({tilt:.1f} {cx:.2f} {cy:.2f})" fill="{fill}"/>'


def head_pos(tx, ty, rot, s=1.0, mirror=False):
    a = math.radians(rot); px, py = BUD_TIP
    px *= -1 if mirror else 1
    return tx - s * (px * math.cos(a) - py * math.sin(a)), ty - s * (px * math.sin(a) + py * math.cos(a))


def local_to(lx, ly, tx, ty, rot, s=1.0, mirror=False):
    """로컬 점 (lx,ly)가 (tx,ty)에 오도록 하는 머리 위치."""
    a = math.radians(rot); lx *= -1 if mirror else 1
    return tx - s * (lx * math.cos(a) - ly * math.sin(a)), ty - s * (lx * math.sin(a) + ly * math.cos(a))


def concept_a():
    """체리 한 쌍: 머리(열매)는 아래로 벌어지고 두 줄기가 위로 모여 한 점에서 만난다. 그 위에 잎 하나."""
    parts, sc = [], 0.92
    for tx, rot, mir in [(124, 199, True), (132, 161, False)]:
        hx, hy = local_to(*BUD_TIP, tx, 74, rot, sc, mir)
        parts.append(f'<path d="{place_c(BUD, hx, hy, rot, sc, mir)}"/>' + grille(hx, hy, rot, sc, mir))
    lf = leaf_path(128, 70, 52, 26, 54)
    return SVG.format("".join(parts) + f'<path d="{lf}"/>')


def concept_b():
    """잎 속 이어버드: 비스듬한 잎 면에서 이어버드를 비워 낸다. 머리의 스피커 구멍이 이어버드임을 알려 준다."""
    lf = leaf_path(40, 222, 236, 150, 44)
    x, y, r, sc = 120, 104, -30, 0.9
    return SVG.format(f'<path d="{lf}"/><path fill="#fff" d="{place_c(BUD, x, y, r, sc)}"/>' + grille(x, y, r, sc, fill="#000"))


def concept_c():
    """잎 표시등 케이스: 에어팟 케이스 한 덩이, 뚜껑 틈 아래 상태 표시등 자리에 잎."""
    body = "M32,112 C32,80 58,62 92,62 H164 C198,62 224,80 224,112 V150 C224,182 198,200 164,200 H92 C58,200 32,182 32,150 Z"
    seam = "M32,104 H224 V112 H32 Z"
    lf = leaf_path(121, 160, 30, 16, 50)
    return SVG.format(f'<path d="{body}"/><path fill="#fff" d="{seam}"/><path fill="#fff" d="{lf}"/>')


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    v = sys.argv[2] if len(sys.argv) > 2 else "v2"
    for name, fn in [("a-cherry", concept_a), ("b-leafbud", concept_b), ("c-case", concept_c)]:
        open(os.path.join(OUT, f"{name}-{v}.svg"), "w", encoding="utf-8").write(fn())
    print("ok")

