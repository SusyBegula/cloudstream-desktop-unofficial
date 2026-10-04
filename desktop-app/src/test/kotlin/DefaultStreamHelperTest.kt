import com.lagradost.cloudstream3.desktop.utils.DefaultStreamHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultStreamHelperTest {

    private fun createLink(name: String, quality: Int, url: String = "https://example.com/$name/$quality"): ExtractorLink {
        return ExtractorLink(
            source = "TestSource",
            name = name,
            url = url,
            referer = "",
            quality = quality,
            type = ExtractorLinkType.VIDEO,
        )
    }

    @Test
    fun `matches only link with matching name and quality`() {
        val link1080 = createLink("StreamPlay", 1080)
        val link720 = createLink("StreamPlay", 720)
        val link480 = createLink("StreamPlay", 480)
        val differentSource1080 = createLink("OtherSource", 1080)

        val pref1080 = DefaultStreamHelper.buildIdentifier(link1080)
        assertEquals("StreamPlay::1080", pref1080)

        assertTrue(DefaultStreamHelper.matches(link1080, pref1080))
        assertFalse(DefaultStreamHelper.matches(link720, pref1080))
        assertFalse(DefaultStreamHelper.matches(link480, pref1080))
        assertFalse(DefaultStreamHelper.matches(differentSource1080, pref1080))
    }

    @Test
    fun `supports legacy preference without quality delimiter`() {
        val link1080 = createLink("StreamPlay", 1080)
        val link720 = createLink("StreamPlay", 720)
        val differentSource = createLink("OtherSource", 1080)

        val legacyPref = "StreamPlay"

        assertTrue(DefaultStreamHelper.matches(link1080, legacyPref))
        assertTrue(DefaultStreamHelper.matches(link720, legacyPref))
        assertFalse(DefaultStreamHelper.matches(differentSource, legacyPref))
    }

    @Test
    fun `formats display name with quality suffix`() {
        assertEquals("StreamPlay (1080p)", DefaultStreamHelper.formatDisplayName("StreamPlay::1080"))
        assertEquals("StreamPlay (720p)", DefaultStreamHelper.formatDisplayName("StreamPlay::720"))
        assertEquals("StreamPlay", DefaultStreamHelper.formatDisplayName("StreamPlay::0"))
        assertEquals("LegacyStream", DefaultStreamHelper.formatDisplayName("LegacyStream"))
        assertEquals("", DefaultStreamHelper.formatDisplayName(null))
    }

    @Test
    fun `sorting links puts highest quality first`() {
        val links = listOf(
            createLink("Server B", 720),
            createLink("Server A", 1080),
            createLink("Server C", 360),
            createLink("Server D", 2160),
            createLink("Server B", 1080),
        )

        val sorted = links.sortedWith(
            compareByDescending<ExtractorLink> { it.quality }.thenBy { it.name.lowercase() }
        )

        assertEquals(2160, sorted[0].quality)
        assertEquals(1080, sorted[1].quality)
        assertEquals("Server A", sorted[1].name)
        assertEquals(1080, sorted[2].quality)
        assertEquals("Server B", sorted[2].name)
        assertEquals(720, sorted[3].quality)
        assertEquals(360, sorted[4].quality)
    }
}
