package com.kahga.kpulse.field

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Saving a downloaded file to the phone, and handing it to a viewer.
 *
 * <p>The voter list is fetched by the page with the agent's token, so it never
 * exists as a URL the system download manager could fetch — it is a `blob:` in
 * the WebView, and a WebView's DownloadListener is never called for those. The
 * page therefore hands the bytes over this bridge instead, and the app writes
 * them where the agent expects to find them.
 *
 * <p>The file is written twice, for two different reasons. The copy in the
 * phone's Downloads folder is the one the agent keeps. The copy in this app's
 * cache is the one "Open" hands to a PDF viewer: under scoped storage a
 * MediaStore row belongs to the app that wrote it, and another app given that
 * URI reads nothing — FLAG_GRANT_READ_URI_PERMISSION only grants URIs served by
 * a provider of our own. So a save always returns a FileProvider URI.
 *
 * <p>Nothing here decides whether a download is allowed; the server has already
 * done that. This only writes the bytes it is given.
 */
class DownloadBridge(private val activity: Activity) {

    companion object {
        /** The name the page calls this by. */
        const val NAME = "KPulseDownloads"

        private const val TAG = "KPulseDownloads"

        /** Writing to the public Downloads folder needs asking, before Android 10. */
        const val LEGACY_STORAGE_REQUEST = 4201

        /** Where the openable copies live, under the app's own cache. */
        private const val SHARED_DIR = "shared"

        /** An openable copy is only needed while the agent is still with the file. */
        private val COPY_LIFETIME_MS = TimeUnit.DAYS.toMillis(1)
    }

    /**
     * Writes the file to the phone's Downloads folder.
     *
     * @param base64 the file's bytes
     * @return a URI the file can be reopened with, or "" if it could not be saved
     */
    @JavascriptInterface
    fun save(base64: String, fileName: String, mimeType: String): String {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes.isEmpty()) {
                Log.w(TAG, "nothing to save for $fileName")
                return ""
            }
            val name = safeName(fileName)
            val kept =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(bytes, name, mimeType)
                } else {
                    saveToPublicDownloads(bytes, name)
                }
            if (!kept) return ""
            openableCopy(bytes, name)?.toString().orEmpty()
        } catch (error: Exception) {
            Log.w(TAG, "could not save $fileName", error)
            ""
        }
    }

    /** Opens a file saved earlier, in whatever app the phone uses for its type. */
    @JavascriptInterface
    fun open(uri: String) {
        if (uri.isBlank()) return
        activity.runOnUiThread {
            val target = Uri.parse(uri)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                // The provider answers from the file's own extension, so another
                // kind of file added later needs no change here.
                setDataAndType(target, activity.contentResolver.getType(target) ?: "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                activity.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                toast(R.string.download_no_viewer)
            }
        }
    }

    /**
     * Android 10 and later: the Downloads collection takes the file without any
     * permission. The row belongs to this app, which is why it is not what
     * "Open" is given — see the note on the class.
     */
    private fun saveToMediaStore(bytes: ByteArray, fileName: String, mimeType: String): Boolean {
        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        val written = resolver.openOutputStream(uri)?.use { it.write(bytes); true } ?: false
        if (!written) {
            // A row left pending would sit in Downloads as an unreadable stub.
            resolver.delete(uri, null, null)
            return false
        }

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    /**
     * Before Android 10 the public folder is an ordinary directory and writing
     * to it needs a granted permission. If it has not been granted the request
     * is put up and this save fails: the agent grants it and taps download
     * again, rather than the file landing somewhere they cannot find.
     */
    private fun saveToPublicDownloads(bytes: ByteArray, fileName: String): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            activity.runOnUiThread {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    LEGACY_STORAGE_REQUEST,
                )
            }
            return false
        }

        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!folder.exists() && !folder.mkdirs()) {
            return false
        }
        File(folder, fileName).writeBytes(bytes)
        return true
    }

    /**
     * The copy a viewer can be handed. Yesterday's copies go with it: a roll is
     * megabytes, and once the agent has moved on nothing reads them again. The
     * file just saved is never one of those, even if its name repeats.
     */
    private fun openableCopy(bytes: ByteArray, fileName: String): Uri? {
        val folder = File(activity.cacheDir, SHARED_DIR)
        if (!folder.exists() && !folder.mkdirs()) {
            return null
        }
        val cutoff = System.currentTimeMillis() - COPY_LIFETIME_MS
        folder.listFiles()?.forEach { stale ->
            if (stale.name != fileName && stale.lastModified() < cutoff) {
                stale.delete()
            }
        }
        val file = File(folder, fileName)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
    }

    /** A name from the server is still a name from outside: keep it to one path segment. */
    private fun safeName(fileName: String): String {
        val cleaned = fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "voter-list.pdf" }
    }

    private fun toast(messageId: Int) {
        Toast.makeText(activity, messageId, Toast.LENGTH_LONG).show()
    }
}
