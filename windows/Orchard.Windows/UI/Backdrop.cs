using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using Microsoft.Win32;

namespace Orchard.Windows;

/// <summary>윈도우 11의 반투명(아크릴) 배경·둥근 모서리·다크 모드 제목줄을 창에 입힌다.</summary>
public static class Backdrop
{
    [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);
    [DllImport("dwmapi.dll")] static extern int DwmExtendFrameIntoClientArea(IntPtr hwnd, ref Margins m);
    [StructLayout(LayoutKind.Sequential)] struct Margins { public int L, R, T, B; }

    const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
    const int DWMWA_SYSTEMBACKDROP_TYPE = 38;

    public static bool IsDark
    {
        get
        {
            using var k = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            return k?.GetValue("AppsUseLightTheme") is int v && v == 0;
        }
    }

    /// <returns>아크릴이 켜졌으면 true(창 배경을 투명하게 둬야 함).</returns>
    public static bool Apply(Window w, bool dark)
    {
        var hwnd = new WindowInteropHelper(w).Handle;
        int on = dark ? 1 : 0;
        DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ref on, sizeof(int));
        int round = 2;
        DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ref round, sizeof(int));

        if (Environment.OSVersion.Version.Build < 22621) return false;
        var m = new Margins { L = -1, R = -1, T = -1, B = -1 };
        DwmExtendFrameIntoClientArea(hwnd, ref m);
        int acrylic = 3;
        bool ok = DwmSetWindowAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, ref acrylic, sizeof(int)) == 0;
        if (ok) HwndSource.FromHwnd(hwnd)!.CompositionTarget!.BackgroundColor = Colors.Transparent;
        return ok;
    }
}
