package com.you.photobackup

import android.app.*
import android.content.ContentUris
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class UploadService : Service() {

    private val pool = Executors.newSingleThreadExecutor()
    private val sentFile = "sent_ids.txt"
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var running = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notif("Starting…"))
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pb:upload")
        wakeLock?.acquire(60 * 60 * 1000L)
        pool.execute { uploadAll() }
        return START_STICKY
    }

    private fun uploadAll() {
        val sent = loadSent()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME)
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, null) ?: run { log("No gallery access"); return }

        var total = 0; var done = 0
        cursor.use { c -> total = c.count }
        log("Found $total photos in gallery.")

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            var index = 0
            while (c.moveToNext() && running) {
                index++
                val id = c.getLong(idCol)
                if (sent.contains(id.toString())) continue
                val name = c.getString(nameCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) { log("Skip unreadable: $name"); continue }
                val ok = post(bytes, name)
                if (ok) {
                    sent.add(id.toString())
                    saveSent(sent)
                    done++
                    if (done % 5 == 0 || done == 1)
                        log("Uploaded $done (scanned $index/$total)")
                    updateNotif("Uploaded $done photos…")
                } else {
                    log("FAILED: $name — will retry next run")
                }
            }
        }
        log("Run finished. Uploaded $done new photos this run.")
        updateNotif("Done this run: $done uploaded")
        stopSelf()
    }

    private fun post(bytes: ByteArray, filename: String): Boolean {
        return try {
            val boundary = "----b${System.currentTimeMillis()}"
            val conn = URL(MainActivity.SERVER).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty(
                "Content-Type", "multipart/form-data; boundary=$boundary")
            val out = DataOutputStream(conn.outputStream)
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"$filename\"\r\n\r\n")
            out.write(bytes)
            out.writeBytes("\r\n--$boundary--\r\n")
            out.flush()
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            log("Network error: ${e.message}")
            false
        }
    }

    private fun notif(text: String): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("bk", "Backup",
                NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, "bk")
            .setContentTitle("Photo backup")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }
    private fun updateNotif(t: String) =
        getSystemService(NotificationManager::class.java).notify(1, notif(t))

    private fun loadSent(): MutableSet<String> =
        File(filesDir, sentFile).takeIf { it.exists() }
            ?.readLines()?.toMutableSet() ?: mutableSetOf()
    private fun saveSent(s: Set<String>) =
        File(filesDir, sentFile).writeText(s.joinToString("\n"))

    private fun log(s: String) { logSink?.invoke(s); android.util.Log.d("PB", s) }

    override fun onDestroy() {
        running = false
        wakeLock?.release()
        super.onDestroy()
    }
    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        var logSink: ((String) -> Unit)? = null
    }
}
