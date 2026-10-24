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

        using var single = new Mutex(true, @"Local\Orchard.Windows", out bool first);
        if (!first) return;

        var app = new App();
        app.InitializeComponent();
        app.Run();
    }
}
