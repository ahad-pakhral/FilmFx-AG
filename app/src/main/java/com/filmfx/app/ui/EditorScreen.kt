package com.filmfx.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.filmfx.app.engine.filters.LinearizeFilter
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

    val linearizeFilter = remember { LinearizeFilter() }
    val toneMapFilter = remember { ToneMapFilter() }
    val filterChain = remember { GPUImageFilterGroup(listOf(linearizeFilter, toneMapFilter)) }
    
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
                            
                            // Apply to filters (redundant with LaunchedEffect but ensures order and reactivity)
                            toneMapFilter.exposure = currentExp
                            toneMapFilter.contrast = currentCon
                            toneMapFilter.shoulder = currentShld
                            toneMapFilter.toe = currentToe
                            
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

            // Bottom Control Area: Cinematic Sliders
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                color = Color(0xFF1A1A1A)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    SliderRow(label = "Exposure", value = exposure, range = -2f..2f) { newValue : Float -> exposure = newValue }
                    SliderRow(label = "Contrast", value = contrast, range = 0.5f..2.0f) { newValue : Float -> contrast = newValue }
                    SliderRow(label = "Latitude", value = shoulder, range = -1f..1f) { newValue : Float -> shoulder = newValue }
                    SliderRow(label = "Shadows", value = toe, range = -1f..1f) { newValue : Float -> toe = newValue }
                }
            }
        }
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
