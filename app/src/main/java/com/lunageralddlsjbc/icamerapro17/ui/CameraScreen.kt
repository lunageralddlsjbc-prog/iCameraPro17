package com.lunageralddlsjbc.icamerapro17.ui

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class CaptureMode { PHOTO, VIDEO, PRO }

@OptIn(ExperimentalMaterial3Api::class)
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
    
    // FIXED: Correct generic type for CameraX 1.4.1
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Initialize / Rebind Camera
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
                
                // FIXED: Create Recorder instance properly for CameraX 1.4.1
                val recorder = Recorder.Builder().build()
                videoCapture = VideoCapture.Builder(recorder).build()
                
                camera = when (captureMode) {
                    CaptureMode.VIDEO -> cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, videoCapture
                    )
                    else -> cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageCapture
                    )
                }
                
                if (captureMode == CaptureMode.PRO) {
                    camera?.cameraControl?.setExposureCompensationIndex(exposureComp)
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
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                }.also { previewViewRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // PREMIUM UI OVERLAY
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Glassmorphism Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .blur(10.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PremiumModeTab("PHOTO", captureMode == CaptureMode.PHOTO) { captureMode = CaptureMode.PHOTO }
                    PremiumModeTab("VIDEO", captureMode == CaptureMode.VIDEO) { captureMode = CaptureMode.VIDEO }
                    PremiumModeTab("PRO", captureMode == CaptureMode.PRO) { captureMode = CaptureMode.PRO }
                }
            }
            
            // Bottom Controls Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Pro Panel (Floating Glass Card)
                if (captureMode == CaptureMode.PRO) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .offset(y = (-80).dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1C1C1E).copy(alpha = 0.85f)
                        ),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("EXPOSURE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = exposureComp.toFloat(),
                                onValueChange = { exposureComp = it.toInt() },
                                valueRange = -2f..2f,
                                steps = 3,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFFFFD700), // Gold accent
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                        }
                    }
                }

                // Main Control Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash Button (Minimalist Icon)
                    IconButton(onClick = { flashOn = !flashOn }) {
                        Text(
                            text = if (flashOn) "⚡" else "○",
                            color = if (flashOn) Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
                            fontSize = 24.sp
                        )
                    }
                    
                    // PREMIUM SHUTTER BUTTON
                    val shutterColor by animateColorAsState(
                        targetValue = if (isRecording) Color.Red else Color.White,
                        label = "shutter"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .border(4.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                            .padding(6.dp)
                            .background(shutterColor, CircleShape)
                            .clickable {
                                if (captureMode == CaptureMode.VIDEO) {
                                    if (!isRecording) {
                                        startRecording(context, videoCapture, cameraExecutor) { rec ->
                                            activeRecording = rec
                                            isRecording = true
                                        }
                                    } else {
                                        activeRecording?.stop()
                                        activeRecording = null
                                        isRecording = false
                                    }
                                } else {
                                    takePhoto(imageCapture, cameraExecutor, context)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Inner ring animation for recording
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                    
                    // Flip Camera Button
                    IconButton(onClick = { isFrontCamera = !isFrontCamera }) {
                        Text("🔄", color = Color.White.copy(alpha = 0.8f), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val textColor = if (selected) Color.White else Color.Gray
    val underlineColor = if (selected) Color(0xFFFFD700) else Color.Transparent
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(2.dp)
                .background(underlineColor)
        )
    }
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
    
    val videoFile = File(
        context.getExternalFilesDir(null),
        "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4"
    )
    
    // FIXED: Use FileOutputOptions for CameraX 1.4.1
    val outputFileOptions = FileOutputOptions.Builder(videoFile).build()
    
    // FIXED: Correct API chain for 1.4.1
    val pendingRecording = capture.output.prepareRecording(context, outputFileOptions)
        .withAudioEnabled()
    
    val recording = pendingRecording.start(executor) { event ->
        when (event) {
            is VideoRecordEvent.Start -> Log.d("Camera", "Recording started")
            is VideoRecordEvent.Finalize -> Log.d("Camera", "Recording finalized")
            is VideoRecordEvent.Status -> Log.d("Camera", "Status update")
        }
    }
    
    onStarted(recording)
}
