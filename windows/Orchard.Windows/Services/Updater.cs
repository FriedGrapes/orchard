using Velopack;
using Velopack.Sources;

namespace Orchard.Windows;

/// <summary>공개 저장소의 GitHub Releases에서 새 버전을 받아 두고, 앱을 끌 때(또는 다음 시작 때) 조용히 바꾼다.</summary>
public sealed class Updater
{
    public const string RepoUrl = "https://github.com/FriedGrapes/orchard";
    readonly UpdateManager _um = new(new GithubSource(RepoUrl, null, false));
    UpdateInfo? _pending;

    public bool IsInstalled => _um.IsInstalled;
    public string CurrentVersion => _um.CurrentVersion?.ToString() ?? typeof(Updater).Assembly.GetName().Version?.ToString(3) ?? "?";
    public string? PendingVersion => _pending?.TargetFullRelease.Version.ToString();

    public event Action? StateChanged;

    /// <summary>새 버전이 있으면 내려받아 둔다. 반환: 받아 둔 버전(없으면 null).</summary>
    public async Task<string?> CheckAndDownloadAsync()
    {
        if (!_um.IsInstalled) return null; // 개발 중 실행(설치 안 함)
        try
        {
            var info = await _um.CheckForUpdatesAsync();
            if (info == null) return null;
            await _um.DownloadUpdatesAsync(info);
            _pending = info;
            Log.Write($"업데이트 준비됨: {PendingVersion}");
            StateChanged?.Invoke();
            return PendingVersion;
        }
        catch (Exception e)
        {
            Log.Write($"업데이트 확인 실패: {e.Message}");
            return null;
        }
    }

    /// <summary>지금 바로 적용하고 다시 시작한다.</summary>
    public void ApplyAndRestart()
    {
        if (_pending != null) _um.ApplyUpdatesAndRestart(_pending, new[] { "--background" });
    }

    /// <summary>앱이 꺼질 때 받아 둔 업데이트를 적용한다(재시작 안 함).</summary>
    public void ApplyOnExit()
    {
        if (_pending != null) _um.WaitExitThenApplyUpdates(_pending, silent: true, restart: false);
    }
}
