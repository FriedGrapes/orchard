using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace Orchard.Windows;

/// <summary>아이폰 배터리 위젯처럼 링이 차오르는 배터리 표시. 값이 바뀌면 부드럽게 움직인다.</summary>
public sealed class BatteryRing : FrameworkElement
{
    public static readonly DependencyProperty ValueProperty = DependencyProperty.Register(
        nameof(Value), typeof(double), typeof(BatteryRing),
        new FrameworkPropertyMetadata(0.0, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty ChargingProperty = DependencyProperty.Register(
        nameof(Charging), typeof(bool), typeof(BatteryRing),
        new FrameworkPropertyMetadata(false, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty KnownProperty = DependencyProperty.Register(
        nameof(Known), typeof(bool), typeof(BatteryRing),
        new FrameworkPropertyMetadata(false, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty TrackBrushProperty = DependencyProperty.Register(
        nameof(TrackBrush), typeof(Brush), typeof(BatteryRing),
        new FrameworkPropertyMetadata(new SolidColorBrush(Color.FromArgb(40, 128, 128, 128)), FrameworkPropertyMetadataOptions.AffectsRender));

    /// <summary>0~100. 직접 넣지 말고 <see cref="AnimateTo"/>를 쓴다.</summary>
    public double Value { get => (double)GetValue(ValueProperty); set => SetValue(ValueProperty, value); }
    public bool Charging { get => (bool)GetValue(ChargingProperty); set => SetValue(ChargingProperty, value); }
    public bool Known { get => (bool)GetValue(KnownProperty); set => SetValue(KnownProperty, value); }
    public Brush TrackBrush { get => (Brush)GetValue(TrackBrushProperty); set => SetValue(TrackBrushProperty, value); }

    public double Thickness { get; set; } = 5;

    // 충전 빛: 0→1 을 2.4초마다 되풀이(안드로이드 ChargeGlow.kt 와 같은 수치)
    public static readonly DependencyProperty PhaseProperty = DependencyProperty.Register(
        nameof(Phase), typeof(double), typeof(BatteryRing),
        new FrameworkPropertyMetadata(0.0, FrameworkPropertyMetadataOptions.AffectsRender));
    public double Phase { get => (double)GetValue(PhaseProperty); set => SetValue(PhaseProperty, value); }
    bool _glowing;

    void UpdateGlow()
    {
        // 윈도우 '애니메이션 효과'를 끈 사람에게는 빛을 흘리지 않는다
        bool want = Charging && SystemParameters.ClientAreaAnimation;
        if (want == _glowing) return;
        _glowing = want;
        if (want) BeginAnimation(PhaseProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(2400)) { RepeatBehavior = RepeatBehavior.Forever });
        else { BeginAnimation(PhaseProperty, null); Phase = 0; }
    }

    public void AnimateTo(int? level, bool charging)
    {
        Known = level.HasValue;
        Charging = charging;
        UpdateGlow();
        var anim = new DoubleAnimation(level ?? 0, TimeSpan.FromMilliseconds(level.HasValue ? 650 : 200))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
        };
        BeginAnimation(ValueProperty, anim);
    }

    static readonly Brush Green = Frozen(Color.FromRgb(0x34, 0xC7, 0x59));
    static readonly Brush Yellow = Frozen(Color.FromRgb(0xFF, 0xCC, 0x00));
    static readonly Brush Red = Frozen(Color.FromRgb(0xFF, 0x3B, 0x30));

    static Brush Frozen(Color c) { var b = new SolidColorBrush(c); b.Freeze(); return b; }

    protected override void OnRender(DrawingContext dc)
    {
        double size = Math.Min(ActualWidth, ActualHeight);
        var c = new Point(ActualWidth / 2, ActualHeight / 2);
        double r = size / 2 - Thickness / 2;

        dc.DrawEllipse(null, new Pen(TrackBrush, Thickness), c, r, r);
        if (!Known) return;

        double v = Math.Clamp(Value, 0, 100);
        var brush = Charging || v > 20 ? Green : v > 10 ? Yellow : Red;
        var pen = new Pen(brush, Thickness) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };

        if (v <= 0.1) return;
        double total = v >= 99.9 ? 360 : v / 100 * 360;
        bool glow = _glowing && v > 2;
        var color = ((SolidColorBrush)brush).Color;

        // 둘레가 숨 쉬듯 은은하게(0.08~0.20)
        if (glow)
        {
            double halo = 0.08 + 0.12 * (0.5 - 0.5 * Math.Cos(2 * Math.PI * Phase));
            var hp = new Pen(new SolidColorBrush(Color.FromArgb((byte)(halo * 255), color.R, color.G, color.B)), Thickness * 2.4)
                { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };
            DrawArc(dc, hp, c, r, 0, total);
        }

        if (v >= 99.9) dc.DrawEllipse(null, pen, c, r, r);
        else DrawArc(dc, pen, c, r, 0, total);

        // 채워진 부분을 따라 짧은 빛(최대 50°)이 흐른다. 가운데가 가장 밝고 처음·끝은 흐리게
        if (glow)
        {
            const int pieces = 8;
            double len = Math.Min(50, total), head = (total + len) * Phase - len, piece = len / pieces;
            double envelope = Math.Sin(Math.PI * Phase);
            for (int i = 0; i < pieces; i++)
            {
                double a0 = Math.Max(head + i * piece, 0), a1 = Math.Min(head + (i + 1) * piece, total);
                if (a1 <= a0) continue;
                double mid = (i + 0.5) / pieces, shape = 1 - Math.Abs(mid * 2 - 1);
                var lp = new Pen(new SolidColorBrush(Color.FromArgb((byte)(0.55 * shape * envelope * 255), 255, 255, 255)), Thickness * 0.7);
                DrawArc(dc, lp, c, r, a0, a1 - a0);
            }
        }
    }

    /// <summary>12시에서 시계 방향으로 [from]°부터 [sweep]°만큼.</summary>
    static void DrawArc(DrawingContext dc, Pen pen, Point c, double r, double from, double sweep)
    {
        if (sweep >= 359.9) { dc.DrawEllipse(null, pen, c, r, r); return; }
        Point At(double deg) { double a = deg * Math.PI / 180; return new Point(c.X + r * Math.Sin(a), c.Y - r * Math.Cos(a)); }
        var g = new StreamGeometry();
        using (var ctx = g.Open())
        {
            ctx.BeginFigure(At(from), false, false);
            ctx.ArcTo(At(from + sweep), new Size(r, r), 0, sweep > 180, SweepDirection.Clockwise, true, false);
        }
        g.Freeze();
        dc.DrawGeometry(null, pen, g);
    }
}
