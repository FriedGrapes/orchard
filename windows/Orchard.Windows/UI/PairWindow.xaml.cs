using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;
using QRCoder;

namespace Orchard.Windows;

/// <summary>
/// '폰과 연결' 창: QR(짝짓기 주소)을 보여 주고, 폰이 찍어 짝을 맺으면 같은 자리에서 초록 체크로 바뀐 뒤 저절로 닫힌다.
/// 아래에 연결된 폰 목록(끊기).
/// </summary>
public partial class PairWindow : Window
{
    readonly LinkService? _link;
    readonly DispatcherTimer _refresh = new() { Interval = TimeSpan.FromSeconds(15) };
    bool _closing;

    public PairWindow(LinkService? link, string? demoUrl = null)
    {
        _link = link;
        InitializeComponent();
        Root.SizeChanged += (_, _) => Card.Data = ContinuousCorners.Rect(Root.ActualWidth, Root.ActualHeight, 30);
        QrTile.SizeChanged += (_, _) => QrBack.Data = ContinuousCorners.Rect(QrTile.ActualWidth, QrTile.ActualHeight, 22);
        // 체크 선 길이 ≈ 78px = 굵기 9의 8.7배 → 대시 9칸이면 처음엔 안 보이고 0으로 당기면 그려진다
        // (시작점을 빈칸 안쪽 0.5칸에 두어, 길이 0인 대시의 둥근 끝이 점으로 보이지 않게)
        Check.StrokeDashArray = new DoubleCollection { 9, 10 };
        Check.StrokeDashOffset = 9.5;

        ShowQr(demoUrl ?? link?.BeginPairing().ToUrl() ?? "orchard://pair");
        if (link != null)
        {
            link.Paired += p => Dispatcher.InvokeAsync(() => ShowDone(p.Name));
            link.PeersChanged += () => Dispatcher.InvokeAsync(RefreshPeers);
        }
        RefreshPeers();
        _refresh.Tick += (_, _) => RefreshPeers();
        _refresh.Start();
        KeyDown += (_, e) => { if (e.Key == Key.Escape) AnimatedClose(); };
        Loaded += (_, _) => { Breathe(); Enter(); };
        Closed += (_, _) => { _refresh.Stop(); _link?.EndPairing(); };
    }

    void ShowQr(string text)
    {
        using var gen = new QRCodeGenerator();
        var data = gen.CreateQrCode(text, QRCodeGenerator.ECCLevel.M);
        var m = data.ModuleMatrix;
        const int quiet = 4; // QRCoder가 붙이는 바깥 여백 — 흰 타일 안쪽 여백으로 대신한다
        int n = m.Count - quiet * 2;
        var geo = new GeometryGroup { FillRule = FillRule.Nonzero };
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                if (m[y + quiet][x + quiet]) geo.Children.Add(new RectangleGeometry(new Rect(x, y, 1.02, 1.02)));
        geo.Freeze();
        var drawing = new DrawingGroup();
        drawing.Children.Add(new GeometryDrawing(Brushes.White, null, new RectangleGeometry(new Rect(0, 0, n, n))));
        drawing.Children.Add(new GeometryDrawing(Brushes.Black, null, geo));
        QrImage.Source = new DrawingImage(drawing);
    }

    /// <summary>폰이 짝을 맺은 순간: QR이 살짝 줄며 사라지고, 초록 원이 스프링처럼 커진 뒤 체크가 그려진다.</summary>
    public void ShowDone(string phone)
    {
        var ease = new CubicEase { EasingMode = EasingMode.EaseIn };
        QrTile.BeginAnimation(OpacityProperty, new DoubleAnimation(0, TimeSpan.FromMilliseconds(160)) { EasingFunction = ease });
        QrZoom.BeginAnimation(ScaleTransform.ScaleXProperty, new DoubleAnimation(0.92, TimeSpan.FromMilliseconds(160)) { EasingFunction = ease });
        QrZoom.BeginAnimation(ScaleTransform.ScaleYProperty, new DoubleAnimation(0.92, TimeSpan.FromMilliseconds(160)) { EasingFunction = ease });

        var spring = new ElasticEase { EasingMode = EasingMode.EaseOut, Oscillations = 1, Springiness = 6 };
        var begin = TimeSpan.FromMilliseconds(120);
        DoneMark.BeginAnimation(OpacityProperty, new DoubleAnimation(1, TimeSpan.FromMilliseconds(180)) { BeginTime = begin });
        DoneZoom.BeginAnimation(ScaleTransform.ScaleXProperty, new DoubleAnimation(1, TimeSpan.FromMilliseconds(520)) { BeginTime = begin, EasingFunction = spring });
        DoneZoom.BeginAnimation(ScaleTransform.ScaleYProperty, new DoubleAnimation(1, TimeSpan.FromMilliseconds(520)) { BeginTime = begin, EasingFunction = spring });
        Check.BeginAnimation(System.Windows.Shapes.Shape.StrokeDashOffsetProperty,
            new DoubleAnimation(0, TimeSpan.FromMilliseconds(320)) { BeginTime = TimeSpan.FromMilliseconds(300), EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } });

        Dot.BeginAnimation(OpacityProperty, null);
        Dot.Opacity = 0;
        Dot.Visibility = Visibility.Collapsed;
        StatusText.Text = $"연결됐어요 · {phone}";
        Steps.Text = "이제 한쪽에서 재생하면 다른 쪽이 바로 에어팟을 넘겨줘요";
        RefreshPeers();
        var t = new DispatcherTimer { Interval = TimeSpan.FromSeconds(2.6) };
        t.Tick += (_, _) => { t.Stop(); if (_link != null) AnimatedClose(); };
        t.Start();
    }

    /// <summary>기다리는 동안 초록 점이 천천히 숨 쉰다(1.6초).</summary>
    void Breathe() => Dot.BeginAnimation(OpacityProperty, new DoubleAnimation(1, 0.25, TimeSpan.FromMilliseconds(800))
    {
        AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever, EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut },
    });

    void Enter()
    {
        Root.Opacity = 0;
        var back = new BackEase { EasingMode = EasingMode.EaseOut, Amplitude = 0.25 };
        Root.BeginAnimation(OpacityProperty, new DoubleAnimation(1, TimeSpan.FromMilliseconds(220)));
        Zoom.BeginAnimation(ScaleTransform.ScaleXProperty, new DoubleAnimation(0.96, 1, TimeSpan.FromMilliseconds(320)) { EasingFunction = back });
        Zoom.BeginAnimation(ScaleTransform.ScaleYProperty, new DoubleAnimation(0.96, 1, TimeSpan.FromMilliseconds(320)) { EasingFunction = back });
    }

    void AnimatedClose()
    {
        if (_closing) return;
        _closing = true;
        var a = new DoubleAnimation(0, TimeSpan.FromMilliseconds(140)) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseIn } };
        a.Completed += (_, _) => Close();
        Root.BeginAnimation(OpacityProperty, a);
        Zoom.BeginAnimation(ScaleTransform.ScaleXProperty, new DoubleAnimation(0.97, TimeSpan.FromMilliseconds(140)));
        Zoom.BeginAnimation(ScaleTransform.ScaleYProperty, new DoubleAnimation(0.97, TimeSpan.FromMilliseconds(140)));
    }

    sealed record Row(uint Id, string Name, string Seen);

    void RefreshPeers() { if (_link != null) SetPeers(_link.Peers); }

    public void SetPeers(IEnumerable<LinkPeer> peers)
    {
        var rows = peers.Select(p => new Row(p.Id, p.Name, Seen(p))).ToList();
        PeerList.ItemsSource = rows;
        PeersBox.Visibility = rows.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    static string Seen(LinkPeer p)
    {
        if (p.LastSeen is not DateTimeOffset t) return "아직 신호 없음";
        var ago = DateTimeOffset.Now - t;
        string when = ago < TimeSpan.FromMinutes(1) ? "방금" : ago < TimeSpan.FromHours(1) ? $"{(int)ago.TotalMinutes}분 전" : ago < TimeSpan.FromDays(1) ? $"{(int)ago.TotalHours}시간 전" : $"{(int)ago.TotalDays}일 전";
        return $"{when} · {(p.Via == "ble" ? "블루투스" : "와이파이")}";
    }

    void Remove_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: uint id }) return;
        var name = _link?.Peers.FirstOrDefault(p => p.Id == id)?.Name ?? "이 폰";
        if (MessageBox.Show(this, $"{name}과(와) 연결을 끊을까요?\n다시 쓰려면 QR을 한 번 더 찍으면 돼요.", "Orchard", MessageBoxButton.OKCancel) == MessageBoxResult.OK)
            _link?.Remove(id);
    }

    void Close_Click(object sender, RoutedEventArgs e) => AnimatedClose();
    void Header_MouseDown(object sender, MouseButtonEventArgs e) { if (e.ButtonState == MouseButtonState.Pressed) DragMove(); }

    /// <summary>확인용: 지금 모습을 PNG로 저장한다.</summary>
    public void SaveSnapshot(string path)
    {
        UpdateLayout();
        double scale = 2;
        var w = ActualWidth; var h = ActualHeight;
        var rtb = new System.Windows.Media.Imaging.RenderTargetBitmap((int)(w * scale), (int)(h * scale), 96 * scale, 96 * scale, PixelFormats.Pbgra32);
        var dv = new DrawingVisual();
        using (var dc = dv.RenderOpen()) dc.DrawRectangle(new SolidColorBrush(Color.FromRgb(0xDC, 0xE3, 0xEA)), null, new Rect(0, 0, w, h));
        rtb.Render(dv);
        rtb.Render(Root);
        var enc = new System.Windows.Media.Imaging.PngBitmapEncoder();
        enc.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(rtb));
        using var f = System.IO.File.Create(path);
        enc.Save(f);
    }
}
