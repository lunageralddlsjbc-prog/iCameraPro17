package com.lunageralddlsjbc.icamerapro17.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
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
import com.lunageralddlsjbc.icamerapro17.ai.AutoPhotoEnhancer
import com.lunageralddlsjbc.icamerapro17.ai.PhotoScore
import com.lunageralddlsjbc.icamerapro17.camera.CameraHardwareInspector
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class CameraAspectRatio(
    val label: String,
    val outputWidth: Int,
    val outputHeight: Int
) {
    RATIO_4_3("4:3", 1200, 900),
    RATIO_1_1("1:1", 1080, 1080),
    RATIO_16_9("16:9", 1600, 900),
    RATIO_20_9("20:9", 720, 1600)
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
            .fillMaxWidth(0.72f)
            .height(26.dp)
            .pointerInput(minZoom, maxZoom) {
                detectTapGestures { point ->
                    val fraction = (point.x / size.width).coerceIn(0f, 1f)
                    onZoomChange(minZoom + fraction * range)
                }
            }
            .pointerInput(minZoom, maxZoom) {
                detectDragGestures(
                    onDragStart = { point ->
                        val fraction = (point.x / size.width).coerceIn(0f, 1f)
                        onZoomChange(minZoom + fraction * range)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onZoomChange(minZoom + fraction * range)
                    }
                )
            }
    ) {
        val centerY = size.height / 2f
        val tickCount = 41
        val spacing = size.width / (tickCount - 1)

        for (i in 0 until tickCount) {
            val x = i * spacing
            val tickHeight = if (i % 5 == 0) 9.dp.toPx() else 4.dp.toPx()

            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(x, centerY - tickHeight / 2f),
                end = Offset(x, centerY + tickHeight / 2f),
                strokeWidth = 1.dp.toPx()
            )
        }

        val fraction = ((zoom - minZoom) / range).coerceIn(0f, 1f)
        val markerX = fraction * size.width

        drawLine(
            color = Color(0xFF00D889),
            start = Offset(markerX, centerY - 12.dp.toPx()),
            end = Offset(markerX, centerY + 12.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
    }
}

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
    var hardwareReport by remember { mutableStateOf<String?>(null) }

    val cameraSettings = remember {
        context.getSharedPreferences("icamera_settings", Context.MODE_PRIVATE)
    }
    var autoEnhanceEnabled by remember {
        mutableStateOf(cameraSettings.getBoolean("auto_enhance", true))
    }
    var keepOriginalBackup by remember {
        mutableStateOf(cameraSettings.getBoolean("keep_original_backup", false))
    }

    var extendedExposureEnabled by remember {
        mutableStateOf(cameraSettings.getBoolean("extended_exposure", false))
    }

    var cinematicModeEnabled by remember {
        mutableStateOf(cameraSettings.getBoolean("cinematic_mode", false))
    }

    var portraitModeEnabled by remember {
        mutableStateOf(cameraSettings.getBoolean("portrait_mode", false))
    }

    var selectedAspectRatio by remember { mutableStateOf(CameraAspectRatio.RATIO_4_3) }
    var selectedZoomRatio by remember { mutableStateOf(1f) }
    var showZoomRuler by remember { mutableStateOf(false) }
    var zoomRulerActivity by remember { mutableStateOf(0) }

    LaunchedEffect(showZoomRuler, zoomRulerActivity) {
        if (showZoomRuler) {
            delay(2500)
            showZoomRuler = false
        }
    }

    var previewViewRef by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) } // CRITICAL: Save Camera instance
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    LaunchedEffect(isFrontCamera, permissionsGranted, selectedAspectRatio, previewViewRef, cinematicModeEnabled) {
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

                // Prefer true UHD 2160p for Cinematic Mode.
                // CameraX selects a lower supported quality if UHD is unavailable.
                val videoQualitySelector = if (cinematicModeEnabled) {
                    QualitySelector.fromOrderedList(
                        listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                } else {
                    QualitySelector.from(Quality.HIGHEST)
                }

                val recorder = Recorder.Builder()
                    .setQualitySelector(videoQualitySelector)
                    .build()
                val newVideoCapture = VideoCapture.Builder(recorder)
                    .setTargetRotation(rotation)
                    .build()

                // Keep the captured image crop synchronized with the selected ratio.
                val viewPort = ViewPort.Builder(
                    Rational(
                        selectedAspectRatio.outputWidth,
                        selectedAspectRatio.outputHeight
                    ),
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

    // ISO 50-style extended exposure: approximately -1 EV using the
    // camera's advertised compensation step and supported index range.
    // This does not change the sensor's native ISO floor.
    LaunchedEffect(camera, extendedExposureEnabled) {
        val activeCamera = camera ?: return@LaunchedEffect
        try {
            val exposureState = activeCamera.cameraInfo.exposureState
            val range = exposureState.exposureCompensationRange
            val step = exposureState.exposureCompensationStep.toDouble()

            val requestedIndex = if (extendedExposureEnabled && step > 0.0) {
                (-1.0 / step).roundToInt().coerceIn(range.lower, range.upper)
            } else {
                0.coerceIn(range.lower, range.upper)
            }

            activeCamera.cameraControl.setExposureCompensationIndex(requestedIndex)
                .addListener(
                    {
                        Log.d("Camera", "Extended exposure index applied: $requestedIndex")
                    },
                    ContextCompat.getMainExecutor(context)
                )
        } catch (e: Exception) {
            Log.e("Camera", "Could not apply extended exposure mode", e)
        }
    }
    val shutterScale by animateFloatAsState(targetValue = if (isShutterPressed) 0.8f else 1f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f), label = "shutter")
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Match the visible preview frame to the phone's current orientation.
        val displayRotation = previewViewRef?.display?.rotation
            ?: android.view.Surface.ROTATION_0
        val isPortrait = displayRotation == android.view.Surface.ROTATION_0 ||
            displayRotation == android.view.Surface.ROTATION_180

        val outputRatio = selectedAspectRatio.outputWidth.toFloat() /
            selectedAspectRatio.outputHeight.toFloat()

        val previewAspectRatio = if (isPortrait) {
            outputRatio
        } else {
            1f / outputRatio
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
        // The zoom value stays visible. Tap it to reveal or hide the small ruler.
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
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable {
                        showZoomRuler = !showZoomRuler
                        zoomRulerActivity++
                    }
                    .padding(8.dp)
            )

            if (showZoomRuler) {
                ZoomRuler(
                    zoom = selectedZoomRatio.coerceIn(minZoom, safeMaxZoom),
                    minZoom = minZoom,
                    maxZoom = safeMaxZoom,
                    onZoomChange = { value ->
                        selectedZoomRatio = value
                        camera?.cameraControl?.setZoomRatio(value)
                        zoomRulerActivity++
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }
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
                    modifier = Modifier.clickable {
                        GlobalScope.launch(Dispatchers.IO) {
                            val report = try {
                                CameraHardwareInspector.createReport(context)
                            } catch (e: Exception) {
                                "Camera inspection failed: ${e.message}"
                            }
                            withContext(Dispatchers.Main) { hardwareReport = report }
                        }
                    }
                ) {
                    Text(
                        text = "DEVICE",
                        color = Color(0xFF00D889),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
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

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (cinematicModeEnabled) Color(0xFF174D3A) else Color.Black.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.clickable(enabled = !isRecording) {
                        cinematicModeEnabled = !cinematicModeEnabled
                        cameraSettings.edit()
                            .putBoolean("cinematic_mode", cinematicModeEnabled)
                            .apply()
                    }
                ) {
                    Text(
                        text = if (cinematicModeEnabled) "CINEMA UHD*" else "CINEMA",
                        color = if (cinematicModeEnabled) Color(0xFF00D889) else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                    )
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (portraitModeEnabled) Color(0xFF174D3A) else Color.Black.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.clickable {
                        portraitModeEnabled = !portraitModeEnabled
                        cameraSettings.edit().putBoolean("portrait_mode", portraitModeEnabled).apply()
                    }
                ) {
                    Text(
                        text = if (portraitModeEnabled) "PORTRAIT ON" else "PORTRAIT",
                        color = if (portraitModeEnabled) Color(0xFF00D889) else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                    )
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (extendedExposureEnabled) Color(0xFF174D3A) else Color.Black.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.clickable {
                        extendedExposureEnabled = !extendedExposureEnabled
                        cameraSettings.edit()
                            .putBoolean("extended_exposure", extendedExposureEnabled)
                            .apply()
                    }
                ) {
                    Text(
                        text = if (extendedExposureEnabled) "EXT -1EV ON" else "EXT OFF",
                        color = if (extendedExposureEnabled) Color(0xFF00D889) else Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AspectRatioMenu(selectedAspectRatio) { selectedAspectRatio = it }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (autoEnhanceEnabled) "AUTO AI ON" else "AUTO AI OFF",
                        color = if (autoEnhanceEnabled) Color(0xFF34C759) else Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            autoEnhanceEnabled = !autoEnhanceEnabled
                            cameraSettings.edit().putBoolean("auto_enhance", autoEnhanceEnabled).apply()
                        }.padding(3.dp)
                    )
                    Text(
                        text = if (keepOriginalBackup) "BACKUP ON" else "BACKUP OFF",
                        color = if (keepOriginalBackup) Color(0xFFFFD700) else Color.LightGray,
                        fontSize = 9.sp,
                        modifier = Modifier.clickable {
                            keepOriginalBackup = !keepOriginalBackup
                            cameraSettings.edit().putBoolean("keep_original_backup", keepOriginalBackup).apply()
                        }.padding(3.dp)
                    )
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
                            if (cinematicModeEnabled) {
                                if (!isRecording) {
                                    val capture = videoCapture
                                    if (capture == null) {
                                        Log.e("Camera", "Video capture is not ready")
                                    } else {
                                        try {
                                            val values = ContentValues().apply {
                                                put(
                                                    MediaStore.Video.Media.DISPLAY_NAME,
                                                    "CINEMA_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4"
                                                )
                                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                    put(
                                                        MediaStore.Video.Media.RELATIVE_PATH,
                                                        Environment.DIRECTORY_MOVIES + "/iCamera Pro 17"
                                                    )
                                                }
                                            }
                                            val outputOptions = MediaStoreOutputOptions.Builder(
                                                context.contentResolver,
                                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                            ).setContentValues(values).build()

                                            var pending = capture.output.prepareRecording(context, outputOptions)
                                            if (ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            ) {
                                                pending = pending.withAudioEnabled()
                                            }

                                            val mainExecutor = ContextCompat.getMainExecutor(context)
                                            val recording = pending.start(cameraExecutor) { event ->
                                                when (event) {
                                                    is VideoRecordEvent.Start -> mainExecutor.execute {
                                                        isRecording = true
                                                    }
                                                    is VideoRecordEvent.Finalize -> mainExecutor.execute {
                                                        isRecording = false
                                                        activeRecording = null
                                                        if (event.hasError()) {
                                                            Log.e("Camera", "Video recording failed: ${event.error}")
                                                        } else {
                                                            val savedUri = event.outputResults.outputUri
                                                            if (savedUri != Uri.EMPTY) lastMediaUri = savedUri
                                                            Log.i("Camera", "Cinematic video saved: $savedUri")
                                                        }
                                                    }
                                                }
                                            }
                                            activeRecording = recording
                                            isRecording = true
                                        } catch (e: Exception) {
                                            Log.e("Camera", "Could not start cinematic recording", e)
                                            isRecording = false
                                            activeRecording = null
                                        }
                                    }
                                } else {
                                    activeRecording?.stop()
                                }
                            } else if (!isRecording) {
                                showCaptureFlash = true
                                GlobalScope.launch(Dispatchers.Main) { delay(150); showCaptureFlash = false }
                                takePhoto(imageCapture, cameraExecutor, context, selectedAspectRatio, autoEnhanceEnabled, keepOriginalBackup, portraitModeEnabled) { uri ->
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
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) { if (isRecording) Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)) }
                    IconButton(onClick = { isFrontCamera = !isFrontCamera }) { Text("🔄", color = Color.White, fontSize = 24.sp) }
                }
            }
        }
    }
    
    if (hardwareReport != null) {
        AlertDialog(
            onDismissRequest = { hardwareReport = null },
            title = { Text("DEVICE CAMERA CAPABILITIES") },
            text = {
                Text(
                    hardwareReport ?: "",
                    fontSize = 11.sp,
                    modifier = Modifier.heightIn(max = 420.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { hardwareReport = null }) {
                    Text("CLOSE")
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
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

private fun takePhoto(
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    context: Context,
    aspectRatio: CameraAspectRatio,
    autoEnhance: Boolean,
    keepOriginalBackup: Boolean,
    portraitMode: Boolean,
    onSaved: (Uri) -> Unit
) {
    if (imageCapture == null) return

    val name = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/iCamera Pro 17")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            output.savedUri?.let { uri ->
                var displayUri = uri
                try {
                    // Always preserve an untouched original, independently of the optional backup setting.
                    val copies = com.lunageralddlsjbc.icamerapro17.camera.PhotoStorageManager
                        .createCopies(context, uri, keepOriginalBackup)

                    displayUri = copies.enhancedUri

                    // Process only the separate Enhanced copy. The Original is never modified.
                    if (autoEnhance) {
                        if (com.lunageralddlsjbc.icamerapro17.camera.PhotoStorageManager
                                .canEnhanceSafely(context, copies.enhancedUri)) {
                            try {
                                AutoPhotoEnhancer.enhance(context, copies.enhancedUri)
                            } catch (e: Exception) {
                                Log.e("Camera", "Enhancement failed; retaining unmodified enhanced copy", e)
                            }
                        } else {
                            Log.w("Camera", "Enhancement skipped to protect available memory")
                        }
                    }

                    // Portrait processing only modifies the separate Enhanced copy.
                    if (portraitMode) {
                        if (com.lunageralddlsjbc.icamerapro17.camera.PhotoStorageManager
                                .canEnhanceSafely(context, copies.enhancedUri)) {
                            com.lunageralddlsjbc.icamerapro17.ai.PortraitPhotoProcessor
                                .apply(context, copies.enhancedUri)
                        } else {
                            Log.w("Portrait", "Portrait effect skipped to protect available memory")
                        }
                    }

                    // Restore available camera metadata after the JPEG has been rewritten.
                    try {
                        com.lunageralddlsjbc.icamerapro17.camera.PhotoStorageManager
                            .restoreExifMetadata(context, copies.originalUri, copies.enhancedUri)
                    } catch (e: Exception) {
                        Log.e("Camera", "Could not restore all EXIF metadata", e)
                    }
                } catch (e: Exception) {
                    // The original CameraX capture remains untouched if copy creation fails.
                    Log.e("Camera", "Phase 2A storage failed; showing original capture", e)
                    displayUri = uri
                }

                ContextCompat.getMainExecutor(context).execute {
                    onSaved(displayUri)
                }
            }
        }

        override fun onError(exc: ImageCaptureException) {
            Log.e("Camera", "Capture failed: ${exc.message}", exc)
        }
    })
}

/**
 * Applies JPEG orientation, center-crops without stretching, and saves the
 * exact requested pixel dimensions back to the same MediaStore image.
 */
private fun backupOriginalPhoto(context: Context, sourceUri: Uri) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "ORIGINAL_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/iCamera Pro 17 Originals")
        }
    }

    val backupUri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ) ?: throw IllegalStateException("Could not create original backup")

    try {
        context.contentResolver.openInputStream(sourceUri).use { input ->
            context.contentResolver.openOutputStream(backupUri).use { output ->
                if (input == null || output == null) {
                    throw IllegalStateException("Could not copy original photo")
                }
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        context.contentResolver.delete(backupUri, null, null)
        throw e
    }
}

private fun normalizeCapturedPhoto(
    context: Context,
    uri: Uri,
    aspectRatio: CameraAspectRatio
) {
    val resolver = context.contentResolver

    val orientation = resolver.openInputStream(uri)?.use { input ->
        ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } ?: ExifInterface.ORIENTATION_NORMAL

    val decoded = resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    } ?: throw IllegalStateException("Could not decode captured photo")

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
    }

    val oriented = if (!matrix.isIdentity) {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } else {
        decoded
    }

    val targetRatio = aspectRatio.outputWidth.toFloat() / aspectRatio.outputHeight.toFloat()
    val sourceRatio = oriented.width.toFloat() / oriented.height.toFloat()

    val cropWidth: Int
    val cropHeight: Int

    if (sourceRatio > targetRatio) {
        cropHeight = oriented.height
        cropWidth = (cropHeight * targetRatio).toInt().coerceAtLeast(1)
    } else {
        cropWidth = oriented.width
        cropHeight = (cropWidth / targetRatio).toInt().coerceAtLeast(1)
    }

    val left = (oriented.width - cropWidth) / 2
    val top = (oriented.height - cropHeight) / 2

    val cropped = Bitmap.createBitmap(oriented, left, top, cropWidth, cropHeight)
    val resized = Bitmap.createScaledBitmap(
        cropped,
        aspectRatio.outputWidth,
        aspectRatio.outputHeight,
        true
    )

    try {
        resolver.openOutputStream(uri, "wt")?.use { output ->
            if (!resized.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw IllegalStateException("Could not save resized JPEG")
            }
        } ?: throw IllegalStateException("Could not reopen photo for saving")
    } finally {
        if (resized !== cropped) resized.recycle()
        if (cropped !== oriented) cropped.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
    }

    Log.i(
        "Camera",
        "Saved ${aspectRatio.label}: ${aspectRatio.outputWidth}x${aspectRatio.outputHeight}"
    )
}
