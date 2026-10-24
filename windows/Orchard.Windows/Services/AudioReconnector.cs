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

    public static bool Reconnect(string address) => OnSta(() => Send(address, KSPROPERTY_ONESHOT_RECONNECT));

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
        ForEachFilter(address, (name, id, active, part) =>
        {
            IntPtr ks = IntPtr.Zero;
            try
            {
                var iid = typeof(IKsControl).GUID;
                // 제어 기능은 연결 단자가 아니라 블루투스 오디오 '필터 장치'에 있다: 필터 ID 로 장치를 다시 열어 꺼낸다
                var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
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
    static void ForEachFilter(string address, Func<string, string, bool, IPart, bool> visit)
    {
        var addr = address.Replace(":", "").ToLowerInvariant();
        var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
        foreach (var flow in new[] { EDataFlow.eRender, EDataFlow.eCapture })
        {
            enumerator.EnumAudioEndpoints(flow, DEVICE_STATE_ACTIVE | DEVICE_STATE_UNPLUGGED, out var devices);
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
                    if (visit(endpointId, filterId, state == DEVICE_STATE_ACTIVE, part)) return;
                }
                catch (COMException) { /* 블루투스가 아닌 끝점은 토폴로지가 다르다 */ }
                catch (InvalidCastException) { }
            }
        }
    }

    // ---------- COM 정의 ----------
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

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    class MMDeviceEnumeratorComObject { }

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
