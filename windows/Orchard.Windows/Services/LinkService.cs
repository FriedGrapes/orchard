using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text.Json;
using Orchard.Core;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Storage.Streams;

namespace Orchard.Windows;

public sealed record LinkPeer(uint Id, string Name, string Key, string? LastAddr = null, DateTimeOffset? LastSeen = null, string? Via = null)
{
    public byte[] KeyBytes => Convert.FromBase64String(Key);
}

public enum LinkResult { Released, Busy, NoAnswer }

/// <summary>
/// 폰과 주고받는 전환 신호(shared/link.md). 와이파이 UDP 47820이 먼저, 답이 없으면 BLE 광고(회사 번호 0xFFFF).
/// 받는 BLE 신호는 <see cref="AirPodsMonitor"/>의 감시가 넘겨준다(<see cref="OnBle"/>).
/// </summary>
public sealed class LinkService : IDisposable
{
    /// <summary>짝 목록 파일. 확인용 실행(--link-selftest)은 진짜 짝 목록을 건드리지 않게 다른 곳을 준다.</summary>
    public static string FilePath { get; set; } = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Orchard", "link.json");

    sealed class Store
    {
        public uint SelfId { get; set; }
        public int Seq { get; set; }
        public List<LinkPeer> Peers { get; set; } = new();
    }

    readonly Store _store;
    readonly object _gate = new();
    readonly ReplayGuard _guard = new();
    UdpClient? _udp;
    readonly System.Threading.Timer _hello;
    TaskCompletionSource<(LinkResult, string)>? _pending;
    PairUrl? _pairing;
    System.Threading.Timer? _pairBeacon;

    /// <summary>지금 이 PC의 상태(에어팟 소리가 여기 있는지 등).</summary>
    public Func<LinkFlags> State { get; set; } = () => LinkFlags.None;
    /// <summary>짝이 에어팟을 원할 때: 재생을 멈추고 에어팟을 놓는다.</summary>
    public Func<Task> Release { get; set; } = () => Task.CompletedTask;
    public event Action<string>? Notify;
    /// <summary>짝짓기 창이 열린 동안 폰이 짝을 맺으면(폰 이름).</summary>
    public event Action<LinkPeer>? Paired;
    public event Action? PeersChanged;

    public IReadOnlyList<LinkPeer> Peers { get { lock (_gate) return _store.Peers.ToList(); } }
    public string SelfName { get; } = Environment.MachineName;

    public LinkService()
    {
        _store = Load();
        _hello = new System.Threading.Timer(_ => SendHello(), null, Timeout.Infinite, Timeout.Infinite);
    }

    public void Start()
    {
        try
        {
            var udp = new UdpClient(AddressFamily.InterNetwork) { EnableBroadcast = true };
            udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            udp.Client.Bind(new IPEndPoint(IPAddress.Any, LinkPacket.Port));
            _udp = udp;
            _ = ReceiveLoop(udp);
            Log.Write($"전환 신호 시작 · 짝 {Peers.Count}개 · 포트 {LinkPacket.Port}");
        }
        catch (Exception e) { Log.Write($"전환 신호(와이파이) 시작 실패: {e.Message}"); }
        _hello.Change(2000, 25_000);
    }

    // ── 가져오는 쪽 ──────────────────────────────────────────

    /// <summary>짝들에게 '에어팟 넘겨 주세요'를 보내고 첫 답을 1.5초까지 기다린다.</summary>
    public async Task<(LinkResult Result, string Peer)> RequestReleaseAsync(bool forCall)
    {
        var peers = Peers;
        if (peers.Count == 0) return (LinkResult.NoAnswer, "");
        var tcs = new TaskCompletionSource<(LinkResult, string)>(TaskCreationOptions.RunContinuationsAsynchronously);
        _pending = tcs;
        var sw = System.Diagnostics.Stopwatch.StartNew();
        var flags = State() | (forCall ? LinkFlags.ForCall : 0);
        var packets = peers.Select(p => (p, raw: Make(LinkType.Want, flags).Encode(p.KeyBytes))).ToList();
        _ = Task.Run(async () =>
        {
            foreach (var delay in new[] { 0, 150, 250 })
            {
                await Task.Delay(delay);
                if (tcs.Task.IsCompleted) return;
                foreach (var (p, raw) in packets) SendUdp(raw, p);
            }
        });
        var first = await Task.WhenAny(tcs.Task, Task.Delay(400));
        if (first != tcs.Task)
        {
            Log.Write("전환 신호: 와이파이 답 없음 → 블루투스로도 보냄");
            foreach (var (_, raw) in packets) Advertise(raw);
            await Task.WhenAny(tcs.Task, Task.Delay(1100));
        }
        _pending = null;
        var r = tcs.Task.IsCompleted ? tcs.Task.Result : (LinkResult.NoAnswer, "");
        Log.Write($"전환 신호: {r.Item1} {r.Item2} · {sw.ElapsedMilliseconds}ms");
        return r;
    }

    // ── 짝짓기 ──────────────────────────────────────────────

    /// <summary>새 열쇠로 QR 주소를 만들고, 창이 열린 동안 1초마다 '여기 있어요'를 브로드캐스트한다.</summary>
    public PairUrl BeginPairing()
    {
        var url = new PairUrl(_store.SelfId, RandomNumberGenerator.GetBytes(32), SelfName, LocalAddresses().Select(a => a.Address.ToString()).ToList(), LinkPacket.Port);
        _pairing = url;
        _pairBeacon?.Dispose();
        _pairBeacon = new System.Threading.Timer(_ =>
        {
            if (_pairing is { } p) SendUdp(Make(LinkType.Hello, State(), SelfName).Encode(p.Key), null);
        }, null, 0, 1000);
        return url;
    }

    public void EndPairing()
    {
        _pairing = null;
        _pairBeacon?.Dispose();
        _pairBeacon = null;
    }

    public void Remove(uint id)
    {
        lock (_gate) { _store.Peers.RemoveAll(p => p.Id == id); Save(); }
        PeersChanged?.Invoke();
    }

    // ── 받기 ────────────────────────────────────────────────

    async Task ReceiveLoop(UdpClient udp)
    {
        while (true)
        {
            UdpReceiveResult r;
            try { r = await udp.ReceiveAsync(); }
            catch (ObjectDisposedException) { return; }
            catch (SocketException) { continue; } // 상대가 닫힌 포트로 보낸 답(ICMP) 같은 것
            try { await Handle(r.Buffer, r.RemoteEndPoint, viaBle: false); }
            catch (Exception e) { Log.Write($"전환 신호 처리 오류: {e.Message}"); }
        }
    }

    /// <summary>BLE 감시가 회사 번호 0xFFFF 신호를 받으면 부른다.</summary>
    public void OnBle(byte[] raw) => _ = Handle(raw, null, viaBle: true);

    async Task Handle(byte[] raw, IPEndPoint? from, bool viaBle)
    {
        if (LinkPacket.PeekSender(raw) is not uint sender || sender == _store.SelfId) return;
        LinkPeer? peer;
        lock (_gate) peer = _store.Peers.FirstOrDefault(p => p.Id == sender);

        LinkPacket? pkt;
        if (peer == null)
        {
            // 모르는 기기: 짝짓기 창이 열려 있고 PAIR일 때만 그 창의 열쇠로 확인
            if (_pairing is not { } pairing || raw[2] != (byte)LinkType.Pair) return;
            pkt = LinkPacket.Decode(raw, pairing.Key);
            if (pkt == null || !_guard.Accept(pkt, LinkPacket.UnixNow())) return;
            peer = new LinkPeer(sender, string.IsNullOrWhiteSpace(pkt.Body) ? "폰" : pkt.Body, Convert.ToBase64String(pairing.Key));
            lock (_gate) { _store.Peers.RemoveAll(p => p.Name == peer.Name && p.Id != sender); _store.Peers.Add(peer); Save(); }
            Log.Write($"짝 맺음: {peer.Name}");
            EndPairing();
            Paired?.Invoke(peer);
            PeersChanged?.Invoke();
        }
        else
        {
            pkt = LinkPacket.Decode(raw, peer.KeyBytes);
            if (pkt == null || !_guard.Accept(pkt, LinkPacket.UnixNow())) return;
        }

        peer = Touch(peer, from, viaBle, pkt.Type is LinkType.Hello or LinkType.Pair && pkt.Body.Length > 0 ? pkt.Body : null);
        switch (pkt.Type)
        {
            case LinkType.Pair:
                Reply(LinkType.PairOk, State(), peer, from, viaBle, SelfName);
                break;
            case LinkType.Want:
                await OnWant(peer, from, viaBle);
                break;
            case LinkType.Released:
                _pending?.TrySetResult((LinkResult.Released, peer.Name));
                break;
            case LinkType.Busy:
                _pending?.TrySetResult((LinkResult.Busy, peer.Name));
                break;
        }
    }

    async Task OnWant(LinkPeer peer, IPEndPoint? from, bool viaBle)
    {
        // 윈도우는 통화 중인지 알 길이 마땅치 않아 BUSY는 보내지 않는다.
        bool had = State().HasFlag(LinkFlags.HasAudio);
        if (had)
        {
            try { await Release(); }
            catch (Exception e) { Log.Write($"넘겨주기 실패: {e.Message}"); }
        }
        Reply(LinkType.Released, State(), peer, from, viaBle);
        Log.Write($"전환 신호: {Josa.IGa(peer.Name)} 에어팟을 원함 → {(had ? "넘겨줌" : "넘길 것 없음")}{(viaBle ? " (블루투스)" : "")}");
        if (had) Notify?.Invoke($"에어팟을 {Josa.Ro(peer.Name)} 넘겼어요");
    }

    LinkPeer Touch(LinkPeer peer, IPEndPoint? from, bool viaBle, string? name)
    {
        lock (_gate)
        {
            var updated = peer with
            {
                LastAddr = from?.ToString() ?? peer.LastAddr,
                LastSeen = DateTimeOffset.Now,
                Via = viaBle ? "ble" : "wifi",
                Name = name ?? peer.Name,
            };
            int i = _store.Peers.FindIndex(p => p.Id == peer.Id);
            if (i >= 0) _store.Peers[i] = updated;
            bool changed = updated.LastAddr != peer.LastAddr || updated.Name != peer.Name;
            if (changed) Save();
            return updated;
        }
    }

    // ── 보내기 ──────────────────────────────────────────────

    LinkPacket Make(LinkType type, LinkFlags flags, string body = "")
    {
        lock (_gate)
        {
            byte seq = (byte)(_store.Seq++ & 0xFF);
            if (_store.Seq % 32 == 0) Save(); // 다시 켜도 같은 순번을 금방 다시 쓰지 않게 가끔 적어 둔다
            return new LinkPacket(type, flags, _store.SelfId, LinkPacket.UnixNow(), seq, body);
        }
    }

    void Reply(LinkType type, LinkFlags flags, LinkPeer peer, IPEndPoint? from, bool viaBle, string body = "")
    {
        var raw = Make(type, flags, viaBle ? "" : body).Encode(peer.KeyBytes);
        if (from != null) Send(raw, from);
        SendUdp(raw, peer);
        if (viaBle) Advertise(raw);
    }

    void SendHello()
    {
        foreach (var p in Peers) SendUdp(Make(LinkType.Hello, State(), SelfName).Encode(p.KeyBytes), p);
    }

    /// <summary>짝의 마지막 주소와 모든 브로드캐스트 주소로 보낸다.</summary>
    void SendUdp(byte[] raw, LinkPeer? peer)
    {
        if (peer?.LastAddr is string a && IPEndPoint.TryParse(a, out var ep))
            Send(raw, ep.Port == 0 ? new IPEndPoint(ep.Address, LinkPacket.Port) : ep);
        Send(raw, new IPEndPoint(IPAddress.Broadcast, LinkPacket.Port));
        foreach (var u in LocalAddresses())
        {
            var addr = u.Address.GetAddressBytes();
            var mask = u.IPv4Mask.GetAddressBytes();
            for (int i = 0; i < 4; i++) addr[i] |= (byte)~mask[i];
            Send(raw, new IPEndPoint(new IPAddress(addr), LinkPacket.Port));
        }
    }

    void Send(byte[] raw, IPEndPoint to)
    {
        try { _udp?.Send(raw, raw.Length, to); }
        catch (Exception e) when (e is SocketException or ObjectDisposedException) { }
    }

    static IEnumerable<UnicastIPAddressInformation> LocalAddresses() =>
        NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType is not NetworkInterfaceType.Loopback and not NetworkInterfaceType.Tunnel)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses)
            .Where(u => u.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(u.Address)
                        && !u.Address.ToString().StartsWith("169.254.") && u.IPv4Mask != null);

    /// <summary>본문 없는 신호를 2초 동안 BLE로 광고한다.</summary>
    void Advertise(byte[] raw)
    {
        if (raw.Length > LinkPacket.MinLength) return; // 본문이 있는 신호는 BLE에 안 맞는다(답장은 본문 없이 만든다)
        try
        {
            var w = new DataWriter();
            w.WriteBytes(raw);
            var pub = new BluetoothLEAdvertisementPublisher();
            pub.Advertisement.ManufacturerData.Add(new BluetoothLEManufacturerData(LinkPacket.BleCompanyId, w.DetachBuffer()));
            pub.StatusChanged += (_, e) => { if (e.Error != global::Windows.Devices.Bluetooth.BluetoothError.Success) Log.Write($"BLE 광고 실패: {e.Error}"); };
            pub.Start();
            _ = Task.Delay(2000).ContinueWith(_ => { try { pub.Stop(); } catch { } });
        }
        catch (Exception e) { Log.Write($"BLE 광고 실패: {e.Message}"); }
    }

    // ── 저장 ────────────────────────────────────────────────

    static Store Load()
    {
        Store? s = null;
        try { if (File.Exists(FilePath)) s = JsonSerializer.Deserialize<Store>(File.ReadAllText(FilePath)); }
        catch (Exception e) { Log.Write($"전환 신호 설정 읽기 실패: {e.Message}"); }
        s ??= new Store();
        if (s.SelfId == 0)
        {
            s.SelfId = BitConverter.ToUInt32(RandomNumberGenerator.GetBytes(4));
            s.Seq = RandomNumberGenerator.GetInt32(256);
            Write(s);
        }
        return s;
    }

    void Save() => Write(_store);

    static void Write(Store s)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            var tmp = FilePath + ".tmp";
            File.WriteAllText(tmp, JsonSerializer.Serialize(s, new JsonSerializerOptions { WriteIndented = true }));
            File.Move(tmp, FilePath, overwrite: true);
        }
        catch (Exception e) { Log.Write($"전환 신호 설정 저장 실패: {e.Message}"); }
    }

    public void Dispose()
    {
        _hello.Dispose();
        _pairBeacon?.Dispose();
        _udp?.Dispose();
        lock (_gate) Save();
    }
}
