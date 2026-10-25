using Orchard.Core;

namespace Orchard.Core.Tests;

public class JosaTests
{
    [Theory]
    [InlineData("갤럭시", "갤럭시로", "갤럭시가")]
    [InlineData("내 폰", "내 폰으로", "내 폰이")]
    [InlineData("거실 PC", "거실 PC로", "거실 PC가")]
    [InlineData("서울", "서울로", "서울이")]
    [InlineData("Galaxy S26", "Galaxy S26로", "Galaxy S26가")]
    public void 받침에_맞는_조사를_붙인다(string name, string ro, string iga)
    {
        Assert.Equal(ro, Josa.Ro(name));
        Assert.Equal(iga, Josa.IGa(name));
    }
}
