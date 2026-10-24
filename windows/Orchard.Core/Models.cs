namespace Orchard.Core;

public enum DeviceKind { Earbuds, Headphones, Unknown }

/// <summary>애플 블루투스 제품 번호(PID) 하나에 대응하는 모델.</summary>
/// <param name="Inferred">실제 기기 정보로 짐작해 넣은 번호(애플 공식 자료로 확인 전)</param>
public sealed record AirPodsModel(int Id, string Name, DeviceKind Kind, bool HasAnc, bool Inferred = false)
{
    public bool IsHeadphones => Kind == DeviceKind.Headphones;
}

public static class ModelCatalog
{
    // 근접 신호의 3·4번째 바이트(작은 쪽 먼저) = 클래식 블루투스 장치 ID의 PID와 같은 값.
    // shared/models.json 과 같은 내용을 유지한다.
    static readonly AirPodsModel[] All =
    {
        new(0x2002, "AirPods", DeviceKind.Earbuds, false),
        new(0x200F, "AirPods 2", DeviceKind.Earbuds, false),
        new(0x2013, "AirPods 3", DeviceKind.Earbuds, false),
        new(0x2019, "AirPods 4", DeviceKind.Earbuds, false),
        new(0x201B, "AirPods 4 (ANC)", DeviceKind.Earbuds, true),
        new(0x2030, "AirPods 5", DeviceKind.Earbuds, true, Inferred: true),
        new(0x200E, "AirPods Pro", DeviceKind.Earbuds, true),
        new(0x2014, "AirPods Pro 2", DeviceKind.Earbuds, true),
        new(0x2024, "AirPods Pro 2 (USB-C)", DeviceKind.Earbuds, true),
        new(0x2027, "AirPods Pro 3", DeviceKind.Earbuds, true, Inferred: true),
        new(0x200A, "AirPods Max", DeviceKind.Headphones, true),
        new(0x201F, "AirPods Max (USB-C)", DeviceKind.Headphones, true),
        new(0x202D, "AirPods Max 2", DeviceKind.Headphones, true, Inferred: true),
    };

    static readonly Dictionary<int, AirPodsModel> ById = All.ToDictionary(m => m.Id);

    public static IReadOnlyList<AirPodsModel> Models => All;

    public static AirPodsModel Find(int id) =>
        ById.TryGetValue(id, out var m) ? m : new AirPodsModel(id, $"AirPods (0x{id:X4})", DeviceKind.Unknown, false);
}
