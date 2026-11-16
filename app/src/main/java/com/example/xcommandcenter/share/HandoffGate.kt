package com.example.xcommandcenter.share

import android.content.pm.PackageManager
import com.example.xcommandcenter.data.prefs.ShareAccessRepository

/** Whether the app that started a hand-off screen may ask it anything. */
sealed interface CallerVerdict {
    data object Allowed : CallerVerdict

    /** Not on the Data sharing list; it is now listed there as a request. */
    data class NotAllowed(val packageName: String) : CallerVerdict {
        val message: String get() = "Not allowed: $packageName. Allow it under Settings > Data sharing."
    }

    /** The caller couldn't be told: it didn't use startActivityForResult, or has no certificate. */
    data object Unknown : CallerVerdict {
        const val MESSAGE = "The app asking couldn't be identified, so nothing was done. " +
            "It has to start this screen with startActivityForResult."
    }
}

/**
 * The hand-off screens' version of [ShareProvider]'s check: the same allowed list, matched by
 * package and signing certificate. An activity learns its caller from `callingPackage`, which
 * the system fills in only for startActivityForResult, so other starts are [CallerVerdict.Unknown].
 */
class HandoffGate(
    private val packageManager: PackageManager,
    private val ownPackage: String,
    private val access: ShareAccessRepository,
) {
    suspend fun check(callingPackage: String?): CallerVerdict {
        if (callingPackage == null) return CallerVerdict.Unknown
        if (callingPackage == ownPackage) return CallerVerdict.Allowed
        val caller = CallerIdentity.of(packageManager, callingPackage) ?: return CallerVerdict.Unknown
        return if (access.check(caller)) CallerVerdict.Allowed else CallerVerdict.NotAllowed(callingPackage)
    }
}
