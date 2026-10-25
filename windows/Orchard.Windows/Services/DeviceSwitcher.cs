using Orchard.Core;
using Windows.Media.Control;

namespace Orchard.Windows;

/// <summary>
/// 기기 간 전환. 자동: 이 PC에서 재생이 시작되면(에어팟이 근처에서 귀에 끼워져 있을 때) 에어팟을 이 PC로 가져온다.
/// 수동: <see cref="TakeOverAsync"/>를 버튼에서 부른다.
/// </summary>
public sealed class DeviceSwitcher
{
    readonly AirPodsMonitor _monitor;
    readonly Settings _settings;
    GlobalSystemMediaTransportControlsSessionManager? _mgr;
    readonly HashSet<string> _hooked = new();
    readonly Dictionary<string, GlobalSystemMediaTransportControlsSessionPlaybackStatus> _last = new();
    bool _busy;
    DateTimeOffset _lastAuto;

    public event Action<string>? Notify;

    public DeviceSwitcher(AirPodsMonitor monitor, Settings settings)
    {
        _monitor = monitor;
        _settings = settings;
    }

    public async Task StartAsync()
    {
        _mgr = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
        _mgr.SessionsChanged += (_, _) => Hook();
        Hook();
        // 에어팟별 '같은 기기' 표시(컨테이너 ID)를 미리 읽어 둔다 — 연결된 뒤에는 못 읽는 PC가 있다
        var paired = _monitor.Paired.ToList();
        _ = Task.Run(() => { foreach (var p in paired) AudioReconnector.Prepare(p.Address); });
    }

    void Hook()
    {
        if (_mgr == null) return;
        foreach (var s in _mgr.GetSessions())
        {
            var id = s.SourceAppUserModelId;
            if (!_hooked.Add(id)) continue;
            _last[id] = s.GetPlaybackInfo().PlaybackStatus;
            s.PlaybackInfoChanged += (sess, _) => OnPlayback(sess);
        }
    }

    void OnPlayback(GlobalSystemMediaTransportControlsSession s)
    {
        var id = s.SourceAppUserModelId;
        var now = s.GetPlaybackInfo().PlaybackStatus;
        _last.TryGetValue(id, out var before);
        _last[id] = now;
        if (now != GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing || before == now) return;
        if (!_settings.AutoSwitch) return;
        // 가져오기에 실패하면 '멈춤 → 다시 재생'이 또 재생 시작으로 잡혀 되풀이되지 않게 15초 쉰다
        if (DateTimeOffset.Now - _lastAuto < TimeSpan.FromSeconds(15)) return;
        _ = AutoAsync(s);
    }

    /// <summary>지금 가져올 수 있는 에어팟: 근처에서 보이고, 이 PC와는 연결 안 된 페어링 기기.</summary>
    public PairedDevice? Candidate(out AirPodsStatus? status)
    {
        status = _monitor.Current;
        if (status == null || _monitor.ConnectedDevice != null) return null;
        var id = status.ModelId;
        return _monitor.Paired.FirstOrDefault(p => p.ProductId == id);
    }

    async Task AutoAsync(GlobalSystemMediaTransportControlsSession session)
    {
        var target = Candidate(out var status);
        if (target == null || status == null) return;
        _lastAuto = DateTimeOffset.Now;
        if (!status.AnyInEar)
        {
            Log.Write("자동 전환 건너뜀: 에어팟을 끼고 있지 않음");
            return;
        }
        Log.Write($"자동 전환: {session.SourceAppUserModelId} 재생 → {target.Name} 가져오기");
        // 스피커로 소리가 새지 않게: 스피커를 음소거하고 재생도 멈췄다가, 에어팟이 연결되면 이어서 재생한다.
        // (일시정지를 무시하는 앱이 있어도 음소거 덕분에 스피커로는 나오지 않는다. 실패해도 끝나면 음소거를 푼다)
        using var guard = SpeakerGuard.Engage();
        bool paused = await session.TryPauseAsync();
        bool ok = await TakeOverAsync(target);
        if (ok) await Task.Delay(150); // 기본 출력이 막 바뀐 직후 첫 소리가 잘리지 않게
        if (paused) await session.TryPlayAsync();
    }

    /// <summary>
    /// 에어팟을 이 PC로 가져온다. 소리가 나갈 준비가 되면(스테레오 끝점이 활성) 바로 기본 출력으로 정하고 true.
    /// 예전에는 '연결됨' 신호를 200ms 간격으로 기다린 뒤 0.6초를 더 쉬었고, 기본 출력은 바꾸지 않았다.
    /// </summary>
    public async Task<bool> TakeOverAsync(PairedDevice target)
    {
        if (_busy) return false;
        _busy = true;
        try
        {
            var sw = System.Diagnostics.Stopwatch.StartNew();
            bool sent = await Task.Run(() => AudioReconnector.Reconnect(target.Address));
            if (!sent)
            {
                Notify?.Invoke($"{target.Name}을 가져오지 못했어요");
                return false;
            }
            for (int i = 0; i < 100; i++)
            {
                await Task.Delay(100);
                var endpoint = await Task.Run(() => AudioReconnector.ActiveStereoEndpoint(target.Address));
                if (endpoint == null) continue;
                await Task.Run(() => AudioReconnector.MakeDefault(endpoint));
                Log.Write($"가져옴: {target.Name} · {sw.ElapsedMilliseconds}ms");
                Notify?.Invoke($"{target.Name}을 이 PC로 가져왔어요");
                return true;
            }
            Log.Write($"가져오기 시간 초과: {target.Name}");
            Notify?.Invoke($"{target.Name}이 응답하지 않아요. 다른 기기에서 쓰는 중인지 확인해 주세요");
            return false;
        }
        finally
        {
            _busy = false;
        }
    }
}
