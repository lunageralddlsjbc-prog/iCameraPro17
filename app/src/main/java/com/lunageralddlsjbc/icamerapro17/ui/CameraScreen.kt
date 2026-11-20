package com.lunageralddlsjbc.icamerapro17.ui

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class CaptureMode { PHOTO, VIDEO, PRO }

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var captureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    
    // Pro Mode State
    var exposureComp by remember { mutableStateOf(0) }
    
    var previewViewRef by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Initialize / Rebind Camera when mode or lens changes
    LaunchedEffect(captureMode, isFrontCamera, flashOn) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                
                val cameraSelector = if (isFrontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewViewRef?.surfaceProvider)
                }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                
                // FIXED: Use Recorder() directly instead of Recorder.builder().build()
                videoCapture = VideoCapture.Builder(Recorder()).build()
                
                camera = when (captureMode) {
                    CaptureMode.VIDEO -> cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, videoCapture
                    )
                    else -> cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageCapture
                    )
                }
                
                // Apply Pro settings
                if (captureMode == CaptureMode.PRO) {
                    camera?.cameraControl?.apply {
                        setExposureCompensationIndex(exposureComp)
                    }
                }
                
            } catch (e: Exception) {
                Log.e("Camera", "Bind failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                }.also { previewViewRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // UI Overlay
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeTab("PHOTO", captureMode == CaptureMode.PHOTO) { captureMode = CaptureMode.PHOTO }
                ModeTab("VIDEO", captureMode == CaptureMode.VIDEO) { captureMode = CaptureMode.VIDEO }
                ModeTab("PRO", captureMode == CaptureMode.PRO) { captureMode = CaptureMode.PRO }
            }
            
            // Bottom Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flash Toggle
                IconButton(onClick = { flashOn = !flashOn }) {
                    Text(if (flashOn) "⚡ON" else "⚡OFF", color = Color.White)
                }
                
                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(if (isRecording) Color.Red else Color.White, CircleShape)
                        .clickable {
                            if (captureMode == CaptureMode.VIDEO) {
                                if (!isRecording) {
                                    startRecording(context, videoCapture, cameraExecutor) { rec ->
                                        recording = rec
                                        isRecording = true
                                    }
                                } else {
                                    recording?.stop()
                                    recording = null
                                    isRecording = false
                                }
                            } else {
                                takePhoto(imageCapture, cameraExecutor, context)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(65.dp)
                            .background(Color.LightGray, CircleShape)
                    )
                }
                
                // Flip Camera
                IconButton(onClick = { isFrontCamera = !isFrontCamera }) {
                    Text("🔄", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
            
            // Pro Controls Panel
            if (captureMode == CaptureMode.PRO) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.8f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Exposure Compensation: $exposureComp", color = Color.White)
                        Slider(
                            value = exposureComp.toFloat(),
                            onValueChange = { exposureComp = it.toInt() },
                            valueRange = -2f..2f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else Color.Gray,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onClick() }
    )
}

private fun takePhoto(
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    context: Context
) {
    val capture = imageCapture ?: return
    val photoFile = File(
        context.getExternalFilesDir(null),
        "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
    )
    
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    capture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            Log.d("Camera", "Photo saved: ${photoFile.absolutePath}")
        }
        override fun onError(exc: ImageCaptureException) {
            Log.e("Camera", "Photo capture failed", exc)
        }
    })
}

private fun startRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    executor: ExecutorService,
    onStarted: (Recording) -> Unit
) {
    val capture = videoCapture ?: return
    
    // FIXED: Use FileOutputOptions for CameraX 1.4.1
    val videoFile = File(
        context.getExternalFilesDir(null),
        "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4"
    )
    
    val outputFileOptions = FileOutputOptions.Builder(videoFile).build()
    
    // FIXED: Use prepareRecording with FileOutputOptions and correct listener syntax
    val recording = capture.output.prepareRecording(context, outputFileOptions)
        .withAudioEnabled()
        .start(executor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> Log.d("Camera", "Recording started")
                is VideoRecordEvent.Finalize -> Log.d("Camera", "Recording finalized")
                is VideoRecordEvent.Status -> Log.d("Camera", "Recording status update")
            }
        }
    
    onStarted(recording)
}
