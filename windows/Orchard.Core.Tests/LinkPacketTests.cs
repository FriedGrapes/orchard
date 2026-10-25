using System.Text.Json;
using Orchard.Core;

namespace Orchard.Core.Tests;

/// <summary>shared/link-vectors.json(파이썬 기준 구현이 만든 것)과 바이트 하나까지 같은지 본다. 안드로이드도 같은 파일로 시험한다.</summary>
public class LinkPacketTests
{
    static readonly JsonElement V = Load();

    static JsonElement Load()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "shared", "link-vectors.json"))) dir = dir.Parent;
        return JsonDocument.Parse(File.ReadAllText(Path.Combine(dir!.FullName, "shared", "link-vectors.json"))).RootElement;
    }

    static byte[] Key => Convert.FromHexString(V.GetProperty("key_hex").GetString()!);
    static uint Now => V.GetProperty("now").GetUInt32();

    public static IEnumerable<object[]> Good() => V.GetProperty("good").EnumerateArray().Select(e => new object[] { e.GetProperty("name").GetString()! });
    public static IEnumerable<object[]> Bad() => V.GetProperty("bad").EnumerateArray().Select(e => new object[] { e.GetProperty("name").GetString()! });

    static JsonElement Find(string list, string name) => V.GetProperty(list).EnumerateArray().First(e => e.GetProperty("name").GetString() == name);

    [Theory, MemberData(nameof(Good))]
    public void 좋은_신호를_같은_바이트로_만든다(string name)
    {
        var g = Find("good", name);
        var p = new LinkPacket((LinkType)g.GetProperty("type").GetInt32(), (LinkFlags)g.GetProperty("flags").GetInt32(),
            g.GetProperty("sender").GetUInt32(), g.GetProperty("time").GetUInt32(), (byte)g.GetProperty("seq").GetInt32(), g.GetProperty("body").GetString()!);
        Assert.Equal(g.GetProperty("hex").GetString(), Convert.ToHexString(p.Encode(Key)).ToLowerInvariant());
    }

    [Theory, MemberData(nameof(Good))]
    public void 좋은_신호를_읽고_확인한다(string name)
    {
        var g = Find("good", name);
        var raw = Convert.FromHexString(g.GetProperty("hex").GetString()!);
        Assert.Equal(g.GetProperty("sender").GetUInt32(), LinkPacket.PeekSender(raw));
        var p = LinkPacket.Decode(raw, Key);
        Assert.NotNull(p);
        Assert.Equal((LinkType)g.GetProperty("type").GetInt32(), p!.Type);
        Assert.Equal(g.GetProperty("body").GetString(), p.Body);
        Assert.Equal((byte)g.GetProperty("seq").GetInt32(), p.Seq);
    }

    [Theory, MemberData(nameof(Bad))]
    public void 나쁜_신호는_버린다(string name)
    {
        var raw = Convert.FromHexString(Find("bad", name).GetProperty("hex").GetString()!);
        Assert.Null(LinkPacket.Decode(raw, Key));
    }

    [Fact]
    public void 시각은_60초_안만_받는다()
    {
        foreach (var c in V.GetProperty("time_cases").EnumerateArray())
        {
            var g = new ReplayGuard();
            var p = new LinkPacket(LinkType.Want, 0, 1, c.GetProperty("time").GetUInt32(), 0);
            Assert.Equal(c.GetProperty("ok").GetBoolean(), g.Accept(p, Now));
        }
    }

    [Fact]
    public void 같은_신호가_두_번_오면_한_번만_받는다()
    {
        var g = new ReplayGuard();
        var p = new LinkPacket(LinkType.Want, 0, 7, Now, 3);
        Assert.True(g.Accept(p, Now));
        Assert.False(g.Accept(p, Now + 1));
        Assert.True(g.Accept(p with { Seq = 4 }, Now + 1));
        Assert.True(g.Accept(p with { Sender = 8 }, Now + 1));
        // 120초가 지나면 기억에서 지운다(그때는 시각 검사가 막는다)
        Assert.False(g.Accept(p, Now + 200));
    }

    [Fact]
    public void QR_주소를_읽고_다시_만든다()
    {
        var url = V.GetProperty("pair_url").GetString()!;
        var want = V.GetProperty("pair_parsed");
        var p = PairUrl.Parse(url)!;
        Assert.Equal(want.GetProperty("id").GetUInt32(), p.Id);
        Assert.Equal(want.GetProperty("key_hex").GetString(), Convert.ToHexString(p.Key).ToLowerInvariant());
        Assert.Equal(want.GetProperty("name").GetString(), p.Name);
        Assert.Equal(want.GetProperty("addrs").EnumerateArray().Select(a => a.GetString()), p.Addrs);
        Assert.Equal(want.GetProperty("port").GetInt32(), p.Port);
        var again = PairUrl.Parse(p.ToUrl())!;
        Assert.Equal(p.Key, again.Key);
        Assert.Equal(p.Addrs, again.Addrs);
        Assert.Equal(p.Name, again.Name);
        Assert.Null(PairUrl.Parse("https://example.com/?k=1"));
    }
}
