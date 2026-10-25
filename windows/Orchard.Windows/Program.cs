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
            // --test-list: 이 주소의 오디오 끝점·필터를 기록(읽기만)
            if (args.Contains("--test-list"))
            {
                foreach (var e in AudioReconnector.Find(args[t + 1])) Log.Write($"끝점 {e.Name} · 활성 {e.Active} · 필터 {e.FilterId}");
                AudioReconnector.DumpRender();
                int n = Array.IndexOf(args, "--name"); // --name AirPods: 기기 이름으로도 찾기
                Log.Write($"지금 쓸 수 있는 스테레오 끝점: {AudioReconnector.ActiveStereoEndpoint(args[t + 1], n >= 0 && n + 1 < args.Length ? args[n + 1] : null) ?? "없음"}");
                return;
            }
            // --test-auto: 실제 자동 전환과 같은 순서(스피커 음소거 → 다시 연결)
            if (args.Contains("--test-auto"))
            {
                var sw = System.Diagnostics.Stopwatch.StartNew();
                string? ep = null;
                using (SpeakerGuard.Engage())
                {
                    if (AudioReconnector.Reconnect(args[t + 1]))
                        for (int i = 0, last = -1; i < 150 && ep == null; i++)
                        {
                            Thread.Sleep(100);
                            ep = AudioReconnector.ActiveStereoEndpoint(args[t + 1]);
                            var now = AudioReconnector.ActiveOfDevice(args[t + 1]);
                            if (now.GetHashCode() != last) { last = now.GetHashCode(); Log.Write($"  {sw.ElapsedMilliseconds}ms 활성: [{now}]"); }
                        }
                    if (ep != null) AudioReconnector.MakeDefault(ep);
                }
                Log.Write($"--test-auto {args[t + 1]} → {(ep != null ? $"소리 준비·기본 출력 {sw.ElapsedMilliseconds}ms" : "실패")}");
                return;
            }
            bool ok = AudioReconnector.Reconnect(args[t + 1]);
            Log.Write($"--test-reconnect {args[t + 1]} → {(ok ? "명령 전달됨" : "실패")}");
            return;
        }

        using var single = new Mutex(true, @"Local\Orchard.Windows", out bool first);
        if (!first && !args.Contains("--demo") && !args.Contains("--pair-demo") && !args.Contains("--link-selftest")) return; // 확인용 화면은 설치된 Orchard 가 켜져 있어도 뜬다

        var app = new App();
        app.InitializeComponent();
        app.Run();
    }
}
