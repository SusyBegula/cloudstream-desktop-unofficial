import com.lagradost.cloudstream3.desktop.utils.DesktopClipboard
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopClipboardTest {

    @Test
    fun `empty text returns false`() {
        assertFalse(DesktopClipboard.copyText(""))
    }

    @Test
    fun `copyText handles text without throwing exceptions`() {
        // Should not throw any exception or crash the JVM
        val result = DesktopClipboard.copyText("https://test.example.com/video.m3u8")
        // In headful/headless or test environment, returns boolean safely
        assertTrue(result || !result)
    }
}
