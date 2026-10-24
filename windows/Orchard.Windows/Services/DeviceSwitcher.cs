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
        if (ok) await Task.Delay(600); // 윈도우가 기본 출력을 에어팟으로 바꿀 시간
        if (paused) await session.TryPlayAsync();
    }

    /// <summary>에어팟을 이 PC로 가져온다. 연결되면 true.</summary>
    public async Task<bool> TakeOverAsync(PairedDevice target)
    {
        if (_busy) return false;
        _busy = true;
        try
        {
            bool sent = await Task.Run(() => AudioReconnector.Reconnect(target.Address));
            if (!sent)
            {
                Notify?.Invoke($"{target.Name}을 가져오지 못했어요");
                return false;
            }
            for (int i = 0; i < 40; i++)
            {
                await Task.Delay(200);
                if (_monitor.ConnectedDevice?.Address == target.Address)
                {
                    Notify?.Invoke($"{target.Name}을 이 PC로 가져왔어요");
                    return true;
                }
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
