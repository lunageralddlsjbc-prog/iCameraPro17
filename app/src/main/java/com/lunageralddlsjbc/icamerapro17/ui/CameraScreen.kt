package com.lunageralddlsjbc.icamerapro17.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lunageralddlsjbc.icamerapro17.ai.ImageQualityScorer
import com.lunageralddlsjbc.icamerapro17.ai.PhotoScore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CameraAspectRatio(val label: String, val width: Int, val height: Int) {
    RATIO_4_3("4:3", 4, 3),
    RATIO_1_1("1:1", 1, 1),
    RATIO_16_9("16:9", 16, 9)
}

@Composable
private fun AspectRatioMenu(
    selected: CameraAspectRatio,
    onSelected: (CameraAspectRatio) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Text(
                text = "${selected.label}  ▾",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CameraAspectRatio.values().forEach { ratio ->
                DropdownMenuItem(
                    text = { Text(ratio.label, fontWeight = if (ratio == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        expanded = false
                        if (ratio != selected) onSelected(ratio)
                    }
                )
            }
        }
    }
}


@Composable
private fun ZoomRuler(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val range = (maxZoom - minZoom).coerceAtLeast(0.01f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .pointerInput(minZoom, maxZoom) {
                fun valueAt(x: Float): Float {
                    val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                    return minZoom + fraction * range
                }

                detectTapGestures { offset ->
                    onZoomChange(valueAt(offset.x))
                }
            }
            .pointerInput(minZoom, maxZoom) {
                fun valueAt(x: Float): Float {
                    val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                    return minZoom + fraction * range
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        onZoomChange(valueAt(offset.x))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onZoomChange(valueAt(change.position.x))
                    }
                )
            }
    ) {
        val centerY = size.height / 2f
        val tickCount =  fiftyTicks
        val spacing = size.width / (tickCount - 1)

        for (i in 0 until tickCount) {
            val x = i * spacing
            val major = i % 5 == 0
            val tickHeight = if (major) 17.dp.toPx() else 9.dp.toPx()

            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(x, centerY - tickHeight / 2f),
                end = Offset(x, centerY + tickHeight / 2f),
                strokeWidth = 2.dp.toPx()
            )
        }

        val fraction = ((zoom - minZoom) / range).coerceIn(0f, 1f)
        val markerX = fraction * size.width

        drawLine(
            color = Color(0xFF00D889),
            start = Offset(markerX, centerY - 25.dp.toPx()),
            end = Offset(markerX, centerY + 25.dp.toPx()),
            strokeWidth = 4.dp.toPx()
        )
    }
}

private const val fiftyTicks = 51

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var permissionsGranted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        permissionsGranted = perms.values.all { it }
    }
    
    LaunchedEffect(Unit) { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
    
    var isFrontCamera by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var lastMediaUri by remember { mutableStateOf<Uri?>(null) }
    var isShutterPressed by remember { mutableStateOf(false) }
    var showCaptureFlash by remember { mutableStateOf(false) }
    var photoScore by remember { mutableStateOf<PhotoScore?>(null) }
    var gridEnabled by remember { mutableStateOf(false) }
    var selectedAspectRatio by remember { mutableStateOf(CameraAspectRatio.RATIO_4_3) }
    var selectedZoomRatio by remember { mutableStateOf(1f) }

    var previewViewRef by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) } // CRITICAL: Save Camera instance
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    LaunchedEffect(isFrontCamera, permissionsGranted, selectedAspectRatio, previewViewRef) {
        if (!permissionsGranted) return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                
                val rotation = previewViewRef?.display?.rotation ?: android.view.Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setSurfaceProvider(previewViewRef?.surfaceProvider) }

                val newImageCapture = ImageCapture.Builder()
                    .setTargetRotation(rotation)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                val newVideoCapture = VideoCapture.Builder(recorder)
                    .setTargetRotation(rotation)
                    .build()

                // Keep the captured image crop synchronized with the selected ratio.
                val viewPort = ViewPort.Builder(
                    Rational(selectedAspectRatio.width, selectedAspectRatio.height),
                    rotation
                )
                    .setScaleType(ViewPort.FILL_CENTER)
                    .build()

                val useCaseGroup = UseCaseGroup.Builder()
                    .setViewPort(viewPort)
                    .addUseCase(preview)
                    .addUseCase(newImageCapture)
                    .addUseCase(newVideoCapture)
                    .build()

                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroup
                )
                camera = cam

                // Restore the selected zoom when the camera is rebound.
                val zoomState = cam.cameraInfo.zoomState.value
                if (zoomState != null) {
                    selectedZoomRatio = selectedZoomRatio.coerceIn(
                        zoomState.minZoomRatio,
                        zoomState.maxZoomRatio
                    )
                    cam.cameraControl.setZoomRatio(selectedZoomRatio)
                }

                imageCapture = newImageCapture
                videoCapture = newVideoCapture
            } catch (e: Exception) { 
                Log.e("Camera", "Bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    LaunchedEffect(flashOn) { imageCapture?.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }
    val shutterScale by animateFloatAsState(targetValue = if (isShutterPressed) 0.8f else 1f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f), label = "shutter")
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Match the visible preview frame to the phone's current orientation.
        val displayRotation = previewViewRef?.display?.rotation
            ?: android.view.Surface.ROTATION_0
        val isPortrait = displayRotation == android.view.Surface.ROTATION_0 ||
            displayRotation == android.view.Surface.ROTATION_180

        val previewAspectRatio = if (isPortrait) {
            selectedAspectRatio.height.toFloat() / selectedAspectRatio.width.toFloat()
        } else {
            selectedAspectRatio.width.toFloat() / selectedAspectRatio.height.toFloat()
        }
        val previewWidth = minOf(maxWidth, maxHeight * previewAspectRatio)
        val previewHeight = previewWidth / previewAspectRatio

        if (!permissionsGranted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Please grant Camera permissions", color = Color.White)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    androidx.camera.view.PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                        setOnTouchListener { v, event ->
                            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f)
                                // USE THE SAVED CAMERA INSTANCE FOR FOCUS
                                camera?.cameraControl?.startFocusAndMetering(
                                    FocusMeteringAction.Builder(factory.createPoint(event.x / v.width, event.y / v.height)).build()
                                )
                            }
                            true
                        }
                    }.also { previewViewRef = it }
                },
                modifier = Modifier
                    .width(previewWidth)
                    .height(previewHeight)
                    .align(Alignment.Center)
            )
        }

        if (gridEnabled) {
            Canvas(
                modifier = Modifier
                    .width(previewWidth)
                    .height(previewHeight)
                    .align(Alignment.Center)
            ) {
                val w = size.width; val h = size.height; val c = Color.White.copy(alpha = 0.5f)
                drawLine(c, Offset(w / 3f, 0f), Offset(w / 3f, h), 2f); drawLine(c, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), 2f)
                drawLine(c, Offset(0f, h / 3f), Offset(w, h / 3f), 2f); drawLine(c, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), 2f)
            }
        }
        // Center zoom readout and draggable ruler, matching the reference camera UI.
        val currentZoomState = camera?.cameraInfo?.zoomState?.value
        val minZoom = currentZoomState?.minZoomRatio ?: 1f
        val maxZoom = currentZoomState?.maxZoomRatio ?: 1f
        val safeMaxZoom = maxOf(maxZoom, minZoom + 0.01f)

        Box(
            modifier = Modifier
                .width(previewWidth)
                .height(previewHeight)
                .align(Alignment.Center)
        ) {
            Text(
                text = String.format(Locale.US, "%.1fx", selectedZoomRatio),
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.align(Alignment.Center)
            )

            ZoomRuler(
                zoom = selectedZoomRatio.coerceIn(minZoom, safeMaxZoom),
                minZoom = minZoom,
                maxZoom = safeMaxZoom,
                onZoomChange = { value ->
                    selectedZoomRatio = value
                    camera?.cameraControl?.setZoomRatio(value)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
            )
        }

        if (showCaptureFlash) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
        
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 50.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { gridEnabled = !gridEnabled }
                ) {
                    Text(
                        text = if (gridEnabled) "GRID ON" else "GRID OFF",
                        color = if (gridEnabled) Color(0xFFFFD700) else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AspectRatioMenu(selectedAspectRatio) { selectedAspectRatio = it }
                }
                IconButton(onClick = { flashOn = !flashOn }) {
                    Text(text = if (flashOn) "⚡" else "○", color = if (flashOn) Color(0xFFFFD700) else Color.White, fontSize = 24.sp)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().background(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))).padding(bottom = 30.dp)) {
                Card(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 90.dp).size(60.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        val gi = android.content.Intent(android.content.Intent.ACTION_MAIN).apply { addCategory(android.content.Intent.CATEGORY_APP_GALLERY); flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK }
                        if (gi.resolveActivity(context.packageManager) != null) context.startActivity(gi) else context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*"); flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK })
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = if (lastMediaUri != null) "🖼️" else "📷", fontSize = 24.sp)
                    }
                }
                
                Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(40.dp))
                    val shutterColor by animateColorAsState(targetValue = if (isRecording) Color.Red else Color.White, label = "shutter")
                    Box(
                        modifier = Modifier.size(76.dp).scale(shutterScale).border(4.dp, Color.White.copy(alpha = 0.55f), CircleShape).padding(4.dp).background(shutterColor, CircleShape).clickable { 
                            isShutterPressed = true
                            GlobalScope.launch(Dispatchers.Main) { delay(100); isShutterPressed = false }
                            if (!isRecording) {
                                showCaptureFlash = true
                                GlobalScope.launch(Dispatchers.Main) { delay(150); showCaptureFlash = false }
                                takePhoto(imageCapture, cameraExecutor, context) { uri -> 
                                    lastMediaUri = uri
                                    GlobalScope.launch(Dispatchers.IO) {
                                        try {
                                            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                                            if (bitmap != null) {
                                                val score = ImageQualityScorer.analyzeImage(bitmap)
                                                withContext(Dispatchers.Main) { photoScore = score }
                                            }
                                        } catch (e: Exception) { Log.e("AI", "Score failed: ${e.message}") }
                                    }
                                }
                            } else {
                                activeRecording?.stop()
                                activeRecording = null
                                isRecording = false
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) { if (isRecording) Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)) }
                    IconButton(onClick = { isFrontCamera = !isFrontCamera }) { Text("🔄", color = Color.White, fontSize = 24.sp) }
                }
            }
        }
    }
    
    if (photoScore != null) {
        ModalBottomSheet(onDismissRequest = { photoScore = null }, containerColor = Color(0xFF1C1C1E)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text("AI PHOTO ANALYSIS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("${photoScore?.overall}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("OVERALL SCORE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(24.dp))
                listOf("Sharpness" to photoScore?.sharpness, "Face Clarity" to photoScore?.faceClarity, "Lighting" to photoScore?.lighting).forEach { (name, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name ?: "", color = Color.White, fontSize = 14.sp)
                        Text("${value ?: 0}/100", color = if ((value ?: 0) >= 80) Color(0xFF34C759) else Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))) {
                        Box(modifier = Modifier.fillMaxWidth((value ?: 0) / 100f).height(4.dp).background(if ((value ?: 0) >= 80) Color(0xFF34C759) else Color(0xFFFFD700), RoundedCornerShape(2.dp)))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun takePhoto(imageCapture: ImageCapture?, executor: ExecutorService, context: Context, onSaved: (Uri) -> Unit) {
    if (imageCapture == null) return
    val name = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/iCamera Pro 17")
        }
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build()
    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            output.savedUri?.let { onSaved(it) }
        }
        override fun onError(exc: ImageCaptureException) { Log.e("Camera", "Capture failed: ${exc.message}") }
    })
}
