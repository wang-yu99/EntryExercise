package com.example.myapplication

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UserScreen(
    onRequestScreenCapturePermission: () -> Unit,
    evaluationResult: String?,
    capturedBitmap: Bitmap?
) {
    val context = LocalContext.current
    var targetAppOpened by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {

        if (!isUsageStatsPermissionGranted(context)) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            // Start a coroutine to handle the app's background check and permission process
            launch(Dispatchers.IO) {
                while (true) {
                    // Check if Instagram is in the foreground
                    targetAppOpened = checkIfTargetAppOpened(context, "com.instagram.android")
                    delay(5000)
                    if (targetAppOpened && !permissionRequested) {
                        // If Instagram is in the foreground, request screen capture permission
                        onRequestScreenCapturePermission()
                        permissionRequested = true

                        // Wait for permission grant
                        delay(5000) // Allow time for Toast to show


                        break // Exit the loop after handling permission and screenshot capture
                    }

                    delay(2000) // Check every 2 seconds if Instagram is still open
                }
            }
        }
    }

    Column {
        if (targetAppOpened) {
            Text("Instagram is open in the foreground.")
        } else {
            Text("Instagram is not open.")
        }

        capturedBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured Screenshot",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp)
            )
        }

        evaluationResult?.let {
            Text("Evaluation Result: $it")
        }
    }
}


fun isUsageStatsPermissionGranted(context: Context): Boolean {
    val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOpsManager.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun checkIfTargetAppOpened(context: Context, targetPackageName: String): Boolean {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    val startTime = endTime - 10000  // Increased to check for a larger window (10 seconds)

    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
    var targetAppOpened = false

    while (usageEvents.hasNextEvent()) {
        val event = UsageEvents.Event()
        usageEvents.getNextEvent(event)

        // Track when the app is moved to foreground
        if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
            event.packageName == targetPackageName) {
            targetAppOpened = true
        }

        // Track when the app is moved to background
        if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND &&
            event.packageName == targetPackageName) {
            targetAppOpened = false
        }
    }

    return targetAppOpened
}
