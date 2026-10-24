using Orchard.Core;
using Windows.Devices.Bluetooth.Advertisement;

// 개발용: 주변 애플 근접 신호를 원본 바이트와 해석 결과로 찍는다.
var secs = args.Length > 0 ? int.Parse(args[0]) : 20;
var w = new BluetoothLEAdvertisementWatcher { ScanningMode = BluetoothLEScanningMode.Active };
var seen = new HashSet<string>();
int total = 0, apple = 0; var types = new Dictionary<byte,int>();
w.Received += (_, e) =>
{
    Interlocked.Increment(ref total);
    foreach (var md in e.Advertisement.ManufacturerData)
    {
        if (md.CompanyId != ProximityParser.AppleCompanyId) continue;
        var b = new byte[md.Data.Length];
        Windows.Storage.Streams.DataReader.FromBuffer(md.Data).ReadBytes(b);
        apple++; if (b.Length > 0) lock (types) types[b[0]] = types.GetValueOrDefault(b[0]) + 1;
        if (b.Length < 2 || b[0] != 0x07) continue;
        var hex = Convert.ToHexString(b);
        var key = hex[..Math.Min(hex.Length, 22)];
        if (!seen.Add(key)) continue;
        var s = ProximityParser.Parse(b, e.BluetoothAddress.ToString("X12"));
        Console.WriteLine($"{DateTime.Now:HH:mm:ss} rssi={e.RawSignalStrengthInDBm} {hex}");
        Console.WriteLine($"   → {s}");
    }
};
w.Start();
await Task.Delay(TimeSpan.FromSeconds(secs));
w.Stop();
Console.WriteLine($"끝 ({w.Status}) 전체 {total} · 애플 {apple} · 종류 {string.Join(",", types.Select(t => $"{t.Key:X2}:{t.Value}"))}");
