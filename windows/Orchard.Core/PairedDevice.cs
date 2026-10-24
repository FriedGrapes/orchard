using System.Text.RegularExpressions;

namespace Orchard.Core;

/// <summary>윈도우에 페어링된 애플 기기. 장치 ID의 VID&amp;0001004C_PID&amp;xxxx 로 모델을 안다.</summary>
public sealed partial record PairedDevice(string Address, int ProductId, string Name)
{
    public AirPodsModel Model => ModelCatalog.Find(ProductId);

    [GeneratedRegex(@"VID&0001004C_PID&([0-9A-F]{4})\\[^\\]*&([0-9A-F]{12})_", RegexOptions.IgnoreCase)]
    private static partial Regex IdPattern();

    public static PairedDevice? TryParse(string instanceId, string name)
    {
        var m = IdPattern().Match(instanceId);
        if (!m.Success) return null;
        return new PairedDevice(m.Groups[2].Value.ToUpperInvariant(), Convert.ToInt32(m.Groups[1].Value, 16), name);
    }
}

/// <summary>
/// 주변의 여러 에어팟 신호 중 '내 기기'를 고른다. 근접 신호의 주소는 계속 바뀌므로
/// 페어링된 기기와 같은 모델인지 + 신호 세기로 고른다.
/// </summary>
public sealed class DeviceSelector(TimeSpan staleAfter, int minRssi = -80)
{
    readonly Dictionary<string, (AirPodsStatus Status, short Rssi, DateTimeOffset Seen)> _seen = new();
    readonly object _gate = new();

    public void Add(AirPodsStatus s, short rssi, DateTimeOffset now)
    {
        lock (_gate) _seen[s.Address] = (s, rssi, now);
    }

    public AirPodsStatus? Pick(IReadOnlySet<int> myModels, DateTimeOffset now)
    {
        lock (_gate)
        {
            foreach (var k in _seen.Where(kv => now - kv.Value.Seen > staleAfter).Select(kv => kv.Key).ToList())
                _seen.Remove(k);
            return _seen.Values
                .Where(v => myModels.Contains(v.Status.ModelId) && v.Rssi >= minRssi)
                .OrderByDescending(v => v.Rssi)
                .Select(v => v.Status)
                .FirstOrDefault();
        }
    }
}
