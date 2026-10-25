namespace Orchard.Core;

/// <summary>shared/link.md 검사 3·4번: 60초 안의 시각만, 같은 신호(기기 번호·시각·순번)는 120초 동안 한 번만.</summary>
public sealed class ReplayGuard
{
    readonly Dictionary<(uint, uint, byte), uint> _seen = new();
    readonly object _gate = new();

    public bool Accept(LinkPacket p, uint now)
    {
        if (Math.Abs((long)p.Time - now) > 60) return false;
        lock (_gate)
        {
            foreach (var k in _seen.Where(kv => now - kv.Value > 120 || kv.Value > now + 60).Select(kv => kv.Key).ToList()) _seen.Remove(k);
            return _seen.TryAdd((p.Sender, p.Time, p.Seq), now);
        }
    }
}
