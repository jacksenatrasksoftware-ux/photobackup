package com.you.photobackup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // >>>>>>>>> PUT YOUR CLOUDFLARE URL HERE <<<<<<<<<
    companion object {
        const val SERVER = "https://jane-incorporated-liverpool-jews.trycloudflare.com/upload"
    }

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)

        UploadService.logSink = { line -> runOnUiThread { status.append("\n$line") } }

        findViewById<Button>(R.id.allowBtn).setOnClickListener {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= 33)
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            else perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33)
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
            log("Requested permissions…")
        }

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            val perm = if (Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, perm)
                != PackageManager.PERMISSION_GRANTED) {
                log("Tap 'Allow access to photos' first."); return@setOnClickListener
            }
            startForegroundService(Intent(this, UploadService::class.java))
            log("Backup ON. You can close this screen.")
        }

        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, UploadService::class.java))
            log("Backup stopped.")
        }
    }

    private fun log(s: String) { status.append("\n$s") }
}
