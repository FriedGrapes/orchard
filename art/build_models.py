# Orchard 기기 3D 모델 — 애플 파일을 쓰지 않고 코드로 직접 만든 모델을 회전 프레임으로 렌더링한다.
#   blender -b -P build_models.py -- <출력 폴더> [프레임 수] [가로 픽셀] [샘플] [이름,이름…]
# 결과: <출력>/<이름>/f000.png … (투명 배경, 바닥 그림자 포함)
# 사용자가 구한 모델(_data/models, 배포 가능한 라이선스)이 생기면 SCENES 에 불러오기 장면을 더한다.
import bpy, bmesh, math, sys, os
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = os.path.abspath(argv[0] if argv else "renders")
FRAMES = int(argv[1]) if len(argv) > 1 else 48
WIDTH = int(argv[2]) if len(argv) > 2 else 480
SAMPLES = int(argv[3]) if len(argv) > 3 else 48
ONLY = argv[4].split(",") if len(argv) > 4 else None


# ---------- 공통 ----------
def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    s = bpy.context.scene
    s.render.engine = "CYCLES"
    s.cycles.device = "CPU"
    s.cycles.samples = SAMPLES
    s.cycles.use_adaptive_sampling = True
    try:
        s.cycles.use_denoising = True
        s.cycles.denoiser = "OPENIMAGEDENOISE"
    except Exception:
        s.cycles.use_denoising = False
    s.render.film_transparent = True
    s.render.image_settings.file_format = "PNG"
    s.render.image_settings.color_mode = "RGBA"
    s.view_settings.view_transform = "AgX"
    s.view_settings.look = "AgX - Base Contrast"
    s.view_settings.exposure = 0.0
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.85, 0.87, 0.9, 1)
    bg.inputs[1].default_value = 0.35
    s.world = world
    return s


def mat(name, color, rough=0.3, metal=0.0, coat=0.0, emit=None, aniso=0.0, sss=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = (*color, 1)
    p.inputs["Roughness"].default_value = rough
    p.inputs["Metallic"].default_value = metal
    if coat:
        p.inputs["Coat Weight"].default_value = coat
        p.inputs["Coat Roughness"].default_value = 0.06
    if aniso:
        p.inputs["Anisotropic"].default_value = aniso
    if sss:
        p.inputs["Subsurface Weight"].default_value = sss
        p.inputs["Subsurface Radius"].default_value = (0.3, 0.3, 0.3)
    if emit:
        p.inputs["Emission Color"].default_value = (*emit, 1)
        p.inputs["Emission Strength"].default_value = 8.0
    return m


def finish(obj, m, subdiv=2):
    for poly in obj.data.polygons:
        poly.use_smooth = True
    if subdiv:
        mod = obj.modifiers.new("sub", "SUBSURF")
        mod.levels = subdiv
        mod.render_levels = subdiv
    obj.data.materials.clear()
    obj.data.materials.append(m)
    return obj


def ellipsoid(name, r, m, loc=(0, 0, 0), rot=(0, 0, 0), subdiv=1):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1, segments=48, ring_count=24, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.scale = r
    bpy.ops.object.transform_apply(scale=True)
    return finish(o, m, subdiv)


def rod(name, radius, a, b, m):
    """a→b 를 잇는 둥근 막대."""
    a, b = Vector(a), Vector(b)
    d = b - a
    bpy.ops.mesh.primitive_cylinder_add(radius=radius, depth=d.length, vertices=32, location=(a + b) / 2)
    o = bpy.context.active_object
    o.name = name
    o.rotation_euler = d.to_track_quat("Z", "Y").to_euler()
    return finish(o, m, 0)


def slab(name, w, d, h, corner, top, bottom, m, z0=0.0):
    """윤곽이 둥근 사각형(모서리 반지름 corner)을 높이 h 로 뽑고, 윗·아랫 모서리를 top/bottom 만큼 둥글린다."""
    bm = bmesh.new()
    corner = min(corner, w / 2 - 1e-3, d / 2 - 1e-3)
    pts = []
    for cx, cy, a0 in ((w / 2 - corner, d / 2 - corner, 0), (-w / 2 + corner, d / 2 - corner, 90),
                       (-w / 2 + corner, -d / 2 + corner, 180), (w / 2 - corner, -d / 2 + corner, 270)):
        for k in range(13):
            a = math.radians(a0 + 90 * k / 12)
            pts.append((cx + corner * math.cos(a), cy + corner * math.sin(a)))
    # 아래에서 위로 여러 층을 쌓고, 윗·아래 층은 안쪽으로 들여 둥근 어깨를 만든다
    layers = []
    steps = 8
    for i in range(steps + 1):
        t = i / steps
        a = (math.pi / 2) * (1 - t)
        layers.append((z0 + bottom * (1 - math.cos(a)), -bottom * (1 - math.sin(a))) if bottom else None)
    rings = []
    profile = []
    for i in range(steps + 1):  # 아래 어깨
        a = (math.pi / 2) * i / steps
        profile.append((z0 + bottom - bottom * math.cos(a), -bottom + bottom * math.sin(a)))
    for i in range(steps + 1):  # 위 어깨
        a = (math.pi / 2) * i / steps
        profile.append((z0 + h - top + top * math.sin(a), -top + top * math.cos(a)))
    for z, inset in profile:
        ring = []
        for (x, y) in pts:
            # 중심에서 바깥으로 향하는 방향으로 inset 만큼 들인다
            ln = math.hypot(x, y) or 1
            sx = x + inset * (x / ln) * (abs(x) / (w / 2))
            sy = y + inset * (y / ln) * (abs(y) / (d / 2))
            ring.append(bm.verts.new((sx, sy, z)))
        rings.append(ring)
    n = len(pts)
    for r0, r1 in zip(rings, rings[1:]):
        for i in range(n):
            bm.faces.new((r0[i], r0[(i + 1) % n], r1[(i + 1) % n], r1[i]))
    bm.faces.new(list(reversed(rings[0])))
    bm.faces.new(rings[-1])
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    return finish(o, m, 1)


def superellipsoid(name, a, b, c, r, t, m, loc=(0, 0, 0), nu=128, nv=64):
    """조약돌 같은 곡면. r: 가로 단면 둥근 정도(2=타원, 클수록 네모), t: 세로 방향."""
    def cp(w, e):
        v = math.cos(w)
        return math.copysign(abs(v) ** e, v)

    def sp(w, e):
        v = math.sin(w)
        return math.copysign(abs(v) ** e, v)

    bm = bmesh.new()
    rows = []
    for j in range(1, nv):
        v = -math.pi / 2 + math.pi * j / nv
        rows.append([bm.verts.new((a * cp(v, 2 / t) * cp(u, 2 / r), b * cp(v, 2 / t) * sp(u, 2 / r), c * sp(v, 2 / t)))
                     for u in [2 * math.pi * i / nu for i in range(nu)]])
    bottom = bm.verts.new((0, 0, -c))
    top = bm.verts.new((0, 0, c))
    for r0, r1 in zip(rows, rows[1:]):
        for i in range(nu):
            bm.faces.new((r0[i], r0[(i + 1) % nu], r1[(i + 1) % nu], r1[i]))
    for i in range(nu):
        bm.faces.new((rows[0][(i + 1) % nu], rows[0][i], bottom))
        bm.faces.new((rows[-1][i], rows[-1][(i + 1) % nu], top))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    o.location = loc
    return finish(o, m, 1)


def seam_material(name, base_color, groove_z, width=0.022):
    """흰 플라스틱 + 높이 groove_z 에 가는 이음선(물체 좌표 기준)."""
    m = mat(name, base_color, rough=0.32, coat=0.5, sss=0.05)
    nt = m.node_tree
    tc = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    sub = nt.nodes.new("ShaderNodeMath"); sub.operation = "SUBTRACT"; sub.inputs[1].default_value = groove_z
    ab = nt.nodes.new("ShaderNodeMath"); ab.operation = "ABSOLUTE"
    lt = nt.nodes.new("ShaderNodeMath"); lt.operation = "LESS_THAN"; lt.inputs[1].default_value = width
    mix = nt.nodes.new("ShaderNodeMix"); mix.data_type = "RGBA"
    mix.inputs["A"].default_value = (*base_color, 1)
    mix.inputs["B"].default_value = (0.38, 0.39, 0.41, 1)
    nt.links.new(tc.outputs["Object"], sep.inputs[0])
    nt.links.new(sep.outputs["Z"], sub.inputs[0])
    nt.links.new(sub.outputs[0], ab.inputs[0])
    nt.links.new(ab.outputs[0], lt.inputs[0])
    nt.links.new(lt.outputs[0], mix.inputs["Factor"])
    nt.links.new(mix.outputs["Result"], nt.nodes["Principled BSDF"].inputs["Base Color"])
    return m


def studio(target, dist, cam_h=0.32, lens=70, key=1.0):
    s = bpy.context.scene
    target = Vector(target)
    bpy.ops.object.camera_add(location=(0, -dist, target.z + dist * cam_h))
    cam = bpy.context.active_object
    cam.data.lens = lens
    cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
    s.camera = cam

    def area(loc, power, size, color=(1, 1, 1)):
        bpy.ops.object.light_add(type="AREA", location=loc)
        l = bpy.context.active_object
        l.data.energy = power * key
        l.data.size = size
        l.data.color = color
        l.rotation_euler = (target - l.location).to_track_quat("-Z", "Y").to_euler()

    k = dist * dist
    area((-dist * 0.7, -dist * 0.6, target.z + dist * 0.9), 14 * k, dist * 0.7)                 # 주광(왼쪽 위)
    area((dist * 0.9, -dist * 0.3, target.z + dist * 0.2), 4 * k, dist * 0.6, (0.93, 0.96, 1))   # 보조광
    area((dist * 0.2, dist * 0.9, target.z + dist * 0.7), 9 * k, dist * 0.35)                    # 윤곽광

    bpy.ops.mesh.primitive_plane_add(size=dist * 8, location=(0, 0, 0))
    bpy.context.active_object.is_shadow_catcher = True


def pivot_of(objs):
    bpy.ops.object.empty_add(location=(0, 0, 0))
    pv = bpy.context.active_object
    for o in objs:
        o.parent = pv
    return pv


def render_turntable(name, pivot, aspect=0.75, start_deg=-28):
    s = bpy.context.scene
    s.render.resolution_x = WIDTH
    s.render.resolution_y = int(WIDTH * aspect)
    out = os.path.join(OUT, name)
    os.makedirs(out, exist_ok=True)
    for i in range(FRAMES):
        pivot.rotation_euler[2] = math.radians(start_deg) + 2 * math.pi * i / FRAMES
        s.render.filepath = os.path.join(out, f"f{i:03d}.png")
        bpy.ops.render.render(write_still=True)
    print(f"[orchard] {name}: {FRAMES} frames → {out}")


def materials():
    return dict(
        white=mat("white", (0.94, 0.94, 0.93), rough=0.32, coat=0.5, sss=0.05),
        tip=mat("tip", (0.86, 0.87, 0.88), rough=0.6, sss=0.2),
        mesh=mat("mesh", (0.06, 0.06, 0.07), rough=0.75),
        chrome=mat("chrome", (0.9, 0.9, 0.92), rough=0.1, metal=1.0),
        led=mat("led", (0.1, 0.9, 0.3), rough=0.2, emit=(0.15, 1.0, 0.35)),
        groove=mat("groove", (0.42, 0.43, 0.45), rough=0.5),
    )


# ---------- 이어버드 (단위: cm) ----------
def bud(M, pro, side):
    """귀에 들어가는 쪽이 앞(-y)으로, 바깥쪽은 side 방향으로 살짝 돌아간다. 줄기 끝이 원점."""
    parts = []
    stem_len = 1.5 if pro else 1.8
    hz = stem_len + 0.4
    out = Vector((side * 0.35, -1.0, 0.05)).normalized()  # 귀 쪽 방향
    parts.append(superellipsoid("head", 0.72, 0.82, 0.7, 2.2, 2.1, M["white"], loc=(0, 0.05, hz)))
    nozzle_c = Vector((0, 0.05, hz)) + out * 0.62
    parts.append(ellipsoid("nozzle", (0.5, 0.5, 0.46), M["white"], loc=nozzle_c))
    if pro:
        tip_c = Vector((0, 0.05, hz)) + out * 1.05
        parts.append(ellipsoid("tip", (0.5, 0.5, 0.5), M["tip"], loc=tip_c))
        parts[-1].scale = (1, 0.72, 1)
        parts[-1].rotation_euler = (0, 0, math.atan2(out.x, -out.y))
        parts.append(ellipsoid("tiphole", (0.2, 0.05, 0.18), M["mesh"], loc=tip_c + out * 0.34))
        parts[-1].rotation_euler = (0, 0, math.atan2(out.x, -out.y))
    else:
        g = Vector((0, 0.05, hz)) + out * 1.08
        parts.append(ellipsoid("grill", (0.26, 0.04, 0.2), M["mesh"], loc=g))
        parts[-1].rotation_euler = (0, 0, math.atan2(out.x, -out.y))
    parts.append(ellipsoid("mic", (0.13, 0.05, 0.03), M["mesh"], loc=(0, 0.25, hz + 0.69)))
    st = slab("stem", 0.48 if pro else 0.44, 0.4, stem_len + 0.25, 0.19, 0.18, 0.18, M["white"], z0=0.08)
    st.rotation_euler = (math.radians(-5), 0, 0)
    parts.append(st)
    parts.append(slab("stemcap", 0.48 if pro else 0.44, 0.4, 0.12, 0.19, 0.04, 0.1, M["chrome"], z0=0.0))
    return parts


def buds_scene(name, pro):
    reset()
    M = materials()
    objs = []
    for side, x, turn in ((1, -1.2, 22), (-1, 1.2, -22)):
        ps = bud(M, pro, side)
        g = pivot_of(ps)
        g.location = (x, 0, 0)
        g.rotation_euler = (0, 0, math.radians(turn))
        objs.append(g)
    pv = pivot_of(objs)
    studio(target=(0, 0, 1.35), dist=11.5, cam_h=0.22)
    render_turntable(name, pv)


# ---------- 케이스 ----------
def case_scene(name, pro):
    reset()
    M = materials()
    if pro:   # 가로로 넓은 조약돌
        a_, b_, c_, r_, t_ = 3.05, 1.12, 2.3, 3.6, 2.9
    else:     # 거의 정사각형, 위가 더 둥근 조약돌
        a_, b_, c_, r_, t_ = 2.35, 1.1, 2.55, 3.4, 2.8
    groove = c_ * 0.42  # 뚜껑 이음선 높이(중심 기준)
    body = superellipsoid("case", a_, b_, c_, r_, t_, seam_material("case", (0.94, 0.94, 0.93), groove),
                          loc=(0, 0, c_))
    led = ellipsoid("led", (0.07, 0.03, 0.07), M["led"], loc=(0, -b_ - 0.002, c_ * 0.95))
    hinge = rod("hinge", 0.05, (-a_ * 0.3, b_ * 0.93, c_ + groove), (a_ * 0.3, b_ * 0.93, c_ + groove), M["chrome"])
    pv = pivot_of([body, led, hinge])
    studio(target=(0, 0, c_), dist=24.0, cam_h=0.3)
    render_turntable(name, pv)


# ---------- AirPods Max ----------
def max_scene(name, color=(0.78, 0.79, 0.81)):
    reset()
    alu = mat("alu", color, rough=0.34, metal=1.0, aniso=0.5)
    canopy = mat("canopy", (0.14, 0.15, 0.17), rough=0.9)
    steel = mat("steel", (0.86, 0.87, 0.89), rough=0.14, metal=1.0)
    cushion = mat("cushion", (0.1, 0.1, 0.11), rough=0.95)
    parts = []
    R, cz = 7.0, 10.0          # 머리띠 반지름·중심 높이
    cup_x, cup_z = 7.3, 4.6    # 컵 중심
    for side in (1, -1):
        cup = superellipsoid(f"cup{side}", 1.2, 3.3, 4.0, 3.2, 3.6, alu, loc=(side * cup_x, 0, cup_z))
        parts.append(cup)
        pad = superellipsoid(f"pad{side}", 0.75, 3.1, 3.8, 3.0, 3.4, cushion, loc=(side * (cup_x - 1.55), 0, cup_z))
        parts.append(pad)
        # 컵 위에서 머리띠로 올라가는 텔레스코픽 팔(앞뒤 두 줄)
        for y in (1.2, -1.2):
            parts.append(rod(f"arm{side}{y}", 0.13, (side * R, y, cz), (side * cup_x * 0.98, y * 0.6, cup_z + 4.0), steel))
    # 머리띠 프레임: 앞뒤 두 줄 반원
    for y in (1.2, -1.2):
        cu = bpy.data.curves.new(f"frame{y}", "CURVE")
        cu.dimensions = "3D"
        cu.bevel_depth = 0.17
        cu.bevel_resolution = 6
        sp = cu.splines.new("POLY")
        pts = [(R * math.cos(math.radians(t)), y, cz + R * math.sin(math.radians(t)), 1) for t in range(0, 181, 3)]
        sp.points.add(len(pts) - 1)
        for pt, co in zip(sp.points, pts):
            pt.co = co
        fo = bpy.data.objects.new(f"frame{y}", cu)
        bpy.context.collection.objects.link(fo)
        fo.data.materials.append(steel)
        parts.append(fo)
    # 메시 캐노피: 프레임 안쪽에 걸린 넓은 띠
    bm = bmesh.new()
    segs = 48
    rr = R - 0.55
    rings = []
    for i in range(segs + 1):
        a = math.radians(14 + (180 - 28) * i / segs)
        sag = 0.25 * math.sin(math.pi * i / segs)
        rings.append([bm.verts.new((rr * math.cos(a), y, cz + (rr - sag) * math.sin(a))) for y in (1.05, -1.05)])
    for r0, r1 in zip(rings, rings[1:]):
        bm.faces.new((r0[0], r1[0], r1[1], r0[1]))
    me = bpy.data.meshes.new("canopy")
    bm.to_mesh(me)
    bm.free()
    band = bpy.data.objects.new("canopy", me)
    bpy.context.collection.objects.link(band)
    sol = band.modifiers.new("thick", "SOLIDIFY")
    sol.thickness = 0.12
    finish(band, canopy, 1)
    parts.append(band)
    # 디지털 크라운·소음 제어 버튼(오른쪽 컵 위)
    parts.append(rod("crown", 0.45, (cup_x + 0.1, 2.0, cup_z + 3.9), (cup_x + 0.1, 2.0, cup_z + 4.55), steel))
    parts.append(slab("button", 0.5, 1.1, 0.35, 0.2, 0.1, 0.0, steel, z0=cup_z + 3.95))
    parts[-1].location = (cup_x + 0.1, -1.0, 0)
    pv = pivot_of(parts)
    studio(target=(0, 0, 8.0), dist=56.0, cam_h=0.22, lens=70)
    render_turntable(name, pv, aspect=0.8, start_deg=-30)


SCENES = {
    "buds_pro": lambda: buds_scene("buds_pro", True),
    "buds_std": lambda: buds_scene("buds_std", False),
    "case_pro": lambda: case_scene("case_pro", True),
    "case_std": lambda: case_scene("case_std", False),
    "max": lambda: max_scene("max"),
}

for key, fn in SCENES.items():
    if ONLY and key not in ONLY:
        continue
    fn()
