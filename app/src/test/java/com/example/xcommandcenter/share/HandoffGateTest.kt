package com.example.xcommandcenter.share

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.data.prefs.ShareAccessRepository
import com.example.xcommandcenter.data.prefs.settingsStore
import com.example.xcommandcenter.domain.DataSharing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

/** Hand-off screens let in only apps on the Data sharing list, matched by package and certificate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HandoffGateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val access = ShareAccessRepository(context)
    private val gate = HandoffGate(context.packageManager, context.packageName, access)
    private val monitor = "com.example.znotifmonitor"
    private val key = byteArrayOf(1, 2, 3, 4)

    @Before
    fun setUp() = runBlocking<Unit> {
        context.settingsStore.edit { it.clear() }
        install(monitor, key)
    }

    private fun install(packageName: String, certificate: ByteArray) {
        val signing = Shadow.newInstanceOf(SigningInfo::class.java)
        shadowOf(signing).setSignatures(arrayOf(Signature(certificate)))
        val info = PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            signingInfo = signing
        }
        shadowOf(context.packageManager).installPackage(info)
    }

    private fun check(callingPackage: String?) = runBlocking { gate.check(callingPackage) }

    @Test
    fun unidentifiedCallersAreRefused() {
        assertEquals(CallerVerdict.Unknown, check(null))
        assertEquals(CallerVerdict.Unknown, check("com.example.not.installed"))
    }

    @Test
    fun theAppItselfIsAllowed() {
        assertEquals(CallerVerdict.Allowed, check(context.packageName))
    }

    @Test
    fun anAppNotOnTheListIsRefusedAndListedAsARequest() = runBlocking {
        val verdict = check(monitor)
        assertEquals(CallerVerdict.NotAllowed(monitor), verdict)
        assertEquals("Not allowed: $monitor. Allow it under Settings > Data sharing.", (verdict as CallerVerdict.NotAllowed).message)
        val pending = access.pending.first().single()
        assertEquals(monitor, pending.packageName)
        assertEquals(DataSharing.fingerprint(key), pending.certSha256)
    }

    @Test
    fun anAllowedAppIsLetInUntilItsCertificateChanges() = runBlocking {
        check(monitor)
        access.allow(access.pending.first().single())
        assertEquals(CallerVerdict.Allowed, check(monitor))

        install(monitor, byteArrayOf(9, 9, 9))
        assertEquals(CallerVerdict.NotAllowed(monitor), check(monitor))
        access.revoke(access.allowed.first().single())
        install(monitor, key)
        assertEquals(CallerVerdict.NotAllowed(monitor), check(monitor))
    }
}
