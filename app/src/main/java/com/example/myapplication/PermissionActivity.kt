package com.example.myapplication

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class PermissionRequestActivity : Activity() {

    private val REQUEST_CODE_MEDIA_PROJECTION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMediaProjectionPermission()
    }

    private fun requestMediaProjectionPermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // Permission granted, now wait 6 seconds before starting the screen capture
                Handler(Looper.getMainLooper()).postDelayed({
                    startScreenCaptureServiceWithPermission(resultCode, data)
                }, 11000)
            } else {
                // Handle the case where permission is denied
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startScreenCaptureServiceWithPermission(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("RESULT_CODE", resultCode)
            putExtra("RESULT_DATA", data)
        }

        // Start service with different methods based on API version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pendingIntent = PendingIntent.getForegroundService(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                pendingIntent.send()
            } catch (e: PendingIntent.CanceledException) {
                e.printStackTrace()
            }
        } else {
            startService(intent)
        }
    }
}


