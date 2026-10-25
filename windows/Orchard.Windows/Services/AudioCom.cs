namespace Orchard.Windows;

/// <summary>
/// 윈도우 오디오 장치 목록(MMDeviceEnumerator) 만들기.
/// 이 부품은 프로그램 안에서 하나를 돌려쓰므로, 파일마다 따로 선언한 [ComImport] 틀로 new 하면
/// 먼저 만든 쪽 틀이 붙은 채 돌아와 "같은 이름인데 바꿀 수 없음" 오류가 난다(0.1.5~0.1.8 자동 전환 실패 원인).
/// 틀 없이 CLSID 로 만들고 각자 필요한 인터페이스로만 바꿔 쓴다.
/// </summary>
static class AudioCom
{
    static readonly Guid ClsidMMDeviceEnumerator = new("BCDE0395-E52F-467C-8E3D-C4579291692E");

    public static object CreateEnumerator() =>
        Activator.CreateInstance(Type.GetTypeFromCLSID(ClsidMMDeviceEnumerator, throwOnError: true)!)!;
}
