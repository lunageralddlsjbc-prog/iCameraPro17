package com.lunageralddlsjbc.icamerapro17.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lunageralddlsjbc.icamerapro17.gallery.GalleryThumbnail
import com.lunageralddlsjbc.icamerapro17.hardware.CapabilityManager
import com.lunageralddlsjbc.icamerapro17.processing.HdrProcessor
import com.lunageralddlsjbc.icamerapro17.processing.PhotographicStyle
import com.lunageralddlsjbc.icamerapro17.settings.CameraSettingsManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

enum class CaptureMode { PHOTO, VIDEO, PRO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> Log.d("Perms", "Granted: ${perms.filter { it.value }.keys}") }
    
    LaunchedEffect(Unit) { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
    
    var captureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var exposureComp by remember { mutableStateOf(0) }
    var isHdrEnabled by remember { mutableStateOf(true) }
    var currentStyle by remember { mutableStateOf(PhotographicStyle.STANDARD) }
    var lastMediaPath by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isShutterPressed by remember { mutableStateOf(false) }
    var showCaptureFlash by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    
    // Settings State
    var gridEnabled by remember { mutableStateOf(false) }
    var levelEnabled by remember { mutableStateOf(true) }
    var mirrorFront by remember { mutableStateOf(false) }
    var lensCorrection by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        CameraSettingsManager.getGridFlow(context).collectLatest { gridEnabled = it }
        CameraSettingsManager.getLevelFlow(context).collectLatest { levelEnabled = it }
        CameraSettingsManager.getMirrorFlow(context).collectLatest { mirrorFront = it }
        CameraSettingsManager.getLensCorrectionFlow(context).collectLatest { lensCorrection = it }
    }
    
    val capabilities = remember { CapabilityManager.detect(context) }
    
    var previewViewRef by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // FIXED: Robust camera binding with error handling
    LaunchedEffect(captureMode, isFrontCamera, flashOn, exposureComp) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                
                val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewViewRef?.surfaceProvider)
                }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.Builder(recorder).build()
                
                camera = when (captureMode) {
                    CaptureMode.VIDEO -> cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, videoCapture)
                    else -> cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                }
                
                if (captureMode == CaptureMode.PRO && capabilities.supportsManualExposure) {
                    camera?.cameraControl?.setExposureCompensationIndex(exposureComp)
                }
                
                if (captureMode != CaptureMode.VIDEO) { isRecording = false; activeRecording = null }
                
                Log.d("Camera", "Bound successfully: $captureMode, Front: $isFrontCamera")
            } catch (e: Exception) { 
                Log.e("Camera", "Bind FAILED: ${e.message}", e) 
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    val shutterScale by animateFloatAsState(
        targetValue = if (isShutterPressed) 0.8f else 1f, 
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f), label = "shutter"
    )
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // FIXED: Apply mirror effect via graphicsLayer
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
                            val point = factory.createPoint(event.x / v.width, event.y / v.height)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                        true
                    }
                }.also { previewViewRef = it }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (isFrontCamera && mirrorFront) -1f else 1f }
        )
        
        if (showCaptureFlash) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f)))
        
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 50.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)), modifier = Modifier.clickable { isHdrEnabled = !isHdrEnabled }) {
                    Text(text = if (isHdrEnabled) "HDR ON" else "HDR OFF", color = if (isHdrEnabled) Color(0xFFFFD700) else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { flashOn = !flashOn }) { Text(text = if (flashOn) "⚡" else "○", color = if (flashOn) Color(0xFFFFD700) else Color.White, fontSize = 20.sp) }
                    IconButton(onClick = { showSettings = true }) { Text(text = "⋮", color = Color.White, fontSize = 24.sp) }
                }
            }
            
            Box(modifier = Modifier.fillMaxWidth().background(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))).padding(bottom = 30.dp)) {
                // GALLERY SHORTCUT (Fixed)
                GalleryThumbnail(lastMediaPath = lastMediaPath, modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 90.dp))
                
                Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(4.dp), horizontalArrangement = Arrangement.Center) {
                    IphoneModeTab("VIDEO", captureMode == CaptureMode.VIDEO) { captureMode = CaptureMode.VIDEO; context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10) }
                    IphoneModeTab("PHOTO", captureMode == CaptureMode.PHOTO) { captureMode = CaptureMode.PHOTO; context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10) }
                    if (capabilities.supportsManualExposure) IphoneModeTab("PRO", captureMode == CaptureMode.PRO) { captureMode = CaptureMode.PRO; context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10) }
                }
                
                Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(40.dp))
                    
                    val shutterColor by animateColorAsState(targetValue = if (isRecording) Color.Red else Color.White, label = "shutter")
                    Box(
                        modifier = Modifier.size(76.dp).scale(shutterScale).border(4.dp, Color.White.copy(alpha = 0.9f), CircleShape).padding(4.dp).background(shutterColor, CircleShape).clickable(enabled = !isCapturing) { 
                            isShutterPressed = true; showCaptureFlash = true; isCapturing = true
                            context.getSystemService(android.os.Vibrator::class.java)?.vibrate(25)
                            GlobalScope.launch(Dispatchers.Main) { delay(100); isShutterPressed = false; delay(50); showCaptureFlash = false; delay(200); isCapturing = false }
                            
                            if (captureMode == CaptureMode.VIDEO) {
                                if (!isRecording) startRecording(context, videoCapture, cameraExecutor) { rec -> activeRecording = rec; isRecording = true }
                                else { activeRecording?.stop(); activeRecording = null; isRecording = false }
                            } else takePhoto(imageCapture, cameraExecutor, context, isHdrEnabled, currentStyle) { path -> lastMediaPath = path }
                        }.pointerInput(Unit) { detectTapGestures(onPress = { isShutterPressed = true; tryAwaitRelease(); isShutterPressed = false }) },
                        contentAlignment = Alignment.Center
                    ) { if (isRecording) Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)) }
                    
                    IconButton(onClick = { isFrontCamera = !isFrontCamera }) { Text("🔄", color = Color.White.copy(alpha = 0.9f), fontSize = 24.sp) }
                }
            }
        }
    }
    
    // IPHONE-STYLE SETTINGS SHEET (With Visible Styles)
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = Color(0xFF1C1C1E)) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("COMPOSITION", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                IphoneSettingSwitch("Grid", gridEnabled) { GlobalScope.launch(Dispatchers.IO) { CameraSettingsManager.toggleGrid(context) } }
                IphoneSettingSwitch("Level", levelEnabled) { GlobalScope.launch(Dispatchers.IO) { CameraSettingsManager.toggleLevel(context) } }
                IphoneSettingSwitch("Mirror Front Camera", mirrorFront) { GlobalScope.launch(Dispatchers.IO) { CameraSettingsManager.toggleMirror(context) } }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("PHOTOGRAPHIC STYLE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                // FIXED: Styles are now clearly visible in a scrollable row
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotographicStyle.values().forEach { style ->
                        FilterChip(
                            selected = currentStyle == style, 
                            onClick = { currentStyle = style }, 
                            label = { Text(style.styleName, fontSize = 10.sp) }, 
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFD700))
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("LENS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                IphoneSettingSwitch("Lens Correction", lensCorrection) { GlobalScope.launch(Dispatchers.IO) { CameraSettingsManager.toggleLensCorrection(context) } }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun IphoneSettingSwitch(label: String, checked: Boolean, onCheckedChange: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = Color.White, fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = { onCheckedChange() }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF34C759), checkedTrackColor = Color(0xFF34C759).copy(alpha = 0.5f)))
    }
}

@Composable
fun IphoneModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) Color(0xFFFFD700).copy(alpha = 0.2f) else Color.Transparent
    val textColor = if (selected) Color(0xFFFFD700) else Color.Gray
    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor).clickable { onClick() }.padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text = label, color = textColor, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, letterSpacing = 0.5.sp)
    }
}

private fun takePhoto(imageCapture: ImageCapture?, executor: ExecutorService, context: Context, useHdr: Boolean, style: PhotographicStyle, onSaved: (String) -> Unit) {
    val capture = imageCapture ?: return
    if (useHdr) {
        val tempFiles = mutableListOf<File>(); var completed = 0
        repeat(3) { index ->
            val tempFile = File(context.cacheDir, "hdr_temp_$index.jpg"); tempFiles.add(tempFile)
            val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()
            capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    completed++
                    if (completed == 3) {
                        val finalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}_${style.styleName}.jpg")
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                HdrProcessor.processHdrSequence(tempFiles, finalFile, style)
                                saveToGallery(context, finalFile, true); onSaved(finalFile.absolutePath)
                            } catch (e: Exception) { Log.e("Camera", "HDR failed", e) } finally { tempFiles.forEach { it.delete() } }
                        }
                    }
                }
                override fun onError(exc: ImageCaptureException) { Log.e("Camera", "HDR error: ${exc.message}", exc) }
            })
        }
    } else {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/iCamera Pro 17")
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build()
        capture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) { onSaved(output.savedUri?.path ?: "") }
            override fun onError(exc: ImageCaptureException) { Log.e("Camera", "Capture error: ${exc.message}", exc) }
        })
    }
}

private fun saveToGallery(context: Context, file: File, isImage: Boolean) {
    val contentValues = ContentValues().apply {
        if (isImage) { put(MediaStore.Images.Media.DISPLAY_NAME, file.name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/iCamera Pro 17") }
        else { put(MediaStore.Video.Media.DISPLAY_NAME, file.name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4"); put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/iCamera Pro 17") }
    }
    val uri = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    context.contentResolver.insert(uri, contentValues)?.let { destUri ->
        context.contentResolver.openOutputStream(destUri)?.use { outputStream -> file.inputStream().use { inputStream -> inputStream.copyTo(outputStream) } }
    }
}

private fun startRecording(context: Context, videoCapture: VideoCapture<Recorder>?, executor: ExecutorService, onStarted: (Recording) -> Unit) {
    val capture = videoCapture ?: return
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/iCamera Pro 17")
    }
    val outputFileOptions = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(contentValues).build()
    val recording = capture.output.prepareRecording(context, outputFileOptions).withAudioEnabled().start(executor) { event ->
        when (event) { is VideoRecordEvent.Start -> Log.d("Camera", "Started"); is VideoRecordEvent.Finalize -> Log.d("Camera", "Saved"); else -> {} }
    }
    onStarted(recording)
}
