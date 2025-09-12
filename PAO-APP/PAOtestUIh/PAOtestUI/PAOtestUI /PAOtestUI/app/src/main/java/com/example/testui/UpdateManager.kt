package com.example.testui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class UpdateManager(
    private val context: Context,
    private val onShowProgress: () -> Unit,
    private val onHideProgress: () -> Unit,
    private val onUpdateAvailable: (String, String, String) -> Unit
) {
    private val TAG = "UpdateManager"
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 30_000L // 30s
    private val updateJsonUrl =
        "https://raw.githubusercontent.com/PatrickAtef8/testOTA/main/update.json"

    fun checkForUpdatesPeriodically() {
        handler.post(object : Runnable {
            override fun run() {
                checkForUpdates()
                handler.postDelayed(this, checkInterval)
            }
        })
    }

    private fun getCurrentVersionName(): String {
        val pm = context.packageManager
        return try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            pi.versionName ?: "0"
        } catch (e: Exception) {
            Log.e(TAG, "Could not get version", e)
            "0"
        }
    }

    private fun checkForUpdates() {
        Thread {
            try {
                Log.i(TAG, "Checking $updateJsonUrl ...")
                val conn = URL(updateJsonUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val data = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(data)

                val latestVersion = json.getString("versionName")
                val apkUrl = json.getString("apkUrl")
                val checksum = json.getString("checksum")

                val currentVersion = getCurrentVersionName()
                Log.i(TAG, "Current=$currentVersion, Latest=$latestVersion")

                if (currentVersion != latestVersion) {
                    Handler(Looper.getMainLooper()).post {
                        onUpdateAvailable(latestVersion, apkUrl, checksum)
                    }
                } else {
                    Log.i(TAG, "Already up to date")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check updates", e)
            }
        }.start()
    }

    fun downloadAndInstall(apkUrl: String, expectedChecksum: String) {
        onShowProgress()
        Thread {
            try {
                Log.i(TAG, "Downloading: $apkUrl")
                val input: InputStream = URL(apkUrl).openStream()
                val file = File(context.getExternalFilesDir(null), "update.apk")
                file.outputStream().use { output -> input.copyTo(output) }

                val actual = sha256(file)
                Log.i(TAG, "Checksum expected=$expectedChecksum actual=$actual")
                if (!actual.equals(expectedChecksum, ignoreCase = true)) {
                    Log.e(TAG, "Checksum mismatch, aborting")
                    onHideProgress()
                    return@Thread
                }

                installWithPackageInstaller(file)
            } catch (e: Exception) {
                Log.e(TAG, "Update failed", e)
                onHideProgress()
            }
        }.start()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(8192)
            var r: Int
            while (input.read(buf).also { r = it } != -1) {
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun installWithPackageInstaller(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(
            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        file.inputStream().use { input ->
            session.openWrite("package", 0, -1).use { out ->
                input.copyTo(out)
                session.fsync(out)
            }
        }

        val cbIntent = Intent(context, UpdateReceiver::class.java)
        val sender = PendingIntent.getBroadcast(
            context,
            0,
            cbIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        ).intentSender

        session.commit(sender)
        session.close()
        Log.i(TAG, "✅ Install committed")
    }
}