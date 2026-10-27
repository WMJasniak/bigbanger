package com.wiktor.bigbanger.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val notes: String,
)

/**
 * Checks GitHub Releases for a newer build and installs it.
 *
 * Release tags are expected as "vN" where N is the integer versionCode
 * (monotonic, unambiguous — unlike comparing "0.1.0" style strings). The APK
 * is published as a release asset. The GitHub REST API is public and needs no
 * auth token for a public repo, so nothing is embedded in the app.
 */
class Updater(
    private val context: Context,
    private val owner: String = "WMJasniak",
    private val repo: String = "bigbanger",
) {

    /** Null if no release found, unparsable, or the request failed. */
    suspend fun checkLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val versionCode = tag.removePrefix("v").toIntOrNull() ?: return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
            apkUrl ?: return@withContext null
            ReleaseInfo(
                versionCode = versionCode,
                versionName = json.optString("name", tag),
                downloadUrl = apkUrl,
                notes = json.optString("body", ""),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads the APK to app cache, reporting 0f..1f progress, then returns
     * the local file. Throws on failure (caller shows an error state).
     */
    suspend fun download(release: ReleaseInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "bigbanger-${release.versionCode}.apk")
            val conn = URL(release.downloadUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            val total = conn.contentLength.coerceAtLeast(1)
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var readSoFar = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        readSoFar += n
                        onProgress((readSoFar.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            out
        }

    /** Launches the system package installer for the downloaded APK. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/** Persists update-check cadence and a user-skipped version, across app restarts. */
class UpdateSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("update_settings", Context.MODE_PRIVATE)

    var lastCheckMs: Long
        get() = prefs.getLong("last_check_ms", 0L)
        set(value) = prefs.edit().putLong("last_check_ms", value).apply()

    var skippedVersionCode: Int
        get() = prefs.getInt("skipped_version_code", -1)
        set(value) = prefs.edit().putInt("skipped_version_code", value).apply()
}
