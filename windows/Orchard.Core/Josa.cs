namespace Orchard.Core;

/// <summary>이름 뒤 조사: 받침 있으면 '으로/이', 없으면 '로/가'(ㄹ 받침은 '로'). 한글로 끝나지 않으면 받침 없는 것으로.</summary>
public static class Josa
{
    static int? Final(string s)
    {
        if (s.Length == 0) return null;
        char c = s[^1];
        return c is >= '가' and <= '힣' ? (c - '가') % 28 : null;
    }

    public static string Ro(string name) => name + (Final(name) is int f && f != 0 && f != 8 ? "으로" : "로");
    public static string IGa(string name) => name + (Final(name) is int f && f != 0 ? "이" : "가");
}
