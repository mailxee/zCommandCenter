package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSharingTest {
    private val cert = "4A:AE:08:8B:91:B7:3A:3F:E3:B7:62:9C:A1:6E:F4:5E:60:8F:92:8E:6D:3D:3B:50:5A:7A:38:CC:DB:93:8E:7D"
    private val otherCert = "11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF"
    private val monitor = ShareApp("com.example.znotifmonitor", "zNotifMonitor", cert, 100)

    @Test
    fun `nothing is allowed by default`() {
        assertFalse(DataSharing.isAllowed(emptyList(), monitor.packageName, cert))
    }

    @Test
    fun `an allowed app matches only with the same package and the same certificate`() {
        val allowed = listOf(monitor)
        assertTrue(DataSharing.isAllowed(allowed, "com.example.znotifmonitor", cert))
        assertTrue(DataSharing.isAllowed(allowed, "com.example.znotifmonitor", cert.lowercase()))
        // Same package, signed with another key: an impostor, or a reinstall that needs allowing again.
        assertFalse(DataSharing.isAllowed(allowed, "com.example.znotifmonitor", otherCert))
        // Same key, another package.
        assertFalse(DataSharing.isAllowed(allowed, "com.example.other", cert))
    }

    @Test
    fun `requests are newest first, one per app, and at most five`() {
        var pending = emptyList<ShareApp>()
        for (i in 1..7) pending = DataSharing.addPending(pending, ShareApp("app.$i", "App $i", cert, i.toLong()))
        assertEquals(listOf("app.7", "app.6", "app.5", "app.4", "app.3"), pending.map { it.packageName })

        // Asking again moves the request to the top, with its new time, instead of adding another.
        pending = DataSharing.addPending(pending, ShareApp("app.4", "App 4", cert, 50))
        assertEquals(listOf("app.4", "app.7", "app.6", "app.5", "app.3"), pending.map { it.packageName })
        assertEquals(50L, pending.first().at)

        // The same package with another key is a different request.
        pending = DataSharing.addPending(pending, ShareApp("app.4", "App 4", otherCert, 60))
        assertEquals(listOf("app.4", "app.4", "app.7", "app.6", "app.5"), pending.map { it.packageName })
    }

    @Test
    fun `allowing moves a request to the allowed list once`() {
        val (allowed, pending) = DataSharing.allow(listOf(monitor), listOf(monitor.copy(at = 200)), monitor.copy(at = 200), now = 300)
        assertEquals(listOf(monitor.copy(at = 300)), allowed)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `removing leaves another key's entry for the same package`() {
        val list = listOf(monitor, monitor.copy(certSha256 = otherCert))
        assertEquals(listOf(otherCert), DataSharing.remove(list, monitor).map { it.certSha256 })
    }

    @Test
    fun `fingerprints read like keytool's and shorten for display`() {
        val fingerprint = DataSharing.fingerprint("abc".toByteArray())
        assertEquals("BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD", fingerprint)
        assertEquals("4A:AE:08:8B…8E:7D", DataSharing.shortFingerprint(cert))
    }
}
