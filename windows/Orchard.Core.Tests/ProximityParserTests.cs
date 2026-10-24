using Orchard.Core;

namespace Orchard.Core.Tests;

public class ProximityParserTests
{
    // Apple 근접 신호(제조사 데이터, 회사 ID 0x004C 뒤) 한 덩어리를 만든다.
    static byte[] Packet(ushort modelId, byte status, byte pods, byte flagsCase, byte lid = 0x00, byte color = 0x00)
    {
        var d = new byte[27];
        d[0] = 0x07; d[1] = 0x19; d[2] = 0x01;
        d[3] = (byte)(modelId & 0xFF); d[4] = (byte)(modelId >> 8);
        d[5] = status; d[6] = pods; d[7] = flagsCase; d[8] = lid; d[9] = color; d[10] = 0x05;
        return d;
    }

    [Fact]
    public void 다른_종류의_신호는_무시한다()
    {
        Assert.Null(ProximityParser.Parse(new byte[] { 0x10, 0x05, 0x01 }));
        var p = Packet(0x2014, 0, 0, 0); p[0] = 0x12;
        Assert.Null(ProximityParser.Parse(p));
        Assert.Null(ProximityParser.Parse(Packet(0x2014, 0, 0, 0)[..9]));
    }

    [Fact]
    public void 맥스2를_맥스2로_알아본다()
    {
        var s = ProximityParser.Parse(Packet(0x202D, 0x20, 0xF8, 0x0F))!;
        Assert.Equal(0x202D, s.ModelId);
        Assert.Equal("AirPods Max 2", s.Model.Name);
        Assert.Equal(DeviceKind.Headphones, s.Model.Kind);
        Assert.Equal(80, s.Headphones);
    }

    [Fact]
    public void 모르는_모델은_번호와_함께_알_수_없음으로()
    {
        var s = ProximityParser.Parse(Packet(0x2099, 0x20, 0x55, 0x05))!;
        Assert.Equal("AirPods (0x2099)", s.Model.Name);
        Assert.Equal(DeviceKind.Unknown, s.Model.Kind);
    }

    [Fact]
    public void 왼쪽이_기준일_때_배터리_충전_귀착용()
    {
        // status 0x28: 왼쪽이 기준(0x20), 케이스 밖 → 귀 비트가 뒤바뀌어 0x08 = 왼쪽 귀에
        // pods 0x73: 아래 4비트 왼쪽 3(30%), 위 4비트 오른쪽 7(70%)
        // flagsCase 0x16: 위 4비트 flags=0x1(왼쪽 충전), 아래 4비트 케이스 6(60%)
        var s = ProximityParser.Parse(Packet(0x2027, 0x28, 0x73, 0x16, lid: 0x00))!;
        Assert.Equal("AirPods Pro 3", s.Model.Name);
        Assert.Equal(30, s.Left); Assert.Equal(70, s.Right); Assert.Equal(60, s.Case);
        Assert.True(s.LeftCharging); Assert.False(s.RightCharging); Assert.False(s.CaseCharging);
        Assert.True(s.LeftInEar); Assert.False(s.RightInEar);
        Assert.True(s.LidOpen);
    }

    [Fact]
    public void 오른쪽이_기준이면_좌우를_뒤집는다()
    {
        var s = ProximityParser.Parse(Packet(0x2030, 0x00, 0x73, 0x46, lid: 0x08))!;
        Assert.Equal(70, s.Left); Assert.Equal(30, s.Right);
        Assert.True(s.CaseCharging);
        Assert.False(s.LidOpen);
    }

    [Fact]
    public void 값_F는_모름_A부터는_100()
    {
        var s = ProximityParser.Parse(Packet(0x201B, 0x20, 0xFA, 0x0F))!;
        Assert.Equal(100, s.Left); Assert.Null(s.Right); Assert.Null(s.Case);
    }
}

public class PairedDeviceTests
{
    [Theory]
    [InlineData(@"BTHENUM\{0000110B-0000-1000-8000-00805F9B34FB}_VID&0001004C_PID&202D\5&1A58F167&0&70F94AB625E2_C00000000", 0x202D, "70F94AB625E2")]
    [InlineData(@"BTHENUM\{0000111E-0000-1000-8000-00805F9B34FB}_HCIBYPASS_VID&0001004C_PID&2027\5&1A58F167&0&BC804EA9B456_C00000000", 0x2027, "BC804EA9B456")]
    public void 장치ID에서_애플_제품번호와_주소를_꺼낸다(string id, int pid, string addr)
    {
        var r = PairedDevice.TryParse(id, "x");
        Assert.NotNull(r);
        Assert.Equal(pid, r!.ProductId);
        Assert.Equal(addr, r.Address);
    }

    [Fact]
    public void 애플이_아니면_무시()
    {
        Assert.Null(PairedDevice.TryParse(@"BTHENUM\{0000110B-0000-1000-8000-00805F9B34FB}_VID&00010006_PID&0001\5&1&0&AABBCCDDEEFF_C00000000", "x"));
    }
}

public class DeviceSelectorTests
{
    static AirPodsStatus S(ushort model, string addr) =>
        ProximityParser.Parse(new byte[] { 0x07, 0x19, 0x01, (byte)(model & 0xFF), (byte)(model >> 8), 0x20, 0x55, 0x05, 0, 0, 5, 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 })! with { Address = addr };

    [Fact]
    public void 내_기기_모델만_고르고_신호가_센_쪽()
    {
        var sel = new DeviceSelector(TimeSpan.FromSeconds(15));
        var t0 = DateTimeOffset.UnixEpoch;
        sel.Add(S(0x2014, "stranger"), -40, t0);        // 남의 Pro 2 (가깝지만 내 모델 아님)
        sel.Add(S(0x202D, "far"), -85, t0);
        sel.Add(S(0x202D, "near"), -55, t0);
        var best = sel.Pick(new HashSet<int> { 0x202D, 0x2027 }, t0);
        Assert.Equal("near", best!.Address);
    }

    [Fact]
    public void 오래된_신호는_버린다()
    {
        var sel = new DeviceSelector(TimeSpan.FromSeconds(15));
        var t0 = DateTimeOffset.UnixEpoch;
        sel.Add(S(0x202D, "a"), -50, t0);
        Assert.Null(sel.Pick(new HashSet<int> { 0x202D }, t0.AddSeconds(20)));
    }

    [Fact]
    public void 너무_먼_신호는_남의_기기로_본다()
    {
        var sel = new DeviceSelector(TimeSpan.FromSeconds(15));
        var t0 = DateTimeOffset.UnixEpoch;
        sel.Add(S(0x202D, "a"), -95, t0);
        Assert.Null(sel.Pick(new HashSet<int> { 0x202D }, t0));
    }
}
