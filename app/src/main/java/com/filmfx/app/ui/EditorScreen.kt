package com.filmfx.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.filmfx.app.engine.filters.LinearizeFilter
import com.filmfx.app.engine.filters.SplitToningFilter
import com.filmfx.app.engine.filters.ToneMapFilter
import com.filmfx.app.utils.ImageUtils
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hasImage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Phase 02: Cinematic Parameters
    var exposure by remember { mutableStateOf(0.0f) }      // -2 to +2
    var contrast by remember { mutableStateOf(1.0f) }      // 0.5 to 2.0
    var shoulder by remember { mutableStateOf(0.0f) }      // 0 to 1
    var toe by remember { mutableStateOf(0.0f) }           // 0 to 1

    // Phase 03: Split Toning Parameters
    var highlightHue by remember { mutableStateOf(0.0f) }
    var highlightSat by remember { mutableStateOf(0.0f) }
    var shadowHue by remember { mutableStateOf(0.0f) }
    var shadowSat by remember { mutableStateOf(0.0f) }
    var balance by remember { mutableStateOf(0.0f) }
    var splitToningImpact by remember { mutableStateOf(1.0f) }

    val linearizeFilter = remember { LinearizeFilter() }
    val splitToningFilter = remember { SplitToningFilter() }
    val toneMapFilter = remember { ToneMapFilter() }
    val filterChain = remember { GPUImageFilterGroup(listOf(linearizeFilter, splitToningFilter, toneMapFilter)) }
    
    var gpuImageView: GPUImageView? = null

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            coroutineScope.launch {
                try {
                    // Task 1: Image picking and downsampling
                    val bmp = withContext(Dispatchers.IO) {
                        try {
                            ImageUtils.loadAndDownsampleImage(context, uri, 1080)
                        } catch (e: OutOfMemoryError) {
                            // Task 3: Automatic downscale/retry on OOM
                            System.gc()
                            ImageUtils.loadAndDownsampleImage(context, uri, 720)
                        }
                    }
                    if (bmp != null) {
                        loadedBitmap = bmp
                        hasImage = true
                        errorMessage = null
                    } else {
                        errorMessage = "Failed to load image."
                    }
                } catch (e: Exception) {
                    errorMessage = "Error loading image: ${e.message}"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FilmFX",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Button(
                    onClick = { launcher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Open Photo", color = Color.White)
                }
            }

            // Preview Area (Photo-centric)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!hasImage && errorMessage == null) {
                    Text(
                        text = "No photo selected",
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                } else if (errorMessage != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(
                            onClick = { launcher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            GPUImageView(ctx).apply {
                                gpuImageView = this
                                this.filter = filterChain
                            }
                        },
                        update = { view ->
                            // Explicitly read these states to ensure Compose triggers 'update' when they change
                            val currentExp = exposure
                            val currentCon = contrast
                            val currentShld = shoulder
                            val currentToe = toe
                            
                            val hH = highlightHue
                            val hS = highlightSat
                            val sH = shadowHue
                            val sS = shadowSat
                            val bal = balance
                            val imp = splitToningImpact

                            // Apply to filters
                            toneMapFilter.exposure = currentExp
                            toneMapFilter.contrast = currentCon
                            toneMapFilter.shoulder = currentShld
                            toneMapFilter.toe = currentToe
                            
                            splitToningFilter.highlightHue = hH
                            splitToningFilter.highlightSat = hS
                            splitToningFilter.shadowHue = sH
                            splitToningFilter.shadowSat = sS
                            splitToningFilter.balance = bal
                            splitToningFilter.impact = imp
                            
                            loadedBitmap?.let { bmp ->
                                try {
                                    view.setImage(bmp)
                                    loadedBitmap = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Renderer crashed. Restarting surface.", Toast.LENGTH_LONG).show()
                                    errorMessage = "GPU Error: Unsupported or crashed."
                                    hasImage = false
                                }
                            }
                            // Ask the view to redraw the existing texture with the new filter parameters
                            view.requestRender()
                        }
                    )
                }
            }

            // Bottom Control Area: Cinematic Sliders & Split Toning
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                color = Color(0xFF1A1A1A)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Page Toggle or Tabs (Simple column for now)
                    Text("Exposure & Latitude", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    SliderRow(label = "Exposure", value = exposure, range = -2f..2f) { newValue : Float -> exposure = newValue }
                    SliderRow(label = "Contrast", value = contrast, range = 0.5f..2.0f) { newValue : Float -> contrast = newValue }
                    SliderRow(label = "Latitude", value = shoulder, range = -1f..1f) { newValue : Float -> shoulder = newValue }
                    SliderRow(label = "Shadows", value = toe, range = -1f..1f) { newValue : Float -> toe = newValue }
                    
                    Divider(color = Color.DarkGray, thickness = 0.5.dp)
                    
                    Text("Split Toning", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        JoystickPad(label = "Shadows", hue = shadowHue, saturation = shadowSat) { h, s ->
                            shadowHue = h
                            shadowSat = s
                        }
                        JoystickPad(label = "Highlights", hue = highlightHue, saturation = highlightSat) { h, s ->
                            highlightHue = h
                            highlightSat = s
                        }
                    }
                    SliderRow(label = "Balance", value = balance, range = -1f..1f) { newValue : Float -> balance = newValue }
                    SliderRow(label = "Impact", value = splitToningImpact, range = 0f..1f) { newValue : Float -> splitToningImpact = newValue }
                }
            }
        }
    }
}

@Composable
fun JoystickPad(
    label: String,
    hue: Float,
    saturation: Float,
    onValueChange: (Float, Float) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            change.consume()
                            val localPos = change.position - Offset(40.dp.toPx(), 40.dp.toPx())
                            val r = Math.sqrt((localPos.x * localPos.x + localPos.y * localPos.y).toDouble()).toFloat()
                            val maxR = 40.dp.toPx()
                            
                            val sat = (r / maxR).coerceIn(0f, 1f)
                            val rawHue = Math.toDegrees(Math.atan2(localPos.y.toDouble(), localPos.x.toDouble())).toFloat()
                            val hueNormalized = if (rawHue < 0) rawHue + 360f else rawHue
                            
                            onValueChange(hueNormalized, sat)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Target Color Indicator (Simplified handle)
            val handleX = Math.cos(Math.toRadians(hue.toDouble())).toFloat() * saturation * 30.dp.value
            val handleY = Math.sin(Math.toRadians(hue.toDouble())).toFloat() * saturation * 30.dp.value
            
            Box(
                modifier = Modifier
                    .offset(x = handleX.dp, y = handleY.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}


@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.Gray,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.DarkGray
            )
        )
        Text(
            text = "%.2f".format(value),
            color = Color.White,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
