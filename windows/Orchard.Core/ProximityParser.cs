namespace Orchard.Core;

public sealed record AirPodsStatus(
    string Address,
    int ModelId,
    AirPodsModel Model,
    int? Left, int? Right, int? Case,
    bool LeftCharging, bool RightCharging, bool CaseCharging,
    bool LeftInEar, bool RightInEar,
    bool LidOpen,
    int Color)
{
    /// <summary>헤드폰(맥스)은 배터리가 하나라 한쪽 칸에만 값이 온다.</summary>
    public int? Headphones => Left ?? Right;

    public bool AnyInEar => LeftInEar || RightInEar;
}

/// <summary>
/// 에어팟이 늘 내보내는 애플 근접 신호(Proximity Pairing, 종류 0x07)를 해석한다.
/// LibrePods BLEManager.parseProximityMessage 를 옮긴 것.
/// </summary>
public static class ProximityParser
{
    public const ushort AppleCompanyId = 0x004C;

    public static AirPodsStatus? Parse(ReadOnlySpan<byte> d, string address = "")
    {
        if (d.Length < 11 || d[0] != 0x07) return null;

        int modelId = d[3] | (d[4] << 8);
        int status = d[5], pods = d[6], flagsCase = d[7], lid = d[8];

        bool primaryLeft = ((status >> 5) & 1) == 1;
        bool thisInCase = ((status >> 6) & 1) == 1;
        bool xor = primaryLeft ^ thisInCase;
        bool leftInEar = xor ? (status & 0x08) != 0 : (status & 0x02) != 0;
        bool rightInEar = xor ? (status & 0x02) != 0 : (status & 0x08) != 0;

        bool flipped = !primaryLeft;
        int leftNib = flipped ? (pods >> 4) & 0xF : pods & 0xF;
        int rightNib = flipped ? pods & 0xF : (pods >> 4) & 0xF;
        int flags = (flagsCase >> 4) & 0xF;

        return new AirPodsStatus(
            address, modelId, ModelCatalog.Find(modelId),
            Battery(leftNib), Battery(rightNib), Battery(flagsCase & 0xF),
            flipped ? (flags & 0x02) != 0 : (flags & 0x01) != 0,
            flipped ? (flags & 0x01) != 0 : (flags & 0x02) != 0,
            (flags & 0x04) != 0,
            leftInEar, rightInEar,
            ((lid >> 3) & 1) == 0,
            d[9]);
    }

    static int? Battery(int n) => n switch
    {
        <= 0x9 => n * 10,
        <= 0xE => 100,
        _ => null,
    };
}
