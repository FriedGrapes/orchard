"""가짜 폰 — 윈도우 앱의 `Orchard.exe --link-selftest <폴더>`와 진짜 UDP로 신호를 주고받는다.

순서: QR 주소(pair_url.txt) 읽기 → PAIR 보내고 PAIR_OK 받기 → WANT 보내고 RELEASED 받기 → PC가 보낸 WANT에 BUSY로 답하기.
실행: python tools/link_fake_phone.py <폴더>   (윈도우 앱보다 먼저 또는 곧바로 실행)
"""
import base64
import os
import socket
import struct
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from link_vectors import encode  # noqa: E402

PORT = 47820
SELF = 0x0FA4E001
NAME = "가짜 폰"


def decode(key, raw):
    import hashlib
    import hmac
    if len(raw) < 21 or raw[0] != 0x4F or raw[1] != 1:
        return None
    if not hmac.compare_digest(hmac.new(key, raw[:-8], hashlib.sha256).digest()[:8], raw[-8:]):
        return None
    _, _, t, flags, sender, tm, seq = struct.unpack(">BBBBIIB", raw[:13])
    return dict(type=t, flags=flags, sender=sender, time=tm, seq=seq, body=raw[13:-8].decode())


def main(folder):
    path = os.path.join(folder, "pair_url.txt")
    for _ in range(200):
        if os.path.exists(path):
            break
        time.sleep(0.1)
    url = open(path, encoding="utf-8").read().strip()
    q = dict(urllib.parse.parse_qsl(url.split("?", 1)[1]))
    k = q["k"]
    key = base64.urlsafe_b64decode(k + "=" * (-len(k) % 4))
    pc = int(q["id"], 16)
    addrs = q["a"].split(",")
    print(f"QR 읽음: {q['n']} · 주소 {addrs}")

    # 윈도우 앱이 같은 컴퓨터에서 47820을 쓰므로, 가짜 폰은 다른 포트에서 보내고 받는다(답장은 보낸 곳으로 온다)
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.bind(("0.0.0.0", 0))
    s.settimeout(0.3)
    seq = 0

    def send(t, flags=0, body=""):
        nonlocal seq
        seq += 1
        raw = encode(key, t, flags, SELF, int(time.time()), seq & 0xFF, body)
        for a in addrs:
            s.sendto(raw, (a, PORT))

    def wait(want_types, seconds):
        end = time.time() + seconds
        while time.time() < end:
            try:
                raw, frm = s.recvfrom(512)
            except socket.timeout:
                continue
            p = decode(key, raw)
            if p and p["sender"] == pc and p["type"] in want_types:
                return p, frm
        return None, None

    ok = True
    t0 = time.time()
    p = None
    while p is None and time.time() - t0 < 3:
        send(5, 0, NAME)
        p, _ = wait({6}, 0.5)
    print("PAIR_OK", "받음" if p else "못 받음", p and p["body"])
    ok &= p is not None

    t0 = time.time()
    send(2)
    p, _ = wait({3, 4}, 1.5)
    print("WANT → ", {3: "RELEASED", 4: "BUSY"}.get(p and p["type"]), f"{(time.time() - t0) * 1000:.0f}ms")
    ok &= p is not None and p["type"] == 3

    p, frm = wait({2}, 15)
    print("PC의 WANT", "받음" if p else "못 받음")
    if p:
        seq += 1
        s.sendto(encode(key, 4, 0x04, SELF, int(time.time()), seq & 0xFF), frm)
    ok &= p is not None
    print("끝:", "통과" if ok else "실패")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main(sys.argv[1])
