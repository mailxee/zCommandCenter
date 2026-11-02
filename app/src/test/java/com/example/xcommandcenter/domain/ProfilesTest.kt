package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilesTest {
    @Test
    fun `a bare handle becomes an X profile, a named one that platform's`() {
        assertEquals("x.com/RyanHoliday", Profiles.normalize("@RyanHoliday"))
        assertEquals("youtube.com/@DailyStoic", Profiles.normalize("youtube @DailyStoic"))
        assertEquals("youtube.com/@DailyStoic", Profiles.normalize("yt: @DailyStoic"))
        assertEquals("instagram.com/name", Profiles.normalize("IG @name"))
        assertEquals("linkedin.com/in/jane-doe", Profiles.normalize("linkedin @jane-doe"))
        // An unknown platform name is kept as typed.
        assertEquals("myspace @name", Profiles.normalize("myspace @name"))
    }

    @Test
    fun `an address loses its scheme, www and trailing slash`() {
        assertEquals("x.com/name", Profiles.normalize("  https://www.x.com/name/ "))
        assertEquals("example.org", Profiles.normalize("http://example.org"))
        assertEquals("", Profiles.normalize("   "))
    }

    @Test
    fun `the platform comes from the host`() {
        assertEquals(Platform.X, Profiles.platformOf("twitter.com/name"))
        assertEquals(Platform.X, Profiles.platformOf("https://x.com/name"))
        assertEquals(Platform.YOUTUBE, Profiles.platformOf("m.youtube.com/@name"))
        assertEquals(Platform.YOUTUBE, Profiles.platformOf("youtu.be/abc"))
        assertEquals(Platform.GITHUB, Profiles.platformOf("github.com/name"))
        assertEquals(Platform.WEBSITE, Profiles.platformOf("ryanholiday.net"))
        // A look-alike domain is not the platform.
        assertEquals(Platform.WEBSITE, Profiles.platformOf("notx.com/name"))
    }

    @Test
    fun `a stored profile opens over https`() {
        assertEquals("https://x.com/name", Profiles.url("x.com/name"))
        assertEquals("http://example.org", Profiles.url("http://example.org"))
    }

    @Test
    fun `an item's kind comes from its link`() {
        assertEquals(PersonItemKind.IDEA, Profiles.kindOf("Ego is the enemy"))
        assertEquals(PersonItemKind.VIDEO, Profiles.kindOf("Talk https://www.youtube.com/watch?v=abc"))
        assertEquals(PersonItemKind.VIDEO, Profiles.kindOf("Clip https://youtu.be/abc"))
        assertEquals(PersonItemKind.POST, Profiles.kindOf("Thread https://x.com/name/status/1"))
        assertEquals(PersonItemKind.LINK, Profiles.kindOf("Essay https://example.org/essay"))
    }
}
