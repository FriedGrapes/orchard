using System.Windows;
using System.Windows.Media;

namespace Orchard.Windows;

/// <summary>
/// 애플식 '연속 곡률' 둥근 사각형. 모서리를 초타원(n=4) 조각으로 그려 직선에서 곡선으로 매끄럽게 넘어간다.
/// 안드로이드(ContinuousShape.kt)와 같은 식 — 두 OS 팝업 모서리가 똑같다.
/// </summary>
public static class ContinuousCorners
{
    public static Geometry Rect(double w, double h, double r, int steps = 24)
    {
        double e = Math.Min(r * 1.6, Math.Min(w, h) / 2);
        const double k = 2.0 / 4.0;
        double C(double t) => Math.Pow(Math.Max(0, Math.Cos(t)), k);
        double S(double t) => Math.Pow(Math.Max(0, Math.Sin(t)), k);
        double half = Math.PI / 2;
        var pts = new List<Point> { new(w - e, 0) };
        for (int i = 1; i <= steps; i++) { double t = half * (1 - (double)i / steps); pts.Add(new(w - e + e * C(t), e - e * S(t))); }
        pts.Add(new(w, h - e));
        for (int i = 1; i <= steps; i++) { double t = half * ((double)i / steps); pts.Add(new(w - e + e * C(t), h - e + e * S(t))); }
        pts.Add(new(e, h));
        for (int i = 1; i <= steps; i++) { double t = half * (1 - (double)i / steps); pts.Add(new(e - e * C(t), h - e + e * S(t))); }
        pts.Add(new(0, e));
        for (int i = 1; i <= steps; i++) { double t = half * ((double)i / steps); pts.Add(new(e - e * C(t), e - e * S(t))); }

        var g = new StreamGeometry();
        using (var ctx = g.Open())
        {
            ctx.BeginFigure(new Point(e, 0), true, true);
            ctx.PolyLineTo(pts, true, true);
        }
        g.Freeze();
        return g;
    }
}
