package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val publishedAt: String = ""
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    object NoInternet : UpdateCheckResult()
    object RateLimited : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

class UpdateManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AUTO_CHECK = "key_auto_check_on_start"
        private const val KEY_LAST_CHECK = "key_last_update_check_time"
        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
            val cleanLatest = latestTag.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

            val latestParts = cleanLatest.split("-")[0].split(".").map { part ->
                part.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
            val currentParts = cleanCurrent.split("-")[0].split(".").map { part ->
                part.filter { it.isDigit() }.toIntOrNull() ?: 0
            }

            val maxLength = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }
    }

    var isAutoCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()

    var lastCheckTimestamp: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    fun shouldAutoCheckToday(): Boolean {
        if (!isAutoCheckEnabled) return false
        val now = System.currentTimeMillis()
        return (now - lastCheckTimestamp) >= ONE_DAY_MS
    }

    suspend fun checkForUpdates(
        currentVersion: String,
        owner: String = "OWNER",
        repo: String = "REPO"
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "MyMoney-Android-App")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (code == 403 || code == 429) {
                    return@withContext UpdateCheckResult.RateLimited
                }

                if (!response.isSuccessful) {
                    if (body.contains("rate limit", ignoreCase = true)) {
                        return@withContext UpdateCheckResult.RateLimited
                    }
                    return@withContext UpdateCheckResult.Error("HTTP $code")
                }

                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "")
                val releaseName = json.optString("name", tagName)
                val releaseNotes = json.optString("body", "")
                val htmlUrl = json.optString("html_url", "")
                val publishedAt = json.optString("published_at", "")

                // Look for an APK asset first
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                val downloadUrl = if (!apkUrl.isNullOrBlank()) apkUrl else htmlUrl

                // Mark successful check timestamp
                lastCheckTimestamp = System.currentTimeMillis()

                if (isNewerVersion(tagName, currentVersion)) {
                    UpdateCheckResult.UpdateAvailable(
                        AppReleaseInfo(
                            tagName = tagName,
                            releaseName = releaseName,
                            releaseNotes = releaseNotes,
                            downloadUrl = downloadUrl,
                            publishedAt = publishedAt
                        )
                    )
                } else {
                    UpdateCheckResult.UpToDate
                }
            }
        } catch (e: java.net.UnknownHostException) {
            UpdateCheckResult.NoInternet
        } catch (e: java.net.SocketTimeoutException) {
            UpdateCheckResult.NoInternet
        } catch (e: IOException) {
            UpdateCheckResult.NoInternet
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Failed to check for updates")
        }
    }
}
