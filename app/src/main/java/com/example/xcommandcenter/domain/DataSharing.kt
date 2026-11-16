package com.example.xcommandcenter.domain

import java.security.MessageDigest

/**
 * An app that asked to read the shared data, or that may: identified by its package name and the
 * SHA-256 of its signing certificate, with the label it showed and when it asked or was allowed.
 */
data class ShareApp(
    val packageName: String,
    val label: String,
    val certSha256: String,
    val at: Long,
)

/**
 * Who may read the shared data (see `ShareProvider`). Nothing is shared until the user allows an
 * app in Settings; an app that asks first is kept as a request, at most [MAX_PENDING] of them,
 * newest first. An allowed entry covers only the same package signed with the same certificate,
 * so another app reusing the package name (signed with another key) is not let in.
 */
object DataSharing {
    const val MAX_PENDING = 5

    fun matches(app: ShareApp, packageName: String, certSha256: String): Boolean =
        app.packageName == packageName && app.certSha256.equals(certSha256, ignoreCase = true)

    fun isAllowed(allowed: List<ShareApp>, packageName: String, certSha256: String): Boolean =
        allowed.any { matches(it, packageName, certSha256) }

    /** Adds [request] as the newest, replacing an earlier one from the same package and certificate; the oldest beyond [MAX_PENDING] are dropped. */
    fun addPending(pending: List<ShareApp>, request: ShareApp): List<ShareApp> =
        (listOf(request) + pending.filterNot { matches(it, request.packageName, request.certSha256) }).take(MAX_PENDING)

    /** Removes [app]'s entry (same package and certificate) from [list]. */
    fun remove(list: List<ShareApp>, app: ShareApp): List<ShareApp> =
        list.filterNot { matches(it, app.packageName, app.certSha256) }

    /** Allows [app] from [now] on: the allowed list gains it (once) and it is no longer pending. */
    fun allow(allowed: List<ShareApp>, pending: List<ShareApp>, app: ShareApp, now: Long): Pair<List<ShareApp>, List<ShareApp>> =
        (remove(allowed, app) + app.copy(at = now)) to remove(pending, app)

    /** A certificate's SHA-256 as keytool prints it: upper-case hex pairs joined by colons. */
    fun fingerprint(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate).joinToString(":") { "%02X".format(it) }

    /** The first four and last two pairs, enough to compare by eye: "4A:AE:08:8B…8E:7D". */
    fun shortFingerprint(fingerprint: String): String {
        val pairs = fingerprint.split(":", ";")
        return if (pairs.size <= 6) fingerprint else pairs.take(4).joinToString(":") + "…" + pairs.takeLast(2).joinToString(":")
    }
}
