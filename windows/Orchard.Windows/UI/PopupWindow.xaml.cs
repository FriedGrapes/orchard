using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;
using Orchard.Core;

namespace Orchard.Windows;

public partial class PopupWindow : Window
{
    readonly Updater _updater;
    bool _hiding;
    int? _shownModel;

    /// <summary>'이 PC로 가져오기'를 눌렀을 때.</summary>
    public event Action? TakeOverRequested;

    void TakeBtn_Click(object sender, RoutedEventArgs e) => TakeOverRequested?.Invoke();

    public PopupWindow(Updater updater)
    {
        _updater = updater;
        InitializeComponent();
        ApplyTheme();
        VersionText.Text = $"Orchard v{updater.CurrentVersion}";
        updater.StateChanged += () => Dispatcher.Invoke(RefreshUpdate);
        SourceInitialized += (_, _) =>
        {
            if (Backdrop.Apply(this, Backdrop.IsDark)) Background = Brushes.Transparent;
        };
        Deactivated += (_, _) => HideAnimated();
        Microsoft.Win32.SystemEvents.UserPreferenceChanged += (_, _) => Dispatcher.Invoke(ApplyTheme);
    }

    void ApplyTheme()
    {
        bool dark = Backdrop.IsDark;
        Color C(byte a, byte r, byte g, byte b) => Color.FromArgb(a, r, g, b);
        void Set(string k, Color c) => Resources[k] = new SolidColorBrush(c);
        Set("WindowBg", dark ? C(255, 32, 32, 34) : C(255, 246, 246, 248));
        Set("Fg", dark ? C(255, 245, 245, 247) : C(255, 28, 28, 30));
        Set("Fg2", dark ? C(170, 235, 235, 245) : C(160, 60, 60, 67));
        Set("Fg3", dark ? C(110, 235, 235, 245) : C(110, 60, 60, 67));
        Set("Line", dark ? C(28, 255, 255, 255) : C(20, 0, 0, 0));
        Set("Chip", dark ? C(30, 255, 255, 255) : C(16, 0, 0, 0));
        Set("Accent", dark ? C(255, 64, 210, 140) : C(255, 22, 140, 85));
        var track = new SolidColorBrush(dark ? C(36, 255, 255, 255) : C(24, 0, 0, 0));
        LeftRing.TrackBrush = RightRing.TrackBrush = CaseRing.TrackBrush = track;
    }

    void RefreshUpdate()
    {
        var v = _updater.PendingVersion;
        UpdateBtn.Visibility = v == null ? Visibility.Collapsed : Visibility.Visible;
        if (v != null) UpdateText.Text = $"v{v}로 업데이트";
    }

    void UpdateBtn_Click(object sender, RoutedEventArgs e) => _updater.ApplyAndRestart();

    public void Render(AirPodsStatus? s, PairedDevice? owner, bool connected)
    {
        // 근처에 보이는데 이 PC와 연결 안 됐으면 가져오기 버튼
        TakeBtn.Visibility = s != null && !connected ? Visibility.Visible : Visibility.Collapsed;
        if (s == null)
        {
            NameText.Text = owner?.Name ?? "Orchard";
            SubText.Text = connected ? "연결됨 · 배터리 정보를 기다리는 중" : "에어팟을 찾는 중";
            StateDot.Fill = connected ? (Brush)Resources["Accent"] : (Brush)Resources["Fg3"];
            HintText.Visibility = Visibility.Visible;
            Rings.Visibility = Visibility.Collapsed;
            if (owner != null) SetArt(owner.Model); else ArtRow.Visibility = Visibility.Collapsed;
            return;
        }

        var m = s.Model;
        NameText.Text = owner?.Name is { Length: > 0 } n ? n : m.Name;
        SubText.Text = (owner?.Name is { Length: > 0 } ? m.Name + " · " : "") + (connected ? "연결됨" : "근처에 있음");
        StateDot.Fill = connected ? (Brush)Resources["Accent"] : (Brush)Resources["Fg3"];
        HintText.Visibility = Visibility.Collapsed;
        Rings.Visibility = Visibility.Visible;
        SetArt(m);

        if (m.IsHeadphones)
        {
            RightCell.Visibility = CaseCell.Visibility = Visibility.Collapsed;
            Rings.Columns = 1;
            LeftLabel.Text = s.AnyInEar ? "착용 중" : "배터리";
            Ring(LeftRing, LeftPct, s.Headphones, s.LeftCharging || s.RightCharging);
        }
        else
        {
            RightCell.Visibility = CaseCell.Visibility = Visibility.Visible;
            Rings.Columns = 3;
            LeftLabel.Text = s.LeftInEar ? "왼쪽 · 착용" : "왼쪽";
            RightLabel.Text = s.RightInEar ? "오른쪽 · 착용" : "오른쪽";
            Ring(LeftRing, LeftPct, s.Left, s.LeftCharging);
            Ring(RightRing, RightPct, s.Right, s.RightCharging);
            Ring(CaseRing, CasePct, s.Case, s.CaseCharging);
        }
    }

    void SetArt(AirPodsModel m)
    {
        ArtRow.Visibility = Visibility.Visible;
        if (_shownModel == m.Id) return;
        _shownModel = m.Id;
        BudsImg.Source = DeviceArt.Buds(m);
        var c = DeviceArt.Case(m);
        CaseImg.Source = c;
        CaseCol.Width = c == null ? new GridLength(0) : new GridLength(1, GridUnitType.Star);
        // 그림이 바뀔 때 살짝 떠오르며 나타난다
        BudsImg.Opacity = CaseImg.Opacity = 0;
        var fade = new DoubleAnimation(1, TimeSpan.FromMilliseconds(320)) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } };
        BudsImg.BeginAnimation(OpacityProperty, fade);
        CaseImg.BeginAnimation(OpacityProperty, fade);
    }

    static void Ring(BatteryRing ring, System.Windows.Controls.TextBlock pct, int? level, bool charging)
    {
        ring.AnimateTo(level, charging);
        pct.Text = level is int l ? (charging ? $"⚡{l}" : $"{l}") : "–";
    }

    public void ShowAnimated()
    {
        _hiding = false;
        var area = SystemParameters.WorkArea;
        if (!IsVisible) { Opacity = 0; Show(); }
        UpdateLayout();
        Left = area.Right - ActualWidth - 12;
        Top = area.Bottom - ActualHeight - 12;
        Activate();

        var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
        BeginAnimation(OpacityProperty, new DoubleAnimation(1, TimeSpan.FromMilliseconds(220)) { EasingFunction = ease });
        Slide.BeginAnimation(TranslateTransform.YProperty,
            new DoubleAnimation(14, 0, TimeSpan.FromMilliseconds(320)) { EasingFunction = new BackEase { EasingMode = EasingMode.EaseOut, Amplitude = 0.25 } });
    }

    /// <summary>확인용: 창 내용을 PNG로 저장한다(아크릴 배경 대신 단색).</summary>
    public void SaveSnapshot(string path)
    {
        var content = (FrameworkElement)Content;
        double scale = 2;
        var rtb = new System.Windows.Media.Imaging.RenderTargetBitmap(
            (int)(ActualWidth * scale), (int)(ActualHeight * scale), 96 * scale, 96 * scale, PixelFormats.Pbgra32);
        var dv = new DrawingVisual();
        using (var dc = dv.RenderOpen())
        {
            dc.DrawRoundedRectangle((Brush)Resources["WindowBg"], null, new Rect(0, 0, ActualWidth, ActualHeight), 8, 8);
            dc.DrawRectangle(new VisualBrush(content), null, new Rect(0, 0, ActualWidth, ActualHeight));
        }
        rtb.Render(dv);
        var enc = new System.Windows.Media.Imaging.PngBitmapEncoder();
        enc.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(rtb));
        using var f = System.IO.File.Create(path);
        enc.Save(f);
    }

    public void HideAnimated()
    {
        if (!IsVisible || _hiding) return;
        _hiding = true;
        var a = new DoubleAnimation(0, TimeSpan.FromMilliseconds(140)) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseIn } };
        a.Completed += (_, _) => { if (_hiding) { Hide(); _hiding = false; } };
        BeginAnimation(OpacityProperty, a);
        Slide.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(0, 8, TimeSpan.FromMilliseconds(140)));
    }
}
