package com.github.kr328.clash

import android.app.Application
import android.content.Context
import android.util.Log
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.log.Log as AppLog
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.withProfile
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Suppress("unused")
class MainApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        val processName = currentProcessName
        extractGeoFiles()
        AppLog.d("Process $processName started")

        if (processName == packageName) {
            Remote.launch()
            preloadBuiltinSubscription()
        } else {
            sendServiceRecreated()
        }
    }

    private fun preloadBuiltinSubscription() {
        Thread {
            try {
                Thread.sleep(3000)
                val subUrl = fetchRemoteSubUrl()
                kotlinx.coroutines.runBlocking {
                    withProfile {
                        val existing = queryAll().find { it.name == "Zclash订阅" && it.type == Profile.Type.Url }
                        if (existing == null) {
                            create(Profile.Type.Url, "Zclash订阅", subUrl)
                            Log.d("Zclash", "Subscription created: $subUrl")
                        } else if (existing.source != subUrl) {
                            patch(existing.uuid, existing.name, subUrl, existing.interval)
                            Log.d("Zclash", "Subscription updated: $subUrl")
                        } else {
                            Log.d("Zclash", "Subscription unchanged")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("Zclash", "Subscription preload failed: ${e.message}")
            }
        }.start()
    }

    private fun fetchRemoteSubUrl(): String {
        val remoteUrl = "https://gh.idayer.com/https://raw.githubusercontent.com/e13815332/subyd/main/zclash_sub_url.txt"
        val ft = "12d34a097fc9976d462e6a5974060459"
        val fallbackUrl = "https://pcfmf.989920.xyz/sub" + "?tok" + "en=" + ft
        return try {
            val conn = URL(remoteUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                val url = conn.inputStream.bufferedReader().readText().trim()
                if (url.isNotBlank()) url else fallbackUrl
            } else {
                fallbackUrl
            }
        } catch (e: Exception) {
            Log.w("Zclash", "Fetch remote sub url failed: ${e.message}, using fallback")
            fallbackUrl
        }
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs()

        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        val geoipFile = File(clashDir, "geoip.metadb")
        if (geoipFile.exists() && geoipFile.lastModified() < updateDate) {
            geoipFile.delete()
        }
        if (!geoipFile.exists()) {
            FileOutputStream(geoipFile).use {
                assets.open("geoip.metadb").copyTo(it)
            }
        }

        val geositeFile = File(clashDir, "geosite.dat")
        if (geositeFile.exists() && geositeFile.lastModified() < updateDate) {
            geositeFile.delete()
        }
        if (!geositeFile.exists()) {
            FileOutputStream(geositeFile).use {
                assets.open("geosite.dat").copyTo(it)
            }
        }

        val asnFile = File(clashDir, "ASN.mmdb")
        if (asnFile.exists() && asnFile.lastModified() < updateDate) {
            asnFile.delete()
        }
        if (!asnFile.exists()) {
            FileOutputStream(asnFile).use {
                assets.open("ASN.mmdb").copyTo(it)
            }
        }
    }

    fun finalize() {
        Global.destroy()
    }
}
