using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;
using Orchard.Core;

namespace Orchard.Windows;

/// <summary>
/// 배터리 카드. 트레이에서 열면 아래에 버전·업데이트 줄이 붙고, 뚜껑을 열어 뜬 카드는 포커스를 뺏지 않고
/// 뚜껑을 닫거나 10초가 지나면 저절로 들어간다(안드로이드 LidCard 와 같은 동작).
/// </summary>
public partial class PopupWindow : Window
{
    readonly Updater _updater;
    readonly DispatcherTimer _autoClose = new() { Interval = TimeSpan.FromSeconds(10) };
    bool _hiding;
    int? _shownModel;

    /// <summary>뚜껑을 열어서 뜬 카드인지(뚜껑을 닫으면 이것만 닫는다).</summary>
    public bool ShownByLid { get; private set; }

    /// <summary>'이 PC로 가져오기'를 눌렀을 때.</summary>
    public event Action? TakeOverRequested;

    // 0 = 숨음, 1 = 제자리. 이 값 하나로 위치·크기·투명도를 함께 움직인다(확인용 프레임도 이 값으로 찍는다).
    public static readonly DependencyProperty ProgressProperty = DependencyProperty.Register(
        nameof(Progress), typeof(double), typeof(PopupWindow),
        new PropertyMetadata(1.0, (d, _) => ((PopupWindow)d).ApplyProgress()));
    public double Progress { get => (double)GetValue(ProgressProperty); set => SetValue(ProgressProperty, value); }

    void TakeBtn_Click(object sender, RoutedEventArgs e) => TakeOverRequested?.Invoke();
    void CloseBtn_Click(object sender, RoutedEventArgs e) => HideAnimated();
    void UpdateBtn_Click(object sender, RoutedEventArgs e) => _updater.ApplyAndRestart();

    public PopupWindow(Updater updater)
    {
        _updater = updater;
        InitializeComponent();
        VersionText.Text = $"Orchard v{updater.CurrentVersion}";
        var track = (Brush)Resources["Track"];
        LeftRing.TrackBrush = RightRing.TrackBrush = CaseRing.TrackBrush = track;
        updater.StateChanged += () => Dispatcher.Invoke(RefreshUpdate);
        Root.SizeChanged += (_, _) => Card.Data = ContinuousCorners.Rect(Root.ActualWidth, Root.ActualHeight, 30);
        Deactivated += (_, _) => { if (!ShownByLid) HideAnimated(); };
        _autoClose.Tick += (_, _) => { _autoClose.Stop(); if (ShownByLid && !IsMouseOver) HideAnimated(); else if (ShownByLid) _autoClose.Start(); };
    }

    void RefreshUpdate()
    {
        var v = _updater.PendingVersion;
        UpdateBtn.Visibility = v == null ? Visibility.Collapsed : Visibility.Visible;
        if (v != null) UpdateText.Text = $"v{v}로 업데이트";
    }

    public void Render(AirPodsStatus? s, PairedDevice? owner, bool connected)
    {
        // 근처에 보이는데 이 PC와 연결 안 됐으면 가져오기 버튼
        TakeBtn.Visibility = s != null && !connected ? Visibility.Visible : Visibility.Collapsed;
        if (s == null)
        {
            NameText.Text = owner?.Name ?? "Orchard";
            HintText.Text = connected ? "연결됨 · 배터리 정보를 기다리는 중" : "케이스를 열거나 귀에 꽂으면 바로 나타나요";
            HintText.Visibility = Visibility.Visible;
            Rings.Visibility = Visibility.Collapsed;
            if (owner != null) SetArt(owner.Model); else ArtRow.Visibility = Visibility.Collapsed;
            return;
        }

        var m = s.Model;
        NameText.Text = owner?.Name is { Length: > 0 } n ? n : m.Name;
        HintText.Visibility = Visibility.Collapsed;
        Rings.Visibility = Visibility.Visible;
        SetArt(m);

        if (m.IsHeadphones)
        {
            RightCell.Visibility = CaseCell.Visibility = Visibility.Collapsed;
            Rings.Columns = 1;
            bool ch = s.LeftCharging || s.RightCharging;
            LeftLabel.Text = ch ? "충전 중" : "배터리";
            Ring(LeftRing, LeftPct, s.Headphones, ch);
        }
        else
        {
            RightCell.Visibility = CaseCell.Visibility = Visibility.Visible;
            Rings.Columns = 3;
            LeftLabel.Text = "왼쪽";
            RightLabel.Text = "오른쪽";
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

    void ApplyProgress()
    {
        double p = Progress;
        Root.Opacity = Math.Clamp(p * 1.6, 0, 1);
        double s = 0.9 + 0.1 * p;
        Zoom.ScaleX = Zoom.ScaleY = s;
        Slide.Y = (1 - p) * 40;
    }

    /// <param name="fromLid">뚜껑을 열어서 띄우는지. 그러면 포커스를 뺏지 않고, 아래 줄을 숨긴다.</param>
    public void ShowAnimated(bool fromLid = false)
    {
        _hiding = false;
        ShownByLid = fromLid;
        Footer.Visibility = fromLid ? Visibility.Collapsed : Visibility.Visible;
        ShowActivated = !fromLid;
        var area = SystemParameters.WorkArea;
        bool wasVisible = IsVisible;
        if (!wasVisible) { Progress = 0; Show(); }
        UpdateLayout();
        Left = area.Right - ActualWidth - 4;
        Top = area.Bottom - ActualHeight - 4;
        if (!fromLid) Activate();
        if (fromLid) { _autoClose.Stop(); _autoClose.Start(); }

        // 애플 알림처럼 살짝 튀며 떠올라 자리 잡는다(안드로이드 스프링 0.8 과 비슷한 느낌)
        BeginAnimation(ProgressProperty, new DoubleAnimation(1, TimeSpan.FromMilliseconds(wasVisible ? 200 : 460))
        {
            EasingFunction = new BackEase { EasingMode = EasingMode.EaseOut, Amplitude = 0.35 }
        });
    }

    public void HideAnimated()
    {
        if (!IsVisible || _hiding) return;
        _hiding = true;
        _autoClose.Stop();
        var a = new DoubleAnimation(0, TimeSpan.FromMilliseconds(240))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseIn }
        };
        a.Completed += (_, _) => { if (_hiding) { Hide(); _hiding = false; ShownByLid = false; } };
        BeginAnimation(ProgressProperty, a);
    }

    /// <summary>확인용: 지금 모습을 PNG로 저장한다(진행값을 주면 그 순간의 움직임 프레임).</summary>
    public void SaveSnapshot(string path, double? progress = null)
    {
        if (progress is double p) { BeginAnimation(ProgressProperty, null); Progress = p; }
        UpdateLayout();
        double scale = 2;
        var w = ActualWidth; var h = ActualHeight;
        var rtb = new System.Windows.Media.Imaging.RenderTargetBitmap((int)(w * scale), (int)(h * scale), 96 * scale, 96 * scale, PixelFormats.Pbgra32);
        var dv = new DrawingVisual();
        using (var dc = dv.RenderOpen())
        {
            dc.DrawRectangle(new SolidColorBrush(Color.FromRgb(0xDC, 0xE3, 0xEA)), null, new Rect(0, 0, w, h));
        }
        rtb.Render(dv);
        rtb.Render(Root); // 그림자 때문에 어긋나지 않게 창 안 자리 그대로 그린다
        var enc = new System.Windows.Media.Imaging.PngBitmapEncoder();
        enc.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(rtb));
        using var f = System.IO.File.Create(path);
        enc.Save(f);
    }
}
