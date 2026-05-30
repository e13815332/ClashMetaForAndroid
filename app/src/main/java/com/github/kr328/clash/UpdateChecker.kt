package com.github.kr328.clash

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_API = "https://api.github.com/repos/e13815332/subyd/releases/tags/zclash"
    private val VERSION_REGEX = Regex("""Zclash-(\d+\.\d+\.\d+)-""")

    data class UpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String,
    )

    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val releaseJson = fetchRelease()
            val latestVersion = extractVersion(releaseJson)
            val downloadUrl = extractDownloadUrl(releaseJson)

            if (latestVersion != null && isNewer(latestVersion, currentVersion)) {
                UpdateInfo(currentVersion, latestVersion, downloadUrl ?: "")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            null
        }
    }

    private fun fetchRelease(): String {
        val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Zclash-UpdateChecker")

        return if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            throw Exception("HTTP ${conn.responseCode}")
        }
    }

    private fun extractVersion(json: String): String? {
        val assets = JSONObject(json).optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val name = assets.getJSONObject(i).optString("name", "")
            val match = VERSION_REGEX.find(name)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun extractDownloadUrl(json: String): String? {
        val assets = JSONObject(json).optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.contains("universal") || name.contains("arm64-v8a")) {
                return asset.optString("browser_download_url")
            }
        }
        return assets.getJSONObject(0).optString("browser_download_url")
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
