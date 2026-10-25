# Orchard 아이콘 2차 시안 — 에어팟 모양 + 요즘 스타일(유리 재질·굵고 단순한 면).
#   python icon_concepts2.py <출력 폴더>
import os, sys
from icon_concepts import HEAD, leaf, render, sheet
import icon_concepts

OUT = sys.argv[1]
icon_concepts.OUT = OUT


def bud(id_, x, y, s=1.0, rot=0.0, flip=False):
    """깔끔한 이어버드 기호: 콩 모양 머리 + 아래로 뻗은 줄기. (x,y)=머리 가운데."""
    sx = -s if flip else s
    # clipPath 안에도 쓸 수 있게 묶음(<g>) 없이 도형 하나로
    return (f'<path id="{id_}" transform="translate({x} {y}) rotate({rot}) scale({sx} {s})" '
            'd="M-96,-6 C-96,-74 -44,-112 12,-112 C70,-112 110,-72 110,-16 C110,30 86,58 58,72 '
            'L58,300 C58,326 40,342 20,342 C0,342 -18,326 -18,300 L-18,92 C-66,86 -96,46 -96,-6 Z"/>')


def grille(x, y, s=1.0, rot=0.0, flip=False, color="#1A1C20"):
    sx = -s if flip else s
    return (f'<g transform="translate({x} {y}) rotate({rot}) scale({sx} {s})">'
            f'<ellipse cx="62" cy="-30" rx="22" ry="17" fill="{color}"/></g>')


def concept_e():
    """글라스 이어버드: 깊은 에메랄드 위 서리 낀 밝은 유리 이어버드 하나(잎은 사과 로고처럼 보여 뺌)."""
    X, Y, S, R = 470, 392, 1.2, -14
    lf, _ = leaf(548, 292, 128, 44, 34)
    B = lambda i: bud(i, X, Y, S, R)
    return HEAD + f'''
<defs>
 <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0E4D3D"/><stop offset="1" stop-color="#04201A"/></linearGradient>
 <radialGradient id="b1" cx="62%" cy="34%" r="38%"><stop offset="0" stop-color="#5FE3A1"/><stop offset="1" stop-color="#5FE3A1" stop-opacity="0"/></radialGradient>
 <radialGradient id="b2" cx="30%" cy="80%" r="40%"><stop offset="0" stop-color="#17A394" stop-opacity=".8"/><stop offset="1" stop-color="#17A394" stop-opacity="0"/></radialGradient>
 <filter id="frost" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="30"/></filter>
 <linearGradient id="fill" x1="0" y1="0" x2=".6" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".78"/><stop offset=".55" stop-color="#EFFFF6" stop-opacity=".5"/><stop offset="1" stop-color="#DFF7EA" stop-opacity=".34"/></linearGradient>
 <linearGradient id="rim" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity="1"/><stop offset=".4" stop-color="#fff" stop-opacity=".25"/><stop offset=".8" stop-color="#fff" stop-opacity=".15"/><stop offset="1" stop-color="#fff" stop-opacity=".7"/></linearGradient>
 <linearGradient id="lf" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#4ED98B"/><stop offset="1" stop-color="#D2FBE0"/></linearGradient>
 <clipPath id="clip">{B("c")}</clipPath>
 <filter id="sh" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="30"/></filter>
 <filter id="soft"><feGaussianBlur stdDeviation="6"/></filter>
</defs>
<rect width="1024" height="1024" fill="url(#bg)"/><rect width="1024" height="1024" fill="url(#b1)"/><rect width="1024" height="1024" fill="url(#b2)"/>
<g opacity=".5" filter="url(#sh)" transform="translate(16 40)" fill="#01140F">{B("s")}</g>
<g clip-path="url(#clip)">
 <g filter="url(#frost)" transform="translate(-30 20)"><rect width="1024" height="1024" fill="url(#bg)"/><rect width="1024" height="1024" fill="url(#b1)"/><rect width="1024" height="1024" fill="url(#b2)"/></g>
 <rect width="1024" height="1024" fill="url(#fill)"/>
 <g fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="26" filter="url(#soft)" transform="translate(-14 -14)">{B("hl")}</g>
</g>
<g fill="none" stroke="url(#rim)" stroke-width="6">{B("r")}</g>
</svg>'''


def concept_f():
    """두 알맹이 열매: 하얀 이어버드 두 개가 머리를 맞대 동그란 열매 꼴, 그 위에 잎. 깊은 숲색 단색 면."""
    lf, _ = leaf(512, 268, 118, 40, 24)
    return HEAD + f'''
<defs>
 <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#14533F"/><stop offset="1" stop-color="#0B3628"/></linearGradient>
 <filter id="sh" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="18"/></filter>
</defs>
<rect width="1024" height="1024" fill="url(#bg)"/>
<g fill="#03251A" opacity=".45" filter="url(#sh)" transform="translate(0 22)">
 {bud("s1", 418, 430, .92, 24, flip=True)}{bud("s2", 606, 430, .92, -24)}
</g>
<g fill="#F7F8F6">
 {bud("l", 418, 430, .92, 24, flip=True)}{bud("r", 606, 430, .92, -24)}
</g>
{grille(418, 430, .92, 24, flip=True, color="#14533F")}{grille(606, 430, .92, -24, color="#14533F")}
<path d="{lf}" fill="#7EE2A5"/>
</svg>'''


def concept_g():
    """글라스 케이스: 옅은 민트 빛 바탕 위 반투명 유리 케이스, 상태 표시등 자리에 초록 잎."""
    lf, _ = leaf(494, 640, 74, 26, 50)
    return HEAD + f'''
<defs>
 <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#F3F7F5"/><stop offset="1" stop-color="#D8E4DE"/></linearGradient>
 <radialGradient id="t1" cx="74%" cy="22%" r="55%"><stop offset="0" stop-color="#7FE0AE" stop-opacity=".7"/><stop offset="1" stop-color="#7FE0AE" stop-opacity="0"/></radialGradient>
 <radialGradient id="t2" cx="18%" cy="86%" r="50%"><stop offset="0" stop-color="#8ED4E6" stop-opacity=".5"/><stop offset="1" stop-color="#8ED4E6" stop-opacity="0"/></radialGradient>
 <linearGradient id="glass" x1="0" y1="0" x2=".4" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".96"/><stop offset=".6" stop-color="#fff" stop-opacity=".78"/><stop offset="1" stop-color="#F2F5F4" stop-opacity=".7"/></linearGradient>
 <linearGradient id="rim" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff"/><stop offset=".5" stop-color="#BFCBC5"/><stop offset="1" stop-color="#fff"/></linearGradient>
 <linearGradient id="lf" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#11A65A"/><stop offset="1" stop-color="#4FDB8F"/></linearGradient>
 <filter id="sh" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="34"/></filter>
 <filter id="soft"><feGaussianBlur stdDeviation="10"/></filter>
</defs>
<rect width="1024" height="1024" fill="url(#bg)"/><rect width="1024" height="1024" fill="url(#t1)"/><rect width="1024" height="1024" fill="url(#t2)"/>
<rect x="232" y="318" width="560" height="440" rx="196" fill="#1F3D31" opacity=".28" filter="url(#sh)" transform="translate(0 40)"/>
<rect x="232" y="318" width="560" height="440" rx="196" fill="url(#glass)" stroke="url(#rim)" stroke-width="4"/>
<path d="M300,360 Q512,326 724,360" fill="none" stroke="#fff" stroke-width="14" stroke-linecap="round" opacity=".9" filter="url(#soft)"/>
<path d="M240,486 H784" stroke="#BCC7C1" stroke-width="3"/>
<path d="{lf}" fill="url(#lf)"/>
</svg>'''


CONCEPTS = [("e_glass_bud", concept_e), ("f_two_buds", concept_f), ("g_glass_case", concept_g)]

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, fn in CONCEPTS:
        render(fn(), os.path.join(OUT, name + ".png"))
    sheet([n for n, _ in CONCEPTS]).save(os.path.join(OUT, "sheet2.png"))
    print("ok")
