"""Orchard Link 기준 구현 — shared/link.md 규격대로 shared/link-vectors.json을 만든다.

두 OS의 시험이 이 파일을 읽어 같은 바이트를 만들고 같은 판정을 내리는지 확인한다.
실행: python tools/link_vectors.py
"""
import hashlib
import hmac
import json
import pathlib
import struct

MAGIC, VERSION = 0x4F, 0x01


def encode(key: bytes, type_: int, flags: int, sender: int, time_: int, seq: int, body: str = "") -> bytes:
    head = struct.pack(">BBBBIIB", MAGIC, VERSION, type_, flags, sender, time_, seq) + body.encode("utf-8")
    return head + hmac.new(key, head, hashlib.sha256).digest()[:8]


def main():
    key = bytes(range(32))
    other = bytes(range(32, 64))
    t = 1_793_000_000
    good = [
        dict(name="hello_body", type=1, flags=0x03, sender=0x1A2B3C4D, time=t, seq=7, body="갤럭시 S26"),
        dict(name="want_music", type=2, flags=0x00, sender=0x1A2B3C4D, time=t, seq=8, body=""),
        dict(name="want_call", type=2, flags=0x10, sender=0xDEADBEEF, time=t + 1, seq=255, body=""),
        dict(name="released", type=3, flags=0x01, sender=0x00000001, time=t, seq=0, body=""),
        dict(name="busy", type=4, flags=0x04, sender=0xFFFFFFFF, time=t, seq=1, body=""),
        dict(name="pair_ok", type=6, flags=0x00, sender=0x0BADF00D, time=t, seq=2, body="H1-PC"),
    ]
    for g in good:
        g["hex"] = encode(key, g["type"], g["flags"], g["sender"], g["time"], g["seq"], g["body"]).hex()

    base = bytearray(bytes.fromhex(good[1]["hex"]))
    tampered = bytearray(base)
    tampered[3] ^= 0x01
    bad = [
        dict(name="too_short", hex=base[:20].hex(), reason="length"),
        dict(name="bad_magic", hex=("50" + base.hex()[2:]), reason="magic"),
        dict(name="bad_version", hex=(base.hex()[:2] + "02" + base.hex()[4:]), reason="version"),
        dict(name="tampered_flags", hex=bytes(tampered).hex(), reason="tag"),
        dict(name="wrong_key", hex=encode(other, 2, 0, 0x1A2B3C4D, t, 8).hex(), reason="tag"),
    ]
    out = dict(
        note="tools/link_vectors.py가 만듦 — 손으로 고치지 말 것",
        key_hex=key.hex(),
        now=t,
        good=good,
        bad=bad,
        # 시각 검사: now 기준 ±60초 안이면 통과
        time_cases=[dict(time=t - 60, ok=True), dict(time=t + 60, ok=True), dict(time=t - 61, ok=False), dict(time=t + 61, ok=False)],
        pair_url="orchard://pair?v=1&id=0badf00d&k=" + "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8" + "&n=H1%20PC&a=192.168.0.10,10.0.0.5&p=47820",
        pair_parsed=dict(id=0x0BADF00D, key_hex=key.hex(), name="H1 PC", addrs=["192.168.0.10", "10.0.0.5"], port=47820),
    )
    path = pathlib.Path(__file__).resolve().parents[1] / "shared" / "link-vectors.json"
    path.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{path} · 좋은 신호 {len(good)} · 나쁜 신호 {len(bad)}")


if __name__ == "__main__":
    main()
