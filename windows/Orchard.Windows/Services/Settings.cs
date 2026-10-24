using System.IO;
using System.Text.Json;
using Microsoft.Win32;

namespace Orchard.Windows;

public sealed class Settings
{
    public bool PauseOnRemove { get; set; } = true;
    public bool ResumeOnInsert { get; set; } = true;
    public bool PopupOnLidOpen { get; set; } = true;
    public bool AutoUpdate { get; set; } = true;
    /// <summary>이 PC에서 재생하면 에어팟을 자동으로 가져온다(끄면 '이 PC로 가져오기' 버튼으로만).</summary>
    public bool AutoSwitch { get; set; } = true;

    static readonly string Dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Orchard");
    static readonly string FilePath = Path.Combine(Dir, "settings.json");

    public static Settings Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<Settings>(File.ReadAllText(FilePath)) ?? new();
        }
        catch (Exception e) { Log.Write($"설정 읽기 실패: {e.Message}"); }
        return new();
    }

    /// <summary>임시 파일에 쓴 뒤 바꿔치기해서 중간에 꺼져도 깨지지 않게 저장한다.</summary>
    public void Save()
    {
        Directory.CreateDirectory(Dir);
        var tmp = FilePath + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        File.Move(tmp, FilePath, overwrite: true);
    }
}

public static class AutoStart
{
    const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

    public static bool IsOn
    {
        get
        {
            using var k = Registry.CurrentUser.OpenSubKey(RunKey);
            return k?.GetValue("Orchard") is string;
        }
    }

    public static void Set(bool on)
    {
        using var k = Registry.CurrentUser.CreateSubKey(RunKey);
        if (on) k.SetValue("Orchard", $"\"{Environment.ProcessPath}\" --background");
        else k.DeleteValue("Orchard", false);
    }
}

public static class Log
{
    static readonly string FilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Orchard", "orchard.log");
    static readonly object Gate = new();

    public static void Write(string msg)
    {
        try
        {
            lock (Gate)
            {
                Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
                var fi = new FileInfo(FilePath);
                if (fi.Exists && fi.Length > 1_000_000) File.Move(FilePath, FilePath + ".1", true);
                File.AppendAllText(FilePath, $"{DateTime.Now:yyyy-MM-dd HH:mm:ss} {msg}{Environment.NewLine}");
            }
        }
        catch { /* 로그 실패로 앱이 멈추면 안 된다 */ }
    }
}
