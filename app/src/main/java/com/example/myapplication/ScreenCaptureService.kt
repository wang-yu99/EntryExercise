package com.example.myapplication

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Base64
import com.example.myapplication.openai.OpenAIService
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {

    private val CHANNEL_ID = "ScreenCaptureServiceChannel"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture Service")
            .setContentText("Capturing screen once...")
            .setSmallIcon(R.drawable.ic_notification) // Ensure this drawable exists
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            setupMediaProjection(resultCode, resultData)
        } else {
            stopSelf() // Stop the service if permission wasn't granted
        }

        return START_NOT_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection != null) {
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture()
                }
            }, null)

            initializeImageReader()
            startVirtualDisplay()
        } else {
            Log.e("ScreenCaptureService", "Failed to initialize MediaProjection.")
        }
    }

    private fun initializeImageReader() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        //val imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, maxImages)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    processImage(image)
                } finally {
                    image.close()
                }
                stopCapture() // Stop capture after a single frame is processed
                stopSelf() // Stop the service
            }
        }, null)
    }

    private fun startVirtualDisplay() {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val density = metrics.densityDpi

        val newsfeedWidth = screenWidth // Full width of the screen
        val newsfeedHeight = screenHeight / 2 // Assume newsfeed takes half the screen

        // Create a VirtualDisplay capturing only the newsfeed region
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCaptureNewsfeed",
            newsfeedWidth, newsfeedHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        if (virtualDisplay == null) {
           // Log.e("ScreenCaptureService", "Failed to create VirtualDisplay for newsfeed.")
        } else {
          //  Log.d("ScreenCaptureService", "VirtualDisplay created for newsfeed capture.")
        }
    }

    private fun processImage(image: Image) {
        val planes = image.planes
        val buffer = planes[0].buffer
        val width = image.width
        val height = image.height
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        // Create a bitmap from the image buffer
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Optionally, crop the bitmap (e.g., top half of the image)
        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height / 2)

        // Resize the cropped bitmap more aggressively (e.g., to a quarter of original size)
        val resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, width / 4, height / 4, false)

        // Set the resized bitmap
        CaptureVmProvider.getInstance().setCapturedBitmap(resizedBitmap)

        // Encode the resized image to Base64
        val base64Image = encodeBitmapToBase64(resizedBitmap)

        // Send the image for analysis
        sendImageToLLM(base64Image)
        Log.d("ScreenCaptureService", "Captured and cropped image for analysis.")
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        // Try compressing the image at 45% quality to start
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 45, byteArrayOutputStream)
        var byteArray = byteArrayOutputStream.toByteArray()
        var base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

        // If the base64 string is still too large, try resizing further and reducing quality
        if (base64String.length > 5000) {
            // First, compress the image further at 30% quality
            byteArrayOutputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, byteArrayOutputStream)
            byteArray = byteArrayOutputStream.toByteArray()
            base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

            // If it still exceeds the threshold, try reducing quality to 20%
            if (base64String.length > 5000) {
                byteArrayOutputStream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 20, byteArrayOutputStream)
                byteArray = byteArrayOutputStream.toByteArray()
                base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            }

            if (base64String.length > 5000) {
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, false)
                return encodeBitmapToBase64(resizedBitmap)
            }
        }

       // Log.d("ImageBase64", "Base64 Image Length: ${base64String.length}")

        return base64String
    }

    private fun sendImageToLLM(base64Image: String) {
        OpenAIService.sendImageForAnalysis(base64Image) { result ->
            sendNotificationWithEvaluation(result)
        }
    }

    private fun sendNotificationWithEvaluation(result: String) {
        Log.d("ScreenCaptureService", "LLM Evaluation Result: $result")
        val notificationManager = getSystemService(NotificationManager::class.java)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Evaluation Result")
            .setContentText("The evaluation of the screen content is: $result")
            .setSmallIcon(R.drawable.ic_notification) // Replace with your icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(2, notification)
    }



    private fun stopCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }
}

