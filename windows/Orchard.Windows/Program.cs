using Velopack;

namespace Orchard.Windows;

public static class Program
{
    [STAThread]
    public static void Main(string[] args)
    {
        // Velopack: 설치·업데이트·제거 때 잠깐 실행되는 단계를 먼저 처리한다.
        VelopackApp.Build()
            .OnFirstRun(_ => AutoStart.Set(true))
            .Run();

        using var single = new Mutex(true, @"Local\Orchard.Windows", out bool first);
        if (!first) return;

        var app = new App();
        app.InitializeComponent();
        app.Run();
    }
}
