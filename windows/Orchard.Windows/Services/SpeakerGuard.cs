using System.Runtime.InteropServices;

namespace Orchard.Windows;

/// <summary>
/// 에어팟을 가져오는 동안 지금 기본 출력(대개 PC 스피커)을 잠깐 음소거해 소리가 새지 않게 한다.
/// 일시정지를 무시하는 앱(일부 브라우저 영상 등)이 있어도 스피커로는 나오지 않는다.
/// </summary>
public sealed class SpeakerGuard : IDisposable
{
    readonly IAudioEndpointVolume? _volume;
    readonly bool _wasMuted;

    SpeakerGuard()
    {
        try
        {
            var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
            if (enumerator.GetDefaultAudioEndpoint(0 /* eRender */, 0 /* eConsole */, out var device) < 0) return;
            var iid = typeof(IAudioEndpointVolume).GUID;
            if (device.Activate(ref iid, 0x17, IntPtr.Zero, out var obj) < 0) return;
            _volume = (IAudioEndpointVolume)obj;
            _volume.GetMute(out _wasMuted);
            if (!_wasMuted) _volume.SetMute(true, Guid.Empty);
        }
        catch (Exception e) { Log.Write($"스피커 음소거 실패: {e.Message}"); }
    }

    /// <summary>전용 STA 스레드에서 만들고 풀어야 하므로 UI 스레드에서 부른다.</summary>
    public static SpeakerGuard Engage() => new();

    public void Dispose()
    {
        try { if (_volume != null && !_wasMuted) _volume.SetMute(false, Guid.Empty); }
        catch (Exception e) { Log.Write($"스피커 음소거 풀기 실패: {e.Message}"); }
    }

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    class MMDeviceEnumeratorComObject { }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator
    {
        [PreserveSig] int EnumAudioEndpoints(int dataFlow, uint stateMask, out IntPtr devices);
        [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice
    {
        [PreserveSig] int Activate(ref Guid iid, uint clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object o);
    }

    [ComImport, Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioEndpointVolume
    {
        void RegisterControlChangeNotify(IntPtr notify);
        void UnregisterControlChangeNotify(IntPtr notify);
        void GetChannelCount(out uint count);
        void SetMasterVolumeLevel(float level, ref Guid ctx);
        void SetMasterVolumeLevelScalar(float level, ref Guid ctx);
        void GetMasterVolumeLevel(out float level);
        void GetMasterVolumeLevelScalar(out float level);
        void SetChannelVolumeLevel(uint channel, float level, ref Guid ctx);
        void SetChannelVolumeLevelScalar(uint channel, float level, ref Guid ctx);
        void GetChannelVolumeLevel(uint channel, out float level);
        void GetChannelVolumeLevelScalar(uint channel, out float level);
        void SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, [MarshalAs(UnmanagedType.LPStruct)] Guid ctx);
        void GetMute([MarshalAs(UnmanagedType.Bool)] out bool mute);
    }
}
