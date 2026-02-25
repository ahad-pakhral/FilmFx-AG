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
import com.filmfx.app.utils.ImageUtils
import jp.co.cyberagent.android.gpuimage.GPUImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
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
                if (loadedBitmap == null && errorMessage == null) {
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
                                setScaleType(GPUImageView.ScaleType.CENTER_INSIDE)
                                gpuImageView = this
                            }
                        },
                        update = { view ->
                            loadedBitmap?.let { bmp ->
                                try {
                                    // Task 2: Upload to GPU and recycle
                                    view.setImage(bmp)
                                    // GPUImage handles texture filtering (bilinear by default)
                                    // and we can clear our CPU reference to let the GC clean it up
                                    loadedBitmap = null
                                } catch (e: Exception) {
                                    // Task 3: UI error boundary
                                    Toast.makeText(context, "Renderer crashed. Restarting surface.", Toast.LENGTH_LONG).show()
                                    errorMessage = "GPU Error: Unsupported or crashed."
                                }
                            }
                        }
                    )
                }
            }

            // Bottom Control Area Placeholder
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                color = Color(0xFF1A1A1A)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Effect Controls (Placeholder)",
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
