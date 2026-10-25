using System.Management;
using Orchard.Core;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Storage.Streams;

namespace Orchard.Windows;

/// <summary>
/// 페어링된 에어팟(장치 ID로 모델을 앎)과 주변 근접 신호를 합쳐 '내 에어팟' 상태를 낸다.
/// 노이즈 제어 같은 AAP 기능은 윈도우가 L2CAP를 열어 주지 않아 2단계(드라이버)에서 한다.
/// </summary>
public sealed class AirPodsMonitor : IDisposable
{
    readonly BluetoothLEAdvertisementWatcher _watcher = new() { ScanningMode = BluetoothLEScanningMode.Active };
    readonly DeviceSelector _selector = new(TimeSpan.FromSeconds(12));
    readonly Dictionary<string, BluetoothDevice> _classic = new();
    readonly System.Threading.Timer _tick;
    List<PairedDevice> _paired = new();
    AirPodsStatus? _last;
    DateTimeOffset _lastRefresh;

    /// <summary>상태가 바뀌거나 사라질 때(null). 백그라운드 스레드에서 온다.</summary>
    public event Action<AirPodsStatus?, PairedDevice?>? Changed;
    /// <summary>BLE로 온 전환 신호(회사 번호 0xFFFF). 백그라운드 스레드에서 온다.</summary>
    public event Action<byte[]>? LinkSignal;

    public PairedDevice? ConnectedDevice { get; private set; }
    public AirPodsStatus? Current => _last;
    public IReadOnlyList<PairedDevice> Paired => _paired;

    public AirPodsMonitor()
    {
        _watcher.Received += OnAdvertisement;
        _watcher.Stopped += (_, e) => Log.Write($"BLE 감시 멈춤: {e.Error}");
        _tick = new System.Threading.Timer(_ => Evaluate(), null, Timeout.Infinite, Timeout.Infinite);
    }

    public async Task StartAsync()
    {
        await RefreshPairedAsync();
        _watcher.Start();
        _tick.Change(1000, 1000);
        Log.Write($"시작 · 페어링된 애플 기기 {_paired.Count}개: {string.Join(", ", _paired.Select(p => $"{p.Name}=0x{p.ProductId:X4}"))}");
    }

    /// <summary>윈도우 장치 목록에서 VID 004C(애플) 블루투스 기기를 주소별로 모은다.</summary>
    public async Task RefreshPairedAsync()
    {
        var found = await Task.Run(() =>
        {
            var list = new Dictionary<string, PairedDevice>();
            try
            {
                using var q = new ManagementObjectSearcher(
                    "SELECT DeviceID, Name FROM Win32_PnPEntity WHERE DeviceID LIKE 'BTHENUM%VID&0001004C%'");
                foreach (var o in q.Get())
                {
                    var p = PairedDevice.TryParse((string)o["DeviceID"], "");
                    if (p != null) list.TryAdd(p.Address, p);
                }
                // 이름은 클래식 기기 노드(BTHENUM\DEV_주소)에 있다.
                using var n = new ManagementObjectSearcher("SELECT DeviceID, Name FROM Win32_PnPEntity WHERE DeviceID LIKE 'BTHENUM\\\\DEV_%'");
                foreach (var o in n.Get())
                {
                    var id = (string)o["DeviceID"];
                    var addr = id.Split('\\')[1].Replace("DEV_", "", StringComparison.OrdinalIgnoreCase).ToUpperInvariant();
                    if (list.TryGetValue(addr, out var p)) list[addr] = p with { Name = (string?)o["Name"] ?? p.Model.Name };
                }
            }
            catch (Exception e) { Log.Write($"장치 목록 읽기 실패: {e.Message}"); }
            return list.Values.ToList();
        });

        foreach (var p in found.Where(p => !_classic.ContainsKey(p.Address)))
        {
            try
            {
                var dev = await BluetoothDevice.FromBluetoothAddressAsync(Convert.ToUInt64(p.Address, 16));
                if (dev == null) continue;
                dev.ConnectionStatusChanged += (_, _) => Evaluate(force: true);
                _classic[p.Address] = dev;
            }
            catch (Exception e) { Log.Write($"{p.Name} 연결 상태 구독 실패: {e.Message}"); }
        }
        _paired = found;
        _lastRefresh = DateTimeOffset.Now;
    }

    void OnAdvertisement(BluetoothLEAdvertisementWatcher sender, BluetoothLEAdvertisementReceivedEventArgs e)
    {
        foreach (var md in e.Advertisement.ManufacturerData)
        {
            // 폰이 보낸 전환 신호(shared/link.md)
            if (md.CompanyId == LinkPacket.BleCompanyId && md.Data.Length == LinkPacket.MinLength)
            {
                var raw = new byte[md.Data.Length];
                DataReader.FromBuffer(md.Data).ReadBytes(raw);
                if (raw[0] == LinkPacket.Magic) LinkSignal?.Invoke(raw);
                continue;
            }
            if (md.CompanyId != ProximityParser.AppleCompanyId || md.Data.Length < 11) continue;
            var bytes = new byte[md.Data.Length];
            DataReader.FromBuffer(md.Data).ReadBytes(bytes);
            var s = ProximityParser.Parse(bytes, e.BluetoothAddress.ToString("X12"));
            if (s == null) continue;
            _selector.Add(s, e.RawSignalStrengthInDBm, DateTimeOffset.Now);
        }
        Evaluate();
    }

    readonly object _evalGate = new();

    void Evaluate(bool force = false)
    {
        lock (_evalGate)
        {
            ConnectedDevice = _paired.FirstOrDefault(p =>
                _classic.TryGetValue(p.Address, out var d) && d.ConnectionStatus == BluetoothConnectionStatus.Connected);

            // 연결된 기기가 있으면 그 모델만, 없으면 페어링된 모든 애플 모델(케이스를 연 순간 팝업용)
            var models = ConnectedDevice != null
                ? new HashSet<int> { ConnectedDevice.ProductId }
                : _paired.Select(p => p.ProductId).ToHashSet();
            var picked = _selector.Pick(models, DateTimeOffset.Now);

            if (!force && Same(picked, _last)) return;
            _last = picked;
            var owner = picked == null ? null
                : ConnectedDevice?.ProductId == picked.ModelId ? ConnectedDevice
                : _paired.FirstOrDefault(p => p.ProductId == picked.ModelId);
            try { Changed?.Invoke(picked, owner); }
            catch (Exception ex) { Log.Write($"상태 알림 처리 오류: {ex}"); }
        }

        // 새로 페어링한 기기를 놓치지 않게 가끔 다시 읽는다.
        if (DateTimeOffset.Now - _lastRefresh > TimeSpan.FromMinutes(2))
        {
            _lastRefresh = DateTimeOffset.Now;
            _ = RefreshPairedAsync();
        }
    }

    // 주소는 계속 바뀌므로 비교에서 뺀다.
    static bool Same(AirPodsStatus? a, AirPodsStatus? b) =>
        a == null || b == null ? a == b : a with { Address = "" } == b with { Address = "" };

    public void Dispose()
    {
        _tick.Dispose();
        try { _watcher.Stop(); } catch { }
        foreach (var d in _classic.Values) d.Dispose();
    }
}
