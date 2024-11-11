package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val captureViewModel = CaptureVmProvider.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                val bitmap by captureViewModel.capturedBitmap.observeAsState()
                val evaluationResult by captureViewModel.evaluationResult.observeAsState() // Observe the result
                UserScreen(
                    onRequestScreenCapturePermission = {
                        // Start the PermissionRequestActivity
                        val intent = Intent(this, PermissionRequestActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Keep the activity stack separate
                        startActivity(intent)
                    },

                    capturedBitmap = bitmap,
                    evaluationResult = evaluationResult
                )
            }
        }
    }
}

