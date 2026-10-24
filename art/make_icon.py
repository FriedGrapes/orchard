# Orchard 앱 아이콘 — 깊은 검정 바탕 + 진주빛 이어버드 + 에메랄드 잎. 두 OS 공통 원본.
#   python make_icon.py <출력 폴더>
# 결과: icon_1024.png(완성 정사각, 둥근 모서리 없음 — OS가 깎음), fg_432.png·bg_432.png·mono_432.png(안드로이드 적응형),
#       orchard.ico(윈도우, 둥근 사각 포함), orchard_256.png
import math, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
S = 2048  # 작업 해상도


def lin(w, h, stops, angle=90):
    """세로(90)·가로(0) 선형 그라데이션."""
    g = Image.new("RGBA", (w, h))
    px = g.load()
    for y in range(h):
        for x in range(w):
            t = (y / (h - 1)) if angle == 90 else (x / (w - 1))
            for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
                if t0 <= t <= t1:
                    k = (t - t0) / (t1 - t0 or 1)
                    px[x, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(4))
                    break
    return g


def radial(size, center, radius, inner, outer):
    w, h = size
    g = Image.new("RGBA", size)
    px = g.load()
    for y in range(h):
        for x in range(w):
            d = min(1.0, math.hypot(x - center[0], y - center[1]) / radius)
            d = d * d * (3 - 2 * d)
            px[x, y] = tuple(int(inner[i] + (outer[i] - inner[i]) * d) for i in range(4))
    return g


def mask_from(draw_fn, size):
    m = Image.new("L", size, 0)
    draw_fn(ImageDraw.Draw(m))
    return m


def background(size):
    w, h = size
    bg = radial(size, (w * 0.5, h * 0.30), w * 0.95, (44, 47, 54, 255), (6, 7, 9, 255))
    # 아래쪽에 아주 옅은 초록 반사
    glow = radial(size, (w * 0.5, h * 1.05), w * 0.75, (24, 70, 48, 120), (0, 0, 0, 0))
    bg.alpha_composite(glow)
    return bg


def earbud_layer(size):
    """진주빛 이어버드(머리 + 줄기) + 잎. size 안에서 중앙 61%(적응형 안전 영역)에 들어오게."""
    w, h = size
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    cx, cy = w * 0.45, h * 0.56
    u = w / 1000.0 * 0.8

    # --- 모양 마스크 (기울이기 전 똑바로 세운 상태) ---
    def bud_shape(d):
        d.ellipse([cx - 150 * u, cy - 260 * u, cx + 150 * u, cy - 10 * u], fill=255)        # 머리
        d.ellipse([cx + 20 * u, cy - 200 * u, cx + 200 * u, cy - 40 * u], fill=255)         # 귀 쪽 볼록
        d.rounded_rectangle([cx - 62 * u, cy - 120 * u, cx + 62 * u, cy + 330 * u], radius=62 * u, fill=255)  # 줄기
    shape = mask_from(bud_shape, size)

    # 진주빛 몸통: 왼쪽 위 밝고 오른쪽 아래 은은한 회청색
    body = lin(w, h, [(0, (255, 255, 255, 255)), (0.55, (236, 238, 242, 255)), (1, (196, 202, 212, 255))], angle=0)
    body.putalpha(shape)
    # 안쪽 그림자(입체감)
    inner = shape.filter(ImageFilter.GaussianBlur(40 * u))
    shade = Image.new("RGBA", size, (120, 128, 142, 0))
    shade.putalpha(ImageChops.subtract(shape, inner).point(lambda v: int(v * 0.55)))
    body.alpha_composite(shade)
    # 하이라이트(왼쪽 위 줄무늬)
    hl = mask_from(lambda d: d.ellipse([cx - 120 * u, cy - 240 * u, cx - 10 * u, cy - 150 * u], fill=255), size)
    hl = hl.filter(ImageFilter.GaussianBlur(26 * u))
    hli = Image.new("RGBA", size, (255, 255, 255, 0)); hli.putalpha(hl.point(lambda v: int(v * 0.9)))
    body.alpha_composite(hli)
    # 그릴(검은 타원) — 귀 쪽
    grill = mask_from(lambda d: d.ellipse([cx + 92 * u, cy - 150 * u, cx + 150 * u, cy - 92 * u], fill=255), size)
    gl = Image.new("RGBA", size, (28, 30, 34, 255)); gl.putalpha(grill)
    body.alpha_composite(gl)
    # 줄기 끝 은색 띠
    tip = mask_from(lambda d: d.rounded_rectangle([cx - 62 * u, cy + 290 * u, cx + 62 * u, cy + 330 * u], radius=30 * u, fill=255), size)
    tipc = lin(w, h, [(0, (210, 214, 220, 255)), (1, (150, 156, 166, 255))], angle=0); tipc.putalpha(ImageChops.multiply(tip, shape))
    body.alpha_composite(tipc)

    # 바닥 그림자
    sh = shape.filter(ImageFilter.GaussianBlur(50 * u)).point(lambda v: int(v * 0.55))
    shadow = Image.new("RGBA", size, (0, 0, 0, 0)); shadow.putalpha(sh)
    layer.alpha_composite(shadow, (int(18 * u), int(40 * u)))
    layer.alpha_composite(body)

    # --- 잎 (머리 오른쪽 위에서 돋아남) ---
    leaf = Image.new("RGBA", size, (0, 0, 0, 0))
    lx, ly = cx + 40 * u, cy - 270 * u
    def leaf_shape(d):
        pts = []
        for k in range(0, 61):
            t = k / 60
            a = math.pi * t
            r = 120 * u * math.sin(a) ** 0.9
            pts.append((lx + 250 * u * t, ly - r * 0.62 + (-170 * u * t)))
        for k in range(60, -1, -1):
            t = k / 60
            a = math.pi * t
            r = 120 * u * math.sin(a) ** 0.9
            pts.append((lx + 250 * u * t, ly + r * 0.38 + (-170 * u * t)))
        d.polygon(pts, fill=255)
    lm = mask_from(leaf_shape, size)
    leafc = lin(w, h, [(0, (110, 231, 160, 255)), (0.5, (52, 199, 120, 255)), (1, (22, 128, 74, 255))], angle=0)
    leafc.putalpha(lm)
    vein = mask_from(lambda d: d.line([(lx + 20 * u, ly - 8 * u), (lx + 225 * u, ly - 153 * u)], fill=255, width=int(9 * u)), size)
    veinc = Image.new("RGBA", size, (220, 255, 232, 0)); veinc.putalpha(ImageChops.multiply(vein, lm).point(lambda v: int(v * 0.6)))
    leafc.alpha_composite(veinc)
    leaf_sh = lm.filter(ImageFilter.GaussianBlur(30 * u)).point(lambda v: int(v * 0.4))
    ls = Image.new("RGBA", size, (0, 0, 0, 0)); ls.putalpha(leaf_sh)
    leaf.alpha_composite(ls, (int(10 * u), int(24 * u)))
    leaf.alpha_composite(leafc)
    # 잎자루
    stem = mask_from(lambda d: d.line([(cx + 10 * u, cy - 245 * u), (lx + 20 * u, ly - 14 * u)], fill=255, width=int(14 * u)), size)
    stc = Image.new("RGBA", size, (40, 150, 90, 255)); stc.putalpha(stem)
    layer.alpha_composite(stc)
    layer.alpha_composite(leaf)

    # 전체를 살짝 기울여(오른쪽으로 14도) 생동감
    layer = layer.rotate(-14, resample=Image.BICUBIC, center=(cx, cy))
    mono_mask = ImageChops.lighter(shape, lm).rotate(-14, resample=Image.BICUBIC, center=(cx, cy))
    return layer, mono_mask


def main():
    os.makedirs(OUT, exist_ok=True)
    size = (S, S)
    bg = background(size)
    fg, mono = earbud_layer(size)

    full = bg.copy(); full.alpha_composite(fg)
    full.resize((1024, 1024), Image.LANCZOS).save(os.path.join(OUT, "icon_1024.png"))

    # 안드로이드 적응형: 108dp 캔버스 → 432px(xxxhdpi). 안전 영역 안에 들어가게 내용을 72%로 줄여 가운데
    def adaptive(img, scale=0.9):
        c = Image.new("RGBA", size, (0, 0, 0, 0))
        small = img.resize((int(S * scale), int(S * scale)), Image.LANCZOS)
        c.alpha_composite(small, ((S - small.width) // 2, (S - small.height) // 2))
        return c.resize((432, 432), Image.LANCZOS)
    adaptive(fg).save(os.path.join(OUT, "fg_432.png"))
    bg.resize((432, 432), Image.LANCZOS).save(os.path.join(OUT, "bg_432.png"))
    m = Image.new("RGBA", size, (255, 255, 255, 0)); m.putalpha(mono)
    adaptive(m).save(os.path.join(OUT, "mono_432.png"))

    # 윈도우·일반: 애플식 둥근 사각(연속 곡률에 가까운 슈퍼 타원)으로 깎은 것
    def squircle(n=5.0):
        mk = Image.new("L", size, 0)
        px = mk.load()
        r = S / 2
        for y in range(S):
            for x in range(S):
                dx, dy = abs(x - r + 0.5) / r, abs(y - r + 0.5) / r
                if dx ** n + dy ** n <= 1: px[x, y] = 255
        return mk
    sq = full.copy(); sq.putalpha(squircle())
    sq.resize((256, 256), Image.LANCZOS).save(os.path.join(OUT, "orchard_256.png"))
    sq.resize((256, 256), Image.LANCZOS).save(os.path.join(OUT, "orchard.ico"),
                                              sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    sq.resize((512, 512), Image.LANCZOS).save(os.path.join(OUT, "orchard_512.png"))
    print("ok")


main()
