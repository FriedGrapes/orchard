using Windows.Media.Control;

namespace Orchard.Windows;

/// <summary>귀에서 빼면 재생 중인 미디어를 멈추고, 다시 끼면 우리가 멈춘 것만 이어서 재생한다.</summary>
public sealed class MediaControl
{
    GlobalSystemMediaTransportControlsSessionManager? _mgr;
    string? _pausedByUs;

    async Task<GlobalSystemMediaTransportControlsSessionManager> Manager() =>
        _mgr ??= await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();

    public async Task PauseAsync()
    {
        try
        {
            var s = (await Manager()).GetCurrentSession();
            if (s?.GetPlaybackInfo().PlaybackStatus != GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing) return;
            if (await s.TryPauseAsync())
            {
                _pausedByUs = s.SourceAppUserModelId;
                Log.Write($"일시정지: {_pausedByUs}");
            }
        }
        catch (Exception e) { Log.Write($"일시정지 실패: {e.Message}"); }
    }

    public async Task ResumeAsync()
    {
        if (_pausedByUs == null) return;
        try
        {
            var s = (await Manager()).GetSessions().FirstOrDefault(x => x.SourceAppUserModelId == _pausedByUs);
            if (s != null && s.GetPlaybackInfo().PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Paused)
                await s.TryPlayAsync();
        }
        catch (Exception e) { Log.Write($"다시 재생 실패: {e.Message}"); }
        finally { _pausedByUs = null; }
    }
}
