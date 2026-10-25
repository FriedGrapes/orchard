using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace Orchard.Core;

/// <summary>폰·PC가 주고받는 신호의 종류(shared/link.md).</summary>
public enum LinkType : byte { Hello = 1, Want = 2, Released = 3, Busy = 4, Pair = 5, PairOk = 6 }

[Flags]
public enum LinkFlags : byte
{
    None = 0,
    /// <summary>에어팟 소리가 지금 이 기기에 있음.</summary>
    HasAudio = 0x01,
    Playing = 0x02,
    InCall = 0x04,
    /// <summary>WANT가 전화 때문(없으면 음악 때문).</summary>
    ForCall = 0x10,
}

/// <summary>
/// 신호 한 개. 모양: 'O' · 판 1 · 종류 · 상태 · 기기 번호 4 · 시각 4 · 순번 1 · 본문 · HMAC-SHA256 앞 8바이트.
/// 바이트는 tools/link_vectors.py(기준 구현)와 똑같아야 한다.
/// </summary>
public sealed record LinkPacket(LinkType Type, LinkFlags Flags, uint Sender, uint Time, byte Seq, string Body = "")
{
    public const byte Magic = 0x4F, Version = 0x01;
    public const int HeadLength = 13, TagLength = 8, MinLength = HeadLength + TagLength, MaxBody = 200;
    /// <summary>BLE 제조사 데이터 회사 번호(시험·내부용으로 비워 둔 번호).</summary>
    public const ushort BleCompanyId = 0xFFFF;
    public const int Port = 47820;

    public byte[] Encode(byte[] key)
    {
        var body = Encoding.UTF8.GetBytes(Body);
        if (body.Length > MaxBody) throw new ArgumentException("본문이 너무 길어요");
        var buf = new byte[HeadLength + body.Length + TagLength];
        buf[0] = Magic; buf[1] = Version; buf[2] = (byte)Type; buf[3] = (byte)Flags;
        BinaryPrimitives.WriteUInt32BigEndian(buf.AsSpan(4), Sender);
        BinaryPrimitives.WriteUInt32BigEndian(buf.AsSpan(8), Time);
        buf[12] = Seq;
        body.CopyTo(buf, HeadLength);
        Tag(key, buf.AsSpan(0, HeadLength + body.Length)).CopyTo(buf.AsSpan(HeadLength + body.Length));
        return buf;
    }

    /// <summary>열쇠를 고르려고 확인 전에 보낸 기기 번호만 읽는다. 모양이 틀리면 null.</summary>
    public static uint? PeekSender(ReadOnlySpan<byte> raw) =>
        raw.Length >= MinLength && raw[0] == Magic && raw[1] == Version ? BinaryPrimitives.ReadUInt32BigEndian(raw[4..]) : null;

    /// <summary>모양과 확인값이 맞으면 신호, 아니면 null. 시각·되풀이 검사는 <see cref="ReplayGuard"/>.</summary>
    public static LinkPacket? Decode(ReadOnlySpan<byte> raw, byte[] key)
    {
        if (PeekSender(raw) is not uint sender) return null;
        int signed = raw.Length - TagLength;
        if (signed - HeadLength > MaxBody) return null;
        if (!CryptographicOperations.FixedTimeEquals(Tag(key, raw[..signed]), raw[signed..])) return null;
        string body;
        try { body = new UTF8Encoding(false, true).GetString(raw[HeadLength..signed]); }
        catch (DecoderFallbackException) { return null; }
        return new LinkPacket((LinkType)raw[2], (LinkFlags)raw[3], sender, BinaryPrimitives.ReadUInt32BigEndian(raw[8..]), raw[12], body);
    }

    static byte[] Tag(byte[] key, ReadOnlySpan<byte> data) => HMACSHA256.HashData(key, data)[..TagLength];

    public static uint UnixNow() => (uint)DateTimeOffset.UtcNow.ToUnixTimeSeconds();
}
