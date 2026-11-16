package com.example.xcommandcenter.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.data.db.AppDatabase
import com.example.xcommandcenter.domain.DataSharing
import com.example.xcommandcenter.domain.ShareApp
import kotlinx.coroutines.runBlocking

/**
 * Lets apps the user has allowed read some of the data, read-only: `content://<applicationId>.share/<path>`
 * for each path in [SharePath], each the whole table with Room's column names, plus `/meta`.
 * Projection, selection and sort order are ignored.
 *
 * Signature permissions can't protect it (Play re-signs this app with Google's key), so callers are
 * checked against the list the user keeps in Settings, by package name and signing certificate.
 * A caller not on it gets a [SecurityException] and is listed in Settings as a request.
 */
class ShareProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val context = requireNotNull(context)
        val path = SharePath.of(uri) ?: throw IllegalArgumentException("Unknown path $uri; the paths are ${SharePath.entries.joinToString { "/" + it.path }}")
        checkCaller(context)
        // The container is made in Application.onCreate, which has run by the time a query arrives.
        val container = (context.applicationContext as XCommandCenterApp).container
        return if (path == SharePath.META) meta(context) else path.read(container.shareDao)
    }

    /** Throws unless the calling app is this one or one the user has allowed. */
    private fun checkCaller(context: Context) {
        if (Binder.getCallingUid() == Process.myUid()) return
        val caller = caller(context) ?: throw SecurityException("The calling app could not be identified.")
        val container = (context.applicationContext as XCommandCenterApp).container
        val allowed = runBlocking { container.shareAccessRepository.check(caller) }
        if (!allowed) {
            throw SecurityException(
                "${caller.packageName} may not read this data yet. Open Manage Everything, Settings, Data sharing, and allow it.",
            )
        }
    }

    /** The caller's package (which the system checks belongs to the calling uid), label and certificate fingerprint. */
    private fun caller(context: Context): ShareApp? {
        val packageName = callingPackage ?: return null
        val pm = context.packageManager
        if (pm.getPackagesForUid(Binder.getCallingUid())?.contains(packageName) != true) return null
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
        return ShareApp(packageName, label, fingerprint, System.currentTimeMillis())
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

    private fun meta(context: Context): Cursor {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return MatrixCursor(arrayOf("contract_version", "db_version", "app_version", "app_version_code")).apply {
            addRow(arrayOf<Any?>(CONTRACT_VERSION, AppDatabase.VERSION, info.versionName, PackageInfoCompat.getLongVersionCode(info)))
        }
    }

    override fun getType(uri: Uri): String? = SharePath.of(uri)?.let { path ->
        val kind = if (path == SharePath.META) "item" else "dir"
        "vnd.android.cursor.$kind/vnd.${uri.authority}.${path.path}"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException(READ_ONLY)

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException(READ_ONLY)

    companion object {
        /**
         * Raise this whenever a shared table or column is renamed or removed (or its meaning
         * changes), so reading apps can tell; added tables and columns don't need it.
         */
        const val CONTRACT_VERSION = 1

        private const val READ_ONLY = "The shared data is read-only."
    }
}
