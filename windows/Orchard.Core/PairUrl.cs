using System.Globalization;

namespace Orchard.Core;

/// <summary>PC가 QR로 보여 주는 짝짓기 주소: orchard://pair?v=1&amp;id=&amp;k=&amp;n=&amp;a=&amp;p=</summary>
public sealed record PairUrl(uint Id, byte[] Key, string Name, IReadOnlyList<string> Addrs, int Port)
{
    public string ToUrl() =>
        $"orchard://pair?v=1&id={Id:x8}&k={B64Url(Key)}&n={Uri.EscapeDataString(Name)}&a={string.Join(',', Addrs)}&p={Port}";

    public static PairUrl? Parse(string url)
    {
        const string prefix = "orchard://pair?";
        if (!url.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return null;
        var q = url[prefix.Length..].Split('&', StringSplitOptions.RemoveEmptyEntries)
            .Select(kv => kv.Split('=', 2))
            .ToDictionary(kv => kv[0], kv => kv.Length > 1 ? Uri.UnescapeDataString(kv[1]) : "");
        try
        {
            if (q.GetValueOrDefault("v") != "1") return null;
            var key = FromB64Url(q["k"]);
            if (key.Length != 32) return null;
            var addrs = q.GetValueOrDefault("a", "").Split(',', StringSplitOptions.RemoveEmptyEntries);
            return new PairUrl(uint.Parse(q["id"], NumberStyles.HexNumber), key, q.GetValueOrDefault("n", ""), addrs,
                int.Parse(q.GetValueOrDefault("p", LinkPacket.Port.ToString())));
        }
        catch (Exception e) when (e is KeyNotFoundException or FormatException or OverflowException) { return null; }
    }

    static string B64Url(byte[] b) => Convert.ToBase64String(b).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    static byte[] FromB64Url(string s)
    {
        s = s.Replace('-', '+').Replace('_', '/');
        return Convert.FromBase64String(s.PadRight(s.Length + (4 - s.Length % 4) % 4, '='));
    }
}
