package com.filmfx.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.filmfx.app.engine.filters.BloomFilter
import com.filmfx.app.engine.filters.HalationFilter
import com.filmfx.app.engine.filters.LinearizeFilter
import com.filmfx.app.engine.filters.SplitToningFilter
import com.filmfx.app.engine.filters.ToneMapFilter
import com.filmfx.app.engine.filters.VignetteFilter
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
    
    // Phase 04: Spatial Effects Parameters
    var halationIntensity by remember { mutableStateOf(0.0f) }
    var halationThreshold by remember { mutableStateOf(0.6f) }
    var bloomIntensity by remember { mutableStateOf(0.0f) }
    var bloomThreshold by remember { mutableStateOf(0.8f) }
    var vignetteIntensity by remember { mutableStateOf(0.0f) }
    var vignetteSmoothness by remember { mutableStateOf(0.5f) }

    // UI State: activeTool manages what panel is sliding up
    var activeTool by remember { mutableStateOf<ToolType?>(null) }

    val linearizeFilter = remember { LinearizeFilter() }
    val splitToningFilter = remember { SplitToningFilter() }
    val halationFilter = remember { HalationFilter() }
    val bloomFilter = remember { BloomFilter() }
    val vignetteFilter = remember { VignetteFilter() }
    val toneMapFilter = remember { ToneMapFilter() }
    
    val filterChain = remember { 
        GPUImageFilterGroup(listOf(
            linearizeFilter, 
            splitToningFilter, 
            halationFilter,
            bloomFilter,
            vignetteFilter,
            toneMapFilter
        )) 
    }
    
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
                            
                            halationFilter.setIntensity(halationIntensity)
                            halationFilter.setThreshold(halationThreshold)
                            bloomFilter.setIntensity(bloomIntensity)
                            bloomFilter.setThreshold(bloomThreshold)
                            vignetteFilter.intensity = vignetteIntensity
                            vignetteFilter.smoothness = vignetteSmoothness
                            
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

            // Bottom Navigation Area (Lightroom Style)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                color = Color(0xFF121212)
            ) {
                Column {
                    // 1. Sliding Tool Panel (appears when a tool is selected)
                    AnimatedVisibility(
                        visible = activeTool != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            color = Color(0xFF1E1E1E)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 20.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (activeTool) {
                                    ToolType.LIGHT -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SliderRow(label = "Exposure", value = exposure, range = -2f..2f) { exposure = it }
                                            SliderRow(label = "Contrast", value = contrast, range = 0.5f..2.0f) { contrast = it }
                                            SliderRow(label = "Latitude", value = shoulder, range = -1f..1f) { shoulder = it }
                                            SliderRow(label = "Shadows", value = toe, range = -1f..1f) { toe = it }
                                        }
                                    }
                                    ToolType.SPLIT_TONING -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            // HSL Readout (Professional Feedback)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Shadow H: ${shadowHue.toInt()}° S: ${(shadowSat * 100).toInt()}%",
                                                    color = Color.Cyan.copy(alpha = 0.9f),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                Text(
                                                    text = "Highlight H: ${highlightHue.toInt()}° S: ${(highlightSat * 100).toInt()}%",
                                                    color = Color.Yellow.copy(alpha = 0.9f),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly
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
                                            SliderRow(label = "Balance", value = balance, range = -1f..1f) { balance = it }
                                            SliderRow(label = "Impact", value = splitToningImpact, range = 0f..1f) { splitToningImpact = it }
                                        }
                                    }
                                    ToolType.EFFECTS -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Halation", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                            SliderRow(label = "Intensity", value = halationIntensity, range = 0f..1f) { halationIntensity = it }
                                            SliderRow(label = "Threshold", value = halationThreshold, range = 0f..1f) { halationThreshold = it }
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Bloom", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                            SliderRow(label = "Intensity", value = bloomIntensity, range = 0f..1f) { bloomIntensity = it }
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Vignette", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                            SliderRow(label = "Intensity", value = vignetteIntensity, range = 0f..1f) { vignetteIntensity = it }
                                            SliderRow(label = "Smoothing", value = vignetteSmoothness, range = 0f..1f) { vignetteSmoothness = it }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }

                    // 2. Scrollable Tool Navbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(Color(0xFF0A0A0A))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolNavItem(
                            name = "Light",
                            icon = Icons.Default.Brightness6,
                            isSelected = activeTool == ToolType.LIGHT,
                            onClick = { activeTool = if (activeTool == ToolType.LIGHT) null else ToolType.LIGHT }
                        )
                        ToolNavItem(
                            name = "Split Toning",
                            icon = Icons.Default.ColorLens,
                            isSelected = activeTool == ToolType.SPLIT_TONING,
                            onClick = { activeTool = if (activeTool == ToolType.SPLIT_TONING) null else ToolType.SPLIT_TONING }
                        )
                        ToolNavItem(
                            name = "Effects",
                            icon = Icons.Default.AutoAwesome,
                            isSelected = activeTool == ToolType.EFFECTS,
                            onClick = { activeTool = if (activeTool == ToolType.EFFECTS) null else ToolType.EFFECTS }
                        )
                        ToolNavItem(
                            name = "Detail",
                            icon = Icons.Default.Grain,
                            isSelected = activeTool == ToolType.DETAIL,
                            onClick = { activeTool = if (activeTool == ToolType.DETAIL) null else ToolType.DETAIL }
                        )
                    }
                }
            }
        }
    }
}

enum class ToolType {
    LIGHT, SPLIT_TONING, EFFECTS, DETAIL
}

@Composable
fun ToolNavItem(
    name: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            tint = if (isSelected) Color.White else Color.Gray,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color.White else Color.Gray
        )
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
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Red
                        )
                    )
                )
                .background(Color.Black.copy(alpha = 0.3f)) // Subdued overlay
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
