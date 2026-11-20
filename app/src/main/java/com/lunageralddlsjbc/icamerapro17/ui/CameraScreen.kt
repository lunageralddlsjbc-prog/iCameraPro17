package com.lunageralddlsjbc.icamerapro17.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lunageralddlsjbc.icamerapro17.hardware.CapabilityManager
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
    
    // Permissions
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        Log.d("Perms", "Camera: ${perms[Manifest.permission.CAMERA]}, Audio: ${perms[Manifest.permission.RECORD_AUDIO]}")
    }
    
    LaunchedEffect(Unit) {
        launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    
    // State
    var captureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var exposureComp by remember { mutableStateOf(0) }
    var zoomRatio by remember { mutableStateOf(1f) }
    
    // Hardware Capabilities
    val capabilities = remember { CapabilityManager.detect(context) }
    
    // Camera State
    var previewViewRef by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Initialize Camera
    LaunchedEffect(captureMode, isFrontCamera, flashOn, exposureComp) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                
                val cameraSelector = if (isFrontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewViewRef?.surfaceProvider)
                }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                
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
                
                // Apply Pro Settings
                if (captureMode == CaptureMode.PRO && capabilities.supportsManualExposure) {
                    camera?.cameraControl?.setExposureCompensationIndex(exposureComp)
                }
                
                // Apply Zoom
                camera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(1f, capabilities.maxZoom))
                
            } catch (e: Exception) {
                Log.e("Camera", "Bind failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview with Tap-to-Focus
        AndroidView(
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                    
                    // Tap to Focus
                    setOnTouchListener { v, event ->
                        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            camera?.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(
                                    androidx.camera.core.MeteringPointFactory.createPoint(event.x, event.y)
                                ).build()
                            )
                        }
                        true
                    }
                }.also { previewViewRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // UI Overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SharpModeTab("PHOTO", captureMode == CaptureMode.PHOTO) { 
                        captureMode = CaptureMode.PHOTO 
                        context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10)
                    }
                    SharpModeTab("VIDEO", captureMode == CaptureMode.VIDEO) { 
                        captureMode = CaptureMode.VIDEO 
                        context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10)
                    }
                    if (capabilities.supportsManualExposure) {
                        SharpModeTab("PRO", captureMode == CaptureMode.PRO) { 
                            captureMode = CaptureMode.PRO 
                            context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10)
                        }
                    }
                }
            }
            
            // Bottom Controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(bottom = 40.dp, top = 10.dp)
            ) {
                // Pro Panel (Fixed Position)
                if (captureMode == CaptureMode.PRO && capabilities.supportsManualExposure) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .offset(y = (-70).dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E).copy(alpha = 0.9f)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("EXPOSURE ($exposureComp)", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = exposureComp.toFloat(),
                                onValueChange = { exposureComp = it.toInt() },
                                valueRange = capabilities.minExposureComp.toFloat()..capabilities.maxExposureComp.toFloat(),
                                steps = (capabilities.maxExposureComp - capabilities.minExposureComp - 1).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFFFFD700),
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { flashOn = !flashOn }) {
                        Text(
                            text = if (flashOn) "⚡" else "○",
                            color = if (flashOn) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
                            fontSize = 24.sp
                        )
                    }
                    
                    val shutterColor by animateColorAsState(
                        targetValue = if (isRecording) Color.Red else Color.White,
                        label = "shutter"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                            .padding(5.dp)
                            .background(shutterColor, CircleShape)
                            .clickable {
                                context.getSystemService(android.os.Vibrator::class.java)?.vibrate(20)
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
                        if (isRecording) {
                            Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                        }
                    }
                    
                    IconButton(onClick = { isFrontCamera = !isFrontCamera }) {
                        Text("🔄", color = Color.White.copy(alpha = 0.8f), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SharpModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 12.dp).clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Gray,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(2.dp)
                .background(if (selected) Color(0xFFFFD700) else Color.Transparent)
        )
    }
}

private fun takePhoto(
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    context: Context
) {
    val capture = imageCapture ?: return
    
    val contentValues = ContentValues(
cd ~/iCameraPro17

# ============================================================
# 1. FIX ANDROIDMANIFEST.XML (Add Audio Permission Properly)
# ============================================================
cat > app/src/main/AndroidManifest.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
        android:maxSdkVersion="29" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.VIBRATE" />
    
    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />
    
    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.ICameraPro17">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:configChanges="orientation|screenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

# ============================================================
# 2. CREATE HARDWARE CAPABILITY MANAGER (For iPhone-Quality Detection)
# ============================================================
mkdir -p app/src/main/java/com/lunageralddlsjbc/icamerapro17/hardware

cat > app/src/main/java/com/lunageralddlsjbc/icamerapro17/hardware/CapabilityManager.kt << 'EOF'
package com.lunageralddlsjbc.icamerapro17.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import androidx.camera.core.CameraSelector

data class CameraCapabilities(
    val hasBackCamera: Boolean = false,
    val hasFrontCamera: Boolean = false,
    val supportsManualExposure: Boolean = false,
    val supportsManualFocus: Boolean = false,
    val supportsRawCapture: Boolean = false,
    val maxZoom: Float = 1.0f,
    val minExposureComp: Int = -2,
    val maxExposureComp: Int = 2,
    val sensorSize: String = "Unknown"
)

object CapabilityManager {
    fun detect(context: Context): CameraCapabilities {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var capabilities = CameraCapabilities()
        
        try {
            // Check Back Camera
            if (CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraManager.cameraIdList).isNotEmpty()) {
                capabilities = capabilities.copy(hasBackCamera = true)
                val chars = cameraManager.getCameraCharacteristics(
                    CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraManager.cameraIdList).first()
                )
                
                // Manual Controls
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                capabilities = capabilities.copy(
                    supportsManualExposure = caps?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
                    ) == true,
                    supportsManualFocus = caps?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_FOCUS
                    ) == true,
                    supportsRawCapture = caps?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                    ) == true
                )
                
                // Zoom
                capabilities = capabilities.copy(
                    maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                )
                
                // Exposure Range
                val expRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                if (expRange != null) {
                    capabilities = capabilities.copy(
                        minExposureComp = expRange.lower,
                        maxExposureComp = expRange.upper
                    )
                }
            }
            
            // Check Front Camera
            if (CameraSelector.DEFAULT_FRONT_CAMERA.filter(cameraManager.cameraIdList).isNotEmpty()) {
                capabilities = capabilities.copy(hasFrontCamera = true)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return capabilities
    }
}
EOF

# ============================================================
# 3. UPDATE CAMERA SCREEN (Fix Audio, Pro UI, Add Haptics)
# ============================================================
cat > app/src/main/java/com/lunageralddlsjbc/icamerapro17/ui/CameraScreen.kt << 'ENDOFFILE'
package com.lunageralddlsjbc.icamerapro17.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lunageralddlsjbc.icamerapro17.hardware.CapabilityManager
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
    
    // Permissions
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        Log.d("Perms", "Camera: ${perms[Manifest.permission.CAMERA]}, Audio: ${perms[Manifest.permission.RECORD_AUDIO]}")
    }
    
    LaunchedEffect(Unit) {
        launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    
    // State
    var captureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var exposureComp by remember { mutableStateOf(0) }
    var zoomRatio by remember { mutableStateOf(1f) }
    
    // Hardware Capabilities
    val capabilities = remember { CapabilityManager.detect(context) }
    
    // Camera State
    var previewViewRef by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Initialize Camera
    LaunchedEffect(captureMode, isFrontCamera, flashOn, exposureComp) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                
                val cameraSelector = if (isFrontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewViewRef?.surfaceProvider)
                }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                
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
                
                // Apply Pro Settings
                if (captureMode == CaptureMode.PRO && capabilities.supportsManualExposure) {
                    camera?.cameraControl?.setExposureCompensationIndex(exposureComp)
                }
                
                // Apply Zoom
                camera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(1f, capabilities.maxZoom))
                
            } catch (e: Exception) {
                Log.e("Camera", "Bind failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview with Tap-to-Focus
        AndroidView(
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                    
                    // Tap to Focus
                    setOnTouchListener { v, event ->
                        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            camera?.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(
                                    androidx.camera.core.MeteringPointFactory.createPoint(event.x, event.y)
                                ).build()
                            )
                        }
                        true
                    }
                }.also { previewViewRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // UI Overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SharpModeTab("PHOTO", captureMode == CaptureMode.PHOTO) { 
                        captureMode = CaptureMode.PHOTO 
                        context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10)
                    }
                    SharpModeTab("VIDEO", captureMode == CaptureMode.VIDEO) { 
                        captureMode = CaptureMode.VIDEO 
                        context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10)
                    }
                    if (capabilities.supportsManualExposure) {
                        SharpModeTab("PRO", captureMode == CaptureMode.PRO) { 
                            captureMode = CaptureMode.PRO 
                            context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10)
                        }
                    }
                }
            }
            
            // Bottom Controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(bottom = 40.dp, top = 10.dp)
            ) {
                // Pro Panel (Fixed Position)
                if (captureMode == CaptureMode.PRO && capabilities.supportsManualExposure) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .offset(y = (-70).dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E).copy(alpha = 0.9f)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("EXPOSURE ($exposureComp)", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = exposureComp.toFloat(),
                                onValueChange = { exposureComp = it.toInt() },
                                valueRange = capabilities.minExposureComp.toFloat()..capabilities.maxExposureComp.toFloat(),
                                steps = (capabilities.maxExposureComp - capabilities.minExposureComp - 1).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFFFFD700),
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { flashOn = !flashOn }) {
                        Text(
                            text = if (flashOn) "⚡" else "○",
                            color = if (flashOn) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
                            fontSize = 24.sp
                        )
                    }
                    
                    val shutterColor by animateColorAsState(
                        targetValue = if (isRecording) Color.Red else Color.White,
                        label = "shutter"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                            .padding(5.dp)
                            .background(shutterColor, CircleShape)
                            .clickable {
                                context.getSystemService(android.os.Vibrator::class.java)?.vibrate(20)
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
                        if (isRecording) {
                            Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                        }
                    }
                    
                    IconButton(onClick = { isFrontCamera = !isFrontCamera }) {
                        Text("🔄", color = Color.White.copy(alpha = 0.8f), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SharpModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 12.dp).clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Gray,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(2.dp)
                .background(if (selected) Color(0xFFFFD700) else Color.Transparent)
        )
    }
}

private fun takePhoto(
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    context: Context
) {
    val capture = imageCapture ?: return
    
    val contentValues = ContentValues(
cd ~/iCameraPro17

# ============================================================
# 1. FIX ANDROIDMANIFEST.XML (Add Audio Permission Properly)
# ============================================================
cat > app/src/main/AndroidManifest.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
        android:maxSdkVersion="29" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.VIBRATE" />
    
    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />
    
    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.ICameraPro17">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:configChanges="orientation|screenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

# ============================================================
# 2. CREATE HARDWARE CAPABILITY MANAGER (For iPhone-Quality Detection)
# ============================================================
mkdir -p app/src/main/java/com/lunageralddlsjbc/icamerapro17/hardware

cat > app/src/main/java/com/lunageralddlsjbc/icamerapro17/hardware/CapabilityManager.kt << 'EOF'
package com.lunageralddlsjbc.icamerapro17.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import androidx.camera.core.CameraSelector

data class CameraCapabilities(
    val hasBackCamera: Boolean = false,
    val hasFrontCamera: Boolean = false,
    val supportsManualExposure: Boolean = false,
    val supportsManualFocus: Boolean = false,
    val supportsRawCapture: Boolean = false,
    val maxZoom: Float = 1.0f,
    val minExposureComp: Int = -2,
    val maxExposureComp: Int = 2,
    val sensorSize: String = "Unknown"
)

object CapabilityManager {
    fun detect(context: Context): CameraCapabilities {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var capabilities = CameraCapabilities()
        
        try {
            // Check Back Camera
            if (CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraManager.cameraIdList).isNotEmpty()) {
                capabilities = capabilities.copy(hasBackCamera = true)
                val chars = cameraManager.getCameraCharacteristics(
                    CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraManager.cameraIdList).first()
                )
                
                // Manual Controls
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                capabilities = capabilities.copy(
                    supportsManualExposure = caps?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
                    ) == true,
                    supportsManualFocus = caps?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_FOCUS
                    ) == true,
                    supportsRawCapture = caps?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                    ) == true
                )
                
                // Zoom
                capabilities = capabilities.copy(
                    maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                )
                
                // Exposure Range
                val expRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                if (expRange != null) {
                    capabilities = capabilities.copy(
                        minExposureComp = expRange.lower,
                        maxExposureComp = expRange.upper
                    )
                }
            }
            
            // Check Front Camera
            if (CameraSelector.DEFAULT_FRONT_CAMERA.filter(cameraManager.cameraIdList).isNotEmpty()) {
                capabilities = capabilities.copy(hasFrontCamera = true)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return capabilities
    }
}
EOF

# ============================================================
# 3. UPDATE CAMERA SCREEN (Fix Audio, Pro UI, Add Haptics)
# ============================================================
cat > app/src/main/java/com/lunageralddlsjbc/icamerapro17/ui/CameraScreen.kt << 'ENDOFFILE'
package com.lunageralddlsjbc.icamerapro17.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lunageralddlsjbc.icamerapro17.hardware.CapabilityManager
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
    
    // Permissions
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        Log.d("Perms", "Camera: ${perms[Manifest.permission.CAMERA]}, Audio: ${perms[Manifest.permission.RECORD_AUDIO]}")
    }
    
    LaunchedEffect(Unit) {
        launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    
    // State
    var captureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var exposureComp by remember { mutableStateOf(0) }
    var zoomRatio by remember { mutableStateOf(1f) }
    
    // Hardware Capabilities
    val capabilities = remember { CapabilityManager.detect(context) }
    
    // Camera State
    var previewViewRef by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Initialize Camera
    LaunchedEffect(captureMode, isFrontCamera, flashOn, exposureComp) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                
                val cameraSelector = if (isFrontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewViewRef?.surfaceProvider)
                }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                
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
                
                // Apply Pro Settings
                if (captureMode == CaptureMode.PRO && capabilities.supportsManualExposure) {
                    camera?.cameraControl?.setExposureCompensationIndex(exposureComp)
                }
                
                // Apply Zoom
                camera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(1f, capabilities.maxZoom))
                
            } catch (e: Exception) {
                Log.e("Camera", "Bind failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview with Tap-to-Focus
        AndroidView(
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                    
                    // Tap to Focus
                    setOnTouchListener { v, event ->
                        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            camera?.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(
                                    androidx.camera.core.MeteringPointFactory.createPoint(event.x, event.y)
                                ).build()
                            )
                        }
                        true
                    }
                }.also { previewViewRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // UI Overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SharpModeTab("PHOTO", captureMode == CaptureMode.PHOTO) { 
                        captureMode = CaptureMode.PHOTO 
                        context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10)
                    }
                    SharpModeTab("VIDEO", captureMode == CaptureMode.VIDEO) { 
                        captureMode = CaptureMode.VIDEO 
                        context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10)
                    }
                    if (capabilities.supportsManualExposure) {
                        SharpModeTab("PRO", captureMode == CaptureMode.PRO) { 
                            captureMode = CaptureMode.PRO 
                            context.getSystemService(android.os.Vibrator::class.java)?.vibrate(10)
                        }
                    }
                }
            }
            
            // Bottom Controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(bottom = 40.dp, top = 10.dp)
            ) {
                // Pro Panel (Fixed Position)
                if (captureMode == CaptureMode.PRO && capabilities.supportsManualExposure) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .offset(y = (-70).dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E).copy(alpha = 0.9f)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("EXPOSURE ($exposureComp)", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = exposureComp.toFloat(),
                                onValueChange = { exposureComp = it.toInt() },
                                valueRange = capabilities.minExposureComp.toFloat()..capabilities.maxExposureComp.toFloat(),
                                steps = (capabilities.maxExposureComp - capabilities.minExposureComp - 1).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFFFFD700),
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { flashOn = !flashOn }) {
                        Text(
                            text = if (flashOn) "⚡" else "○",
                            color = if (flashOn) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
                            fontSize = 24.sp
                        )
                    }
                    
                    val shutterColor by animateColorAsState(
                        targetValue = if (isRecording) Color.Red else Color.White,
                        label = "shutter"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                            .padding(5.dp)
                            .background(shutterColor, CircleShape)
                            .clickable {
                                context.getSystemService(android.os.Vibrator::class.java)?.vibrate(20)
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
                        if (isRecording) {
                            Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                        }
                    }
                    
                    IconButton(onClick = { isFrontCamera = !isFrontCamera }) {
                        Text("🔄", color = Color.White.copy(alpha = 0.8f), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SharpModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 12.dp).clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Gray,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(2.dp)
                .background(if (selected) Color(0xFFFFD700) else Color.Transparent)
        )
    }
}

private fun takePhoto(
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    context: Context
) {
    val capture = imageCapture ?: return
    
    val contentValues = ContentValues().apply {
        Spacer(modifier = Modifier.height(6.dp))
fun SharpModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
                .background(if (selected) Color(0xFFFFD700) else Color.Transparent)
            Log.e("Camera", "Photo save failed", exc)
                        contentAlignment = Alignment.Center
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            Log.d("Camera", "Photo saved to Gallery: ${output.savedUri}")
                        if (isRecording) {
                }
            }
    ) {
        Text(
private fun startRecording(
                    
                    }
                        }
            fontSize = 13.sp,
            letterSpacing = 0.8.sp
        }
    
    
    Column(
    executor: ExecutorService,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
    }
        Box(
                .width(16.dp)
    imageCapture: ImageCapture?,
    onStarted: (Recording) -> Unit
                    }
        )
            modifier = Modifier
}
}
    }
) {

            text = label,
        contentValues
    context: Context,
        }
                .height(2.dp)
@Composable
    executor: ExecutorService,
}
private fun takePhoto(
                    ) {
        context.contentResolver,
    ).build()
    context: Context
        )

    
) {
    }
    videoCapture: VideoCapture<Recorder>?,
        }

    
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/iCamera Pro 17")
    val capture = videoCapture ?: return
    val capture = imageCapture ?: return
        }
    })
        }
    }
    
    val outputFileOptions = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(contentValues).build()
    
    // FIXED: Ensure audio is enabled
    val recording = capture.output.prepareRecording(context, outputFileOptions)
        .withAudioEnabled()
        .start(executor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> Log.d("Camera", "Recording started with audio")
                is VideoRecordEvent.Finalize -> Log.d("Camera", "Video saved to Gallery")
                is VideoRecordEvent.Status -> {}
            }
        }
    
    onStarted(recording)
}
