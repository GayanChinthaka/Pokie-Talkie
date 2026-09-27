package com.pokerlanka.pokie_talkie.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val downloadUrl: String?
)

object Updater {
    private const val TAG = "PokieUpdater"
    private const val GITHUB_REPO = "GayanChinthaka/Pokie-Talkie"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    /**
     * Checks GitHub API for the latest published release.
     */
    suspend fun getLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(API_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Pokie-Talkie-Android")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val tagName = if (json.isNull("tag_name")) "" else json.optString("tag_name", "")
                val name = if (json.isNull("name")) tagName else json.optString("name", tagName)
                val body = if (json.isNull("body")) "No release notes provided." else json.optString("body", "No release notes provided.")
                val publishedAt = formatDate(if (json.isNull("published_at")) "" else json.optString("published_at", ""))
                val htmlUrl = if (json.isNull("html_url")) "https://github.com/$GITHUB_REPO/releases" else json.optString("html_url", "https://github.com/$GITHUB_REPO/releases")

                var apkDownloadUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = if (asset.isNull("name")) "" else asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = if (asset.isNull("browser_download_url")) null else asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                Result.success(
                    ReleaseInfo(
                        tagName = tagName,
                        name = name,
                        body = body,
                        publishedAt = publishedAt,
                        htmlUrl = htmlUrl,
                        downloadUrl = apkDownloadUrl ?: htmlUrl
                    )
                )
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Result.failure(Exception("No releases found on GitHub repository."))
            } else {
                Result.failure(Exception("GitHub API returned error code $responseCode"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check GitHub releases: ${e.message}")
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Determines whether a newer release version is available compared to the installed version.
     */
    fun isUpdateAvailable(latestVersion: String, currentVersion: String): Boolean {
        return try {
            val cleanLatest = latestVersion.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

            val latestHasSuffix = cleanLatest.contains("-") || cleanLatest.contains("+")
            val currentHasSuffix = cleanCurrent.contains("-") || cleanCurrent.contains("+")

            val latestNumeric = cleanLatest.substringBefore("-").substringBefore("+")
            val currentNumeric = cleanCurrent.substringBefore("-").substringBefore("+")

            val latestParts = latestNumeric.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = currentNumeric.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            // When numeric parts are equal, treat the version without a pre-release suffix as newer
            if (!latestHasSuffix && currentHasSuffix) return true
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens the browser or download link for a release.
     */
    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open download URL: ${e.message}")
        }
    }

    private fun formatDate(rawDate: String): String {
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = isoFormat.parse(rawDate) ?: return rawDate
            val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            displayFormat.format(date)
        } catch (_: Exception) {
            rawDate.take(10)
        }
    }
}
