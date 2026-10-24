package me.kavishdevar.librepods.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test fun 버전_비교() {
        assertTrue(UpdateLogic.isNewer("v0.2.0", "0.1.9"))
        assertTrue(UpdateLogic.isNewer("0.10.0", "0.9.3"))
        assertFalse(UpdateLogic.isNewer("v0.1.0", "0.1.0-debug"))
        assertFalse(UpdateLogic.isNewer("0.1.0", "0.2.0"))
        assertFalse(UpdateLogic.isNewer("엉망", "0.1.0"))
    }

    @Test fun 릴리스에서_안드로이드_apk를_고른다() {
        val json = """
            {"tag_name":"v0.2.0","body":"고친 것","draft":false,"prerelease":false,
             "assets":[
               {"name":"Orchard-win-Setup.exe","browser_download_url":"https://x/win.exe","size":10},
               {"name":"Orchard-android_v0.2.0.apk","browser_download_url":"https://x/a.apk","size":20}
             ]}
        """.trimIndent()
        val r = UpdateLogic.parseRelease(json)!!
        assertEquals("0.2.0", r.version)
        assertEquals("https://x/a.apk", r.apkUrl)
        assertEquals(20L, r.size)
        assertEquals("고친 것", r.notes)
    }

    @Test fun apk가_없거나_미리보기_릴리스면_null() {
        assertNull(UpdateLogic.parseRelease("""{"tag_name":"v0.2.0","assets":[]}"""))
        assertNull(UpdateLogic.parseRelease("""{"tag_name":"v0.2.0","prerelease":true,"assets":[{"name":"Orchard-android_v0.2.0.apk","browser_download_url":"u","size":1}]}"""))
    }
}
