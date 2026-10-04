import com.lagradost.cloudstream3.desktop.updater.DesktopAppUpdater
import com.lagradost.cloudstream3.desktop.updater.GitHubRelease
import com.lagradost.cloudstream3.desktop.updater.GitHubReleaseAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopAppUpdaterTest {

    @Test
    fun `parses version numbers accurately`() {
        assertEquals(listOf(0, 1, 0), DesktopAppUpdater.parseVersionNumbers("v0.1.0"))
        assertEquals(listOf(1, 2, 3), DesktopAppUpdater.parseVersionNumbers("1.2.3"))
        assertEquals(listOf(0, 2, 0), DesktopAppUpdater.parseVersionNumbers("v0.2.0-beta1"))
    }

    @Test
    fun `detects newer versions correctly`() {
        assertTrue(DesktopAppUpdater.isNewerVersion("v0.2.0", "0.1.0"))
        assertTrue(DesktopAppUpdater.isNewerVersion("v0.1.1", "0.1.0"))
        assertTrue(DesktopAppUpdater.isNewerVersion("v1.0.0", "0.1.0"))
        assertTrue(DesktopAppUpdater.isNewerVersion("v0.1.0.1", "0.1.0"))

        assertFalse(DesktopAppUpdater.isNewerVersion("v0.1.0", "0.1.0"))
        assertFalse(DesktopAppUpdater.isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(DesktopAppUpdater.isNewerVersion("v0.0.9", "0.1.0"))
        assertFalse(DesktopAppUpdater.isNewerVersion("v0.0.1", "0.1.0"))
    }

    @Test
    fun `finds matching asset for current platform`() {
        val release = GitHubRelease(
            tagName = "v0.2.0",
            name = "Release 0.2.0",
            assets = listOf(
                GitHubReleaseAsset(name = "CloudStream-Desktop-0.2.0.msi", downloadUrl = "https://example.com/win.msi", size = 90000000L),
                GitHubReleaseAsset(name = "CloudStream-Desktop-0.2.0.AppImage", downloadUrl = "https://example.com/linux.AppImage", size = 85000000L),
                GitHubReleaseAsset(name = "cloudstream-desktop_0.2.0_amd64.deb", downloadUrl = "https://example.com/linux.deb", size = 75000000L),
            ),
        )

        val asset = DesktopAppUpdater.findMatchingAsset(release)
        assertNotNull(asset)

        val os = System.getProperty("os.name")?.lowercase() ?: ""
        if (os.contains("linux")) {
            assertTrue(asset.name.endsWith(".AppImage") || asset.name.endsWith(".deb"))
        } else if (os.contains("win")) {
            assertTrue(asset.name.endsWith(".msi") || asset.name.endsWith(".exe"))
        }
    }
}
