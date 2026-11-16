package com.example.xcommandcenter.share

import android.content.pm.PackageManager
import android.os.Build
import com.example.xcommandcenter.domain.DataSharing
import com.example.xcommandcenter.domain.ShareApp

/** Who another installed app is: its package, label and signing-certificate fingerprint. */
object CallerIdentity {

    /**
     * [packageName]'s identity, or null if it isn't installed or has no certificate. The caller of
     * this must already know the package is the one asking (the system vouches for it).
     */
    fun of(pm: PackageManager, packageName: String, now: Long = System.currentTimeMillis()): ShareApp? {
        val certificates = try {
            signingCertificates(pm, packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        if (certificates.isEmpty()) return null
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
        // An app signed by several keys (rare) is identified by all of them together.
        val fingerprint = certificates.map { DataSharing.fingerprint(it) }.sorted().joinToString(";")
        return ShareApp(packageName, label, fingerprint, now)
    }

    /** The certificates the installed APK is signed with now (not earlier ones it rotated from). */
    private fun signingCertificates(pm: PackageManager, packageName: String): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            info?.apkContentsSigners.orEmpty().map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty().map { it.toByteArray() }
        }
}
