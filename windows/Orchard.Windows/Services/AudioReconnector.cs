using System.Runtime.InteropServices;

namespace Orchard.Windows;

/// <summary>
/// 페어링된 블루투스 오디오 기기(에어팟)를 이 PC로 다시 연결하거나 끊는다.
/// 블루투스 오디오 드라이버의 KSPROPERTY_ONESHOT_RECONNECT/DISCONNECT 를 쓴다(ToothTray 등과 같은 방식).
/// 오디오 끝점 → 토폴로지 → 연결된 KS 필터의 장치 ID에 블루투스 주소가 들어 있는 것을 고른다.
/// </summary>
public static class AudioReconnector
{
    public sealed record Endpoint(string Name, string FilterId, bool Active);

    /// <summary>주소(12자리 16진수)가 일치하는 오디오 끝점들. 개발 확인용으로도 쓴다.</summary>
    public static List<Endpoint> Find(string address)
    {
        return OnSta(() =>
        {
            var list = new List<Endpoint>();
            ForEachFilter(address, (name, id, active, _) => { list.Add(new Endpoint(name, id, active)); return false; });
            return list;
        });
    }

    public static bool Reconnect(string address) => OnSta(() =>
    {
        RememberContainers(address);
        return Send(address, KSPROPERTY_ONESHOT_RECONNECT);
    });

    /// <summary>
    /// 이 에어팟의 스테레오(A2DP) 재생 끝점이 지금 쓸 수 있으면 그 ID, 아니면 null.
    /// 퀄컴 칩 PC(서피스 프로 등)는 실제 소리를 블루투스 주소가 안 들어간 별도 경로(qcasd#a2dpspeaker…)로 내보낸다.
    /// 그래서 주소로 찾은 끝점의 '컨테이너 ID'(같은 물리 기기면 같음)로 활성 스테레오 끝점을 고른다.
    /// </summary>
    /// <param name="name">블루투스 기기 이름(예: "AirPods"). 끝점의 기기 이름과 같으면 내 것으로 본다 —
    /// 서피스는 다시 연결할 때마다 새 컨테이너 ID 의 끝점을 만들어, 기억한 컨테이너로는 못 찾고 10초를 넘겼다(0.2.0 실기).</param>
    public static string? ActiveStereoEndpoint(string address, string? name = null) => OnSta(() =>
    {
        RememberContainers(address);
        Containers.TryGetValue(Key(address), out var known);
        string? found = null;
        ForEachFilter("", (endpointId, filterId, active, device) =>
        {
            if (!active || !endpointId.StartsWith("{0.0.0.")) return false;
            var f = filterId.ToLowerInvariant();
            bool stereo = f.Contains("0000110b") || f.Contains("a2dp");
            bool mine = f.Contains(Key(address))
                        || (!string.IsNullOrEmpty(name) && string.Equals(InterfaceName(device), name, StringComparison.OrdinalIgnoreCase))
                        || (known != null && ContainerId(device) is Guid g && known.Contains(g));
            if (stereo && mine) { found = endpointId; return true; }
            return false;
        });
        return found;
    });

    // 주소 → 그 기기 끝점들의 컨테이너 ID. 연결되고 나면 주소 든 끝점을 못 읽는 PC가 있어, 읽을 수 있을 때(연결 전) 기억해 둔다.
    static readonly Dictionary<string, HashSet<Guid>> Containers = new();
    static string Key(string address) => address.Replace(":", "").ToLowerInvariant();

    /// <summary>앱이 켜질 때(에어팟이 아직 안 붙어 있을 때) 미리 기억해 둔다.</summary>
    public static void Prepare(string address) => OnSta(() => { RememberContainers(address); return true; });

    static void RememberContainers(string address)
    {
        ForEachFilter(address, (_, _, _, device) =>
        {
            if (ContainerId(device) is Guid g)
            {
                lock (Containers)
                {
                    if (!Containers.TryGetValue(Key(address), out var set)) Containers[Key(address)] = set = new();
                    set.Add(g);
                }
            }
            return false;
        });
    }

    /// <summary>확인용: 이 기기(컨테이너)의 지금 활성인 끝점 이름들.</summary>
    public static string ActiveOfDevice(string address) => OnSta(() =>
    {
        Containers.TryGetValue(Key(address), out var known);
        var names = new List<string>();
        ForEachFilter("", (endpointId, filterId, active, device) =>
        {
            if (active && known != null && ContainerId(device) is Guid g && known.Contains(g))
                names.Add((endpointId.StartsWith("{0.0.0.") ? "출력:" : "입력:") + (filterId.Contains("a2dp", StringComparison.OrdinalIgnoreCase) ? "스테레오" : "통화"));
            return false;
        });
        return string.Join(",", names);
    });

    /// <summary>확인용: 모든 재생 끝점의 ID·활성·컨테이너·필터 앞부분을 기록한다.</summary>
    public static void DumpRender() => OnSta(() =>
    {
        ForEachFilter("", (endpointId, filterId, active, device) =>
        {
            if (endpointId.StartsWith("{0.0.0."))
                Log.Write($"재생 끝점 {endpointId[^38..]} · {InterfaceName(device)} · 활성 {active} · 컨테이너 {ContainerId(device)} · {filterId[..Math.Min(70, filterId.Length)]}");
            return false;
        });
        return true;
    });

    /// <summary>끝점의 PKEY_DeviceInterface_FriendlyName — "헤드폰(AirPods)"의 괄호 안 기기 이름("AirPods").</summary>
    static string? InterfaceName(IMMDevice device)
    {
        IntPtr storePtr = IntPtr.Zero;
        try
        {
            device.OpenPropertyStore(0 /* STGM_READ */, out storePtr);
            var store = (IPropertyStore)Marshal.GetObjectForIUnknown(storePtr);
            var key = new PropertyKey { Fmtid = new Guid("026E516E-B814-414B-83CD-856D6FEF4822"), Pid = 2 };
            var pv = new PropVariant();
            if (store.GetValue(ref key, ref pv) < 0) return null;
            try { return pv.Vt == 31 /* VT_LPWSTR */ && pv.Ptr != IntPtr.Zero ? Marshal.PtrToStringUni(pv.Ptr) : null; }
            finally { PropVariantClear(ref pv); }
        }
        catch { return null; }
        finally { if (storePtr != IntPtr.Zero) Marshal.Release(storePtr); }
    }

    /// <summary>끝점의 PKEY_Device_ContainerId — 같은 물리 기기의 끝점들은 같은 값을 가진다.</summary>
    static Guid? ContainerId(IMMDevice device)
    {
        IntPtr storePtr = IntPtr.Zero;
        try
        {
            device.OpenPropertyStore(0 /* STGM_READ */, out storePtr);
            var store = (IPropertyStore)Marshal.GetObjectForIUnknown(storePtr);
            var key = new PropertyKey { Fmtid = new Guid("8C7ED206-3F8A-4827-B3AB-AE9E1FAEFC6C"), Pid = 2 };
            var pv = new PropVariant();
            if (store.GetValue(ref key, ref pv) < 0) return null;
            try { return pv.Vt == 72 /* VT_CLSID */ && pv.Ptr != IntPtr.Zero ? Marshal.PtrToStructure<Guid>(pv.Ptr) : null; }
            finally { PropVariantClear(ref pv); }
        }
        catch { return null; }
        finally { if (storePtr != IntPtr.Zero) Marshal.Release(storePtr); }
    }

    /// <summary>
    /// 끝점을 윈도우 기본 출력(일반·멀티미디어·통신 모두)으로 정한다.
    /// 공식 API가 없어 소리 설정 창과 같은 내부 인터페이스(IPolicyConfig)를 쓴다 — SoundSwitch·EarTrumpet 등과 같은 방식.
    /// </summary>
    public static bool MakeDefault(string endpointId) => OnSta(() =>
    {
        var policy = (IPolicyConfig)Activator.CreateInstance(Type.GetTypeFromCLSID(ClsidPolicyConfigClient, throwOnError: true)!)!;
        bool ok = true;
        for (int role = 0; role < 3; role++) ok &= policy.SetDefaultEndpoint(endpointId, role) >= 0;
        Log.Write($"기본 출력 → {endpointId}: {(ok ? "바꿈" : "실패")}");
        return ok;
    });

    public static bool Disconnect(string address) => OnSta(() => Send(address, KSPROPERTY_ONESHOT_DISCONNECT));

    /// <summary>
    /// 오디오 장치 COM 부품은 만든 스레드에서만 쓸 수 있어, 찾기부터 명령까지 한 STA 스레드에서 한다.
    /// (0.1.1: 배경 스레드에서 IKsControl 로 형 변환하다 'Specified cast is not valid' 로 전환 실패)
    /// </summary>
    static T OnSta<T>(Func<T> work)
    {
        T result = default!;
        Exception? error = null;
        var t = new Thread(() => { try { result = work(); } catch (Exception e) { error = e; } });
        t.SetApartmentState(ApartmentState.STA);
        t.Start();
        t.Join();
        if (error != null) Log.Write($"오디오 장치 작업 실패: {error.Message}");
        return result;
    }

    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int KsPropertyFn(IntPtr self, ref KsProperty property, int propertyLength, IntPtr data, int dataLength, out int bytesReturned);

    static bool Send(string address, uint prop)
    {
        bool sent = false;
        ForEachFilter(address, (name, id, active, dev) =>
        {
            IntPtr ks = IntPtr.Zero;
            try
            {
                var iid = typeof(IKsControl).GUID;
                // 제어 기능은 연결 단자가 아니라 블루투스 오디오 '필터 장치'에 있다: 필터 ID 로 장치를 다시 열어 꺼낸다
                var enumerator = (IMMDeviceEnumerator)AudioCom.CreateEnumerator();
                int ghr = enumerator.GetDevice(id, out var filterDevice);
                int ahr = ghr < 0 ? ghr : filterDevice.Activate(ref iid, CLSCTX_ALL, IntPtr.Zero, out ks);
                if (ahr < 0 || ks == IntPtr.Zero)
                {
                    Log.Write($"IKsControl 꺼내기 실패 0x{ahr:X8} (필터 {id})");
                    return false;
                }
                // 형 변환(QueryInterface) 없이 IKsControl::KsProperty(가상 함수표 4번째)를 바로 부른다
                var vtable = Marshal.ReadIntPtr(ks);
                var fn = Marshal.GetDelegateForFunctionPointer<KsPropertyFn>(Marshal.ReadIntPtr(vtable, 3 * IntPtr.Size));
                var p = new KsProperty { Set = KSPROPSETID_BtAudio, Id = prop, Flags = KSPROPERTY_TYPE_GET };
                int hr = fn(ks, ref p, Marshal.SizeOf<KsProperty>(), IntPtr.Zero, 0, out _);
                Log.Write($"{(prop == KSPROPERTY_ONESHOT_RECONNECT ? "다시 연결" : "연결 끊기")} 요청: {name} → 0x{hr:X8}");
                sent |= hr >= 0;
                return sent; // 한 번 성공하면 그만
            }
            catch (Exception e)
            {
                Log.Write($"KS 요청 실패({name}): {e}");
                return false;
            }
            finally
            {
                if (ks != IntPtr.Zero) Marshal.Release(ks);
            }
        });
        return sent;
    }

    /// <summary>visit 이 true 를 돌려주면 멈춘다.</summary>
    static void ForEachFilter(string address, Func<string, string, bool, IMMDevice, bool> visit)
    {
        var addr = address.Replace(":", "").ToLowerInvariant();
        var enumerator = (IMMDeviceEnumerator)AudioCom.CreateEnumerator();
        foreach (var flow in new[] { EDataFlow.eRender, EDataFlow.eCapture })
        {
            // 모든 상태: 퀄컴 PC는 연결되면 주소 들어간 끝점이 '없음' 상태로 바뀐다(컨테이너 ID 를 읽으려면 필요)
            enumerator.EnumAudioEndpoints(flow, 0xF /* DEVICE_STATEMASK_ALL */, out var devices);
            devices.GetCount(out uint count);
            for (uint i = 0; i < count; i++)
            {
                try
                {
                    devices.Item(i, out var device);
                    device.GetState(out uint state);
                    var iid = typeof(IDeviceTopology).GUID;
                    if (device.Activate(ref iid, CLSCTX_ALL, IntPtr.Zero, out var topoPtr) < 0) continue;
                    var topo = (IDeviceTopology)Marshal.GetObjectForIUnknown(topoPtr);
                    Marshal.Release(topoPtr);
                    topo.GetConnector(0, out var connector);
                    connector.GetConnectedTo(out var other);
                    var part = (IPart)other;
                    part.GetTopologyObject(out var filterTopo);
                    filterTopo.GetDeviceId(out string filterId);
                    if (!filterId.ToLowerInvariant().Contains(addr)) continue;
                    device.GetId(out string endpointId);
                    if (visit(endpointId, filterId, state == DEVICE_STATE_ACTIVE, device)) return;
                }
                // 블루투스가 아닌 끝점은 토폴로지가 다르고, '없음' 상태 끝점은 경로를 못 찾는다(0x80070003) — 건너뛴다
                catch (Exception e) when (e is COMException or InvalidCastException or System.IO.IOException or UnauthorizedAccessException) { }
            }
        }
    }

    // ---------- COM 정의 ----------
    [StructLayout(LayoutKind.Sequential)]
    struct PropertyKey { public Guid Fmtid; public uint Pid; }

    [StructLayout(LayoutKind.Explicit, Size = 24)]
    struct PropVariant { [FieldOffset(0)] public ushort Vt; [FieldOffset(8)] public IntPtr Ptr; }

    [DllImport("ole32.dll")] static extern int PropVariantClear(ref PropVariant pv);

    [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPropertyStore
    {
        [PreserveSig] int GetCount(out uint count);
        [PreserveSig] int GetAt(uint index, out PropertyKey key);
        [PreserveSig] int GetValue(ref PropertyKey key, ref PropVariant value);
    }

    static readonly Guid ClsidPolicyConfigClient = new("870AF99C-171D-4F9E-AF0D-E63DF40C2BC9");

    [ComImport, Guid("F8679F50-850A-41CF-9C72-430F290290C8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPolicyConfig
    {
        // 앞의 10개는 쓰지 않지만 순서(가상 함수표 자리)를 맞추려고 둔다
        [PreserveSig] int GetMixFormat(IntPtr a, IntPtr b);
        [PreserveSig] int GetDeviceFormat(IntPtr a, int b, IntPtr c);
        [PreserveSig] int ResetDeviceFormat(IntPtr a);
        [PreserveSig] int SetDeviceFormat(IntPtr a, IntPtr b, IntPtr c);
        [PreserveSig] int GetProcessingPeriod(IntPtr a, int b, IntPtr c, IntPtr d);
        [PreserveSig] int SetProcessingPeriod(IntPtr a, IntPtr b);
        [PreserveSig] int GetShareMode(IntPtr a, IntPtr b);
        [PreserveSig] int SetShareMode(IntPtr a, IntPtr b);
        [PreserveSig] int GetPropertyValue(IntPtr a, int b, IntPtr c, IntPtr d);
        [PreserveSig] int SetPropertyValue(IntPtr a, int b, IntPtr c, IntPtr d);
        [PreserveSig] int SetDefaultEndpoint([MarshalAs(UnmanagedType.LPWStr)] string deviceId, int role);
        [PreserveSig] int SetEndpointVisibility(IntPtr a, int b);
    }
    const uint CLSCTX_ALL = 0x17;
    const uint CLSCTX_INPROC_SERVER = 0x1;
    const uint DEVICE_STATE_ACTIVE = 0x1;
    const uint DEVICE_STATE_UNPLUGGED = 0x8;
    const uint KSPROPERTY_TYPE_GET = 0x1;
    const uint KSPROPERTY_ONESHOT_RECONNECT = 0;
    const uint KSPROPERTY_ONESHOT_DISCONNECT = 1;
    static readonly Guid KSPROPSETID_BtAudio = new("7FA06C40-B8F6-4C7E-8556-E8C33A12E54D");

    enum EDataFlow { eRender, eCapture, eAll }

    [StructLayout(LayoutKind.Sequential)]
    struct KsProperty { public Guid Set; public uint Id; public uint Flags; }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator
    {
        [PreserveSig] int EnumAudioEndpoints(EDataFlow dataFlow, uint stateMask, out IMMDeviceCollection devices);
        [PreserveSig] int GetDefaultAudioEndpoint(EDataFlow dataFlow, int role, out IMMDevice device);
        [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IMMDevice device);
    }

    [ComImport, Guid("0BD7A1BE-7A1A-44DB-8397-CC5392387B5E"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceCollection
    {
        [PreserveSig] int GetCount(out uint count);
        [PreserveSig] int Item(uint index, out IMMDevice device);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice
    {
        [PreserveSig] int Activate(ref Guid iid, uint clsCtx, IntPtr activationParams, out IntPtr o);
        void OpenPropertyStore(uint access, out IntPtr store);
        void GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
        void GetState(out uint state);
    }

    [ComImport, Guid("2A07407E-6497-4A18-9787-32F79BD0D98F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IDeviceTopology
    {
        void GetConnectorCount(out uint count);
        void GetConnector(uint index, out IConnector connector);
        void GetSubunitCount(out uint count);
        void GetSubunit(uint index, out IntPtr subunit);
        void GetPartById(uint id, out IPart part);
        void GetDeviceId([MarshalAs(UnmanagedType.LPWStr)] out string id);
    }

    [ComImport, Guid("9C2C4058-23F5-41DE-877A-DF3AF236A09E"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IConnector
    {
        void GetType(out int type);
        void GetDataFlow(out int flow);
        void ConnectTo(IConnector other);
        void Disconnect();
        void IsConnected(out bool connected);
        void GetConnectedTo(out IConnector other);
    }

    [ComImport, Guid("AE2DE0E4-5BCA-4F2D-AA46-5D13F8FDB3A9"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPart
    {
        void GetName([MarshalAs(UnmanagedType.LPWStr)] out string name);
        void GetLocalId(out uint id);
        void GetGlobalId([MarshalAs(UnmanagedType.LPWStr)] out string id);
        void GetPartType(out int type);
        void GetSubType(out Guid subType);
        void GetControlInterfaceCount(out uint count);
        void GetControlInterface(uint index, out IntPtr iface);
        void EnumPartsIncoming(out IntPtr parts);
        void EnumPartsOutgoing(out IntPtr parts);
        void GetTopologyObject(out IDeviceTopology topology);
        [PreserveSig] int Activate(uint clsContext, ref Guid iid, out IntPtr o);
    }

    [ComImport, Guid("28F54685-06FD-11D2-B27A-00A0C9223196"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IKsControl
    {
        [PreserveSig] int KsProperty(ref KsProperty property, int propertyLength, IntPtr data, int dataLength, out int bytesReturned);
    }
}
