using System.Windows.Media.Imaging;
using Orchard.Core;

namespace Orchard.Windows;

public static class DeviceArt
{
    static string Key(int id) => id switch
    {
        0x2002 or 0x200F => "airpods_2",
        0x2013 => "airpods_3",
        0x2019 => "airpods_4",
        0x201B => "airpods_4_anc",
        0x2030 => "airpods_5_wireless",
        0x200E => "airpods_pro_1",
        0x2014 or 0x2024 => "airpods_pro_2",
        0x2027 => "airpods_pro_3",
        0x200A or 0x201F or 0x202D => "airpods_max",
        _ => "airpods_4_anc",
    };

    static BitmapImage Load(string name) =>
        new(new Uri($"pack://application:,,,/Assets/devices/{name}.png"));

    public static BitmapImage Buds(AirPodsModel m) =>
        m.IsHeadphones ? Load("airpods_max") : Load(Key(m.Id) + "_buds");

    public static BitmapImage? Case(AirPodsModel m) =>
        m.IsHeadphones ? null : Load(Key(m.Id) + "_case");
}
