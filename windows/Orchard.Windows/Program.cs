using Velopack;

namespace Orchard.Windows;

public static class Program
{
    [STAThread]
    public static void Main(string[] args)
    {
        // Velopack: 설치·업데이트·제거 때 잠깐 실행되는 단계를 먼저 처리한다.
        // 시작 등록은 앱 안에서(App.OnStartup) 처음 한 번 한다 — 조용히 설치하면 '첫 실행' 단계가 없어 빠졌다(0.1.0).
        VelopackApp.Build()
            .OnBeforeUninstallFastCallback(_ => AutoStart.Set(false))
            .Run();

        // 개발·확인용: --test-reconnect <블루투스 주소> — 다시 연결 명령만 보내고 결과를 기록한 뒤 끝낸다
        int t = Array.IndexOf(args, "--test-reconnect");
        if (t >= 0 && t + 1 < args.Length)
        {
            if (args.Contains("--test-mute")) { using (SpeakerGuard.Engage()) { Thread.Sleep(1500); } Log.Write("--test-mute 끝"); return; }
            bool ok = AudioReconnector.Reconnect(args[t + 1]);
            Log.Write($"--test-reconnect {args[t + 1]} → {(ok ? "명령 전달됨" : "실패")}");
            return;
        }

        using var single = new Mutex(true, @"Local\Orchard.Windows", out bool first);
        if (!first && !args.Contains("--demo")) return; // 확인용 --demo 는 설치된 Orchard 가 켜져 있어도 뜬다

        var app = new App();
        app.InitializeComponent();
        app.Run();
    }
}
