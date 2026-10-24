using System.Windows;
using System.Windows.Controls;
using H.NotifyIcon;
using Orchard.Core;

namespace Orchard.Windows;

public partial class App : Application
{
    readonly Settings _settings = Settings.Load();
    readonly Updater _updater = new();
    readonly MediaControl _media = new();
    AirPodsMonitor? _monitor;
    DeviceSwitcher? _switcher;
    TaskbarIcon? _tray;
    PopupWindow? _popup;
    AirPodsStatus? _prev;
    bool _wasConnected;

    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        DispatcherUnhandledException += (_, ex) => { Log.Write($"처리 못 한 오류: {ex.Exception}"); ex.Handled = true; };
        AppDomain.CurrentDomain.UnhandledException += (_, ex) => Log.Write($"치명 오류: {ex.ExceptionObject}");

        _popup = new PopupWindow(_updater);
        CreateTray();

        // 개발·확인용: --demo 202D 처럼 모델을 주면 가짜 상태로 팝업을 띄운다.
        int demo = Array.IndexOf(e.Args, "--demo");
        if (demo >= 0)
        {
            int id = demo + 1 < e.Args.Length ? Convert.ToInt32(e.Args[demo + 1], 16) : 0x2027;
            var bytes = new byte[] { 0x07, 0x19, 0x01, (byte)(id & 0xFF), (byte)(id >> 8), 0x2A, 0x86, 0x57, 0x00, 0x00, 0x05 };
            var s = ProximityParser.Parse(bytes)!;
            _popup.Render(s, new PairedDevice("", id, ""), connected: true);
            _popup.ShowAnimated();
            int snap = Array.IndexOf(e.Args, "--snap");
            if (snap >= 0 && snap + 1 < e.Args.Length)
            {
                await Task.Delay(1200); // 등장·링 애니메이션이 끝난 뒤
                _popup.SaveSnapshot(e.Args[snap + 1]);
                Shutdown();
            }
            return;
        }

        _monitor = new AirPodsMonitor();
        _monitor.Changed += (s, owner) => Dispatcher.InvokeAsync(() => OnChanged(s, owner));
        await _monitor.StartAsync();
        _switcher = new DeviceSwitcher(_monitor, _settings);
        _switcher.Notify += msg => Dispatcher.Invoke(() => _tray?.ShowNotification("Orchard", msg));
        _popup.TakeOverRequested += () => _ = TakeOverHere();
        try { await _switcher.StartAsync(); } catch (Exception ex) { Log.Write($"미디어 감시 시작 실패: {ex.Message}"); }

        // 개발·확인용(읽기만): 페어링된 에어팟마다 다시 연결에 쓸 오디오 장치를 찾는지 기록한다.
        if (e.Args.Contains("--list-audio"))
        {
            foreach (var p in _monitor.Paired)
                foreach (var ep in AudioReconnector.Find(p.Address))
                    Log.Write($"오디오 장치: {p.Name}(0x{p.ProductId:X4}) · {(ep.Active ? "연결됨" : "끊김")} · {ep.FilterId}");
            Log.Write("오디오 장치 목록 끝");
            Quit();
            return;
        }
        OnChanged(null, null);
        if (!e.Args.Contains("--background")) ShowPopup();

        if (_settings.AutoUpdate) StartUpdateLoop();
    }

    void CreateTray()
    {
        var menu = new ContextMenu();
        MenuItem Toggle(string text, Func<bool> get, Action<bool> set)
        {
            var mi = new MenuItem { Header = text, IsCheckable = true, IsChecked = get() };
            mi.Click += (_, _) => { set(mi.IsChecked); _settings.Save(); };
            return mi;
        }
        menu.Items.Add(new MenuItem { Header = "열기", FontWeight = FontWeights.SemiBold });
        ((MenuItem)menu.Items[0]).Click += (_, _) => ShowPopup();
        menu.Items.Add(new Separator());
        menu.Items.Add(Toggle("귀에서 빼면 일시정지", () => _settings.PauseOnRemove, v => _settings.PauseOnRemove = v));
        menu.Items.Add(Toggle("다시 끼면 이어서 재생", () => _settings.ResumeOnInsert, v => _settings.ResumeOnInsert = v));
        menu.Items.Add(Toggle("케이스를 열면 팝업", () => _settings.PopupOnLidOpen, v => _settings.PopupOnLidOpen = v));
        menu.Items.Add(new Separator());
        menu.Items.Add(Toggle("재생하면 자동으로 가져오기", () => _settings.AutoSwitch, v => _settings.AutoSwitch = v));
        var take = new MenuItem { Header = "이 PC로 가져오기" };
        take.Click += (_, _) => _ = TakeOverHere();
        menu.Items.Add(take);
        menu.Items.Add(new Separator());
        menu.Items.Add(Toggle("윈도우 시작 때 실행", () => AutoStart.IsOn, AutoStart.Set));
        menu.Items.Add(Toggle("자동 업데이트", () => _settings.AutoUpdate, v => _settings.AutoUpdate = v));
        var check = new MenuItem { Header = "지금 업데이트 확인" };
        check.Click += async (_, _) =>
        {
            var v = await _updater.CheckAndDownloadAsync();
            _tray?.ShowNotification("Orchard", v == null ? "최신 버전이에요" : $"v{v}을 받아 두었어요. 팝업에서 바로 적용할 수 있어요.");
        };
        menu.Items.Add(check);
        menu.Items.Add(new Separator());
        var quit = new MenuItem { Header = "끝내기" };
        quit.Click += (_, _) => Quit();
        menu.Items.Add(quit);

        _tray = new TaskbarIcon
        {
            IconSource = new System.Windows.Media.Imaging.BitmapImage(new Uri("pack://application:,,,/Assets/orchard.ico")),
            ToolTipText = "Orchard",
            ContextMenu = menu,
            NoLeftClickDelay = true,
        };
        _tray.TrayLeftMouseUp += (_, _) => ShowPopup();
        _tray.ForceCreate(enablesEfficiencyMode: false);
    }

    async Task TakeOverHere()
    {
        var target = _switcher?.Candidate(out _) ?? _monitor?.Paired.FirstOrDefault(p => p.ProductId == _monitor.Current?.ModelId);
        if (target == null)
        {
            _tray?.ShowNotification("Orchard", "근처에 가져올 에어팟이 없어요. 케이스를 열거나 귀에 껴 주세요.");
            return;
        }
        if (_switcher != null) await _switcher.TakeOverAsync(target);
    }

    void ShowPopup()
    {
        if (_popup == null) return;
        _popup.Render(_monitor?.Current, _monitor?.ConnectedDevice ?? _monitor?.Paired.FirstOrDefault(), _monitor?.ConnectedDevice != null);
        _popup.ShowAnimated();
    }

    void OnChanged(AirPodsStatus? s, PairedDevice? owner)
    {
        bool connected = _monitor?.ConnectedDevice != null;
        owner ??= _monitor?.ConnectedDevice;
        _popup?.Render(s, owner, connected);

        if (_tray != null)
        {
            _tray.ToolTipText = s == null
                ? (connected ? $"{owner?.Name} · 연결됨" : "Orchard · 에어팟 없음")
                : s.Model.IsHeadphones
                    ? $"{owner?.Name ?? s.Model.Name}\n배터리 {s.Headphones?.ToString() ?? "–"}%"
                    : $"{owner?.Name ?? s.Model.Name}\n왼쪽 {s.Left?.ToString() ?? "–"}% · 오른쪽 {s.Right?.ToString() ?? "–"}% · 케이스 {s.Case?.ToString() ?? "–"}%";
        }

        if (s != null && _prev != null && s.ModelId == _prev.ModelId && connected)
        {
            if (_settings.PauseOnRemove && _prev.AnyInEar && !s.AnyInEar) _ = _media.PauseAsync();
            else if (_settings.ResumeOnInsert && !_prev.AnyInEar && s.AnyInEar) _ = _media.ResumeAsync();
        }

        // 케이스를 연 순간(또는 처음 보인 순간 뚜껑이 열려 있으면) 팝업
        bool lidJustOpened = s is { LidOpen: true } && !s.Model.IsHeadphones && (_prev == null || !_prev.LidOpen || _prev.ModelId != s.ModelId);
        if (_settings.PopupOnLidOpen && lidJustOpened && !(s!.AnyInEar)) _popup?.ShowAnimated();
        if (connected && !_wasConnected) Log.Write($"연결됨: {owner?.Name}");

        _wasConnected = connected;
        _prev = s;
    }

    void StartUpdateLoop()
    {
        _ = Task.Run(async () =>
        {
            await Task.Delay(TimeSpan.FromSeconds(20));
            while (true)
            {
                var v = await _updater.CheckAndDownloadAsync();
                if (v != null)
                    Dispatcher.Invoke(() => _tray?.ShowNotification("Orchard 업데이트", $"v{v}을 받아 두었어요. 다음에 켤 때 적용돼요."));
                await Task.Delay(TimeSpan.FromHours(6));
            }
        });
    }

    void Quit()
    {
        _updater.ApplyOnExit();
        _monitor?.Dispose();
        _tray?.Dispose();
        Shutdown();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _monitor?.Dispose();
        base.OnExit(e);
    }
}
