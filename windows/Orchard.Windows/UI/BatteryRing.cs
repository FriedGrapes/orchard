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

    public void AnimateTo(int? level, bool charging)
    {
        Known = level.HasValue;
        Charging = charging;
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

        if (v >= 99.9) { dc.DrawEllipse(null, pen, c, r, r); return; }
        if (v <= 0.1) return;

        double a = v / 100 * 2 * Math.PI;
        var start = new Point(c.X, c.Y - r);
        var end = new Point(c.X + r * Math.Sin(a), c.Y - r * Math.Cos(a));
        var g = new StreamGeometry();
        using (var ctx = g.Open())
        {
            ctx.BeginFigure(start, false, false);
            ctx.ArcTo(end, new Size(r, r), 0, a > Math.PI, SweepDirection.Clockwise, true, false);
        }
        g.Freeze();
        dc.DrawGeometry(null, pen, g);
    }
}
