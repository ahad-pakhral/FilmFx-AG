package com.filmfx.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmfx.app.engine.EngineContext
import com.filmfx.app.engine.filters.*
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.GPUImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.filmfx.app.data.Preset
import com.filmfx.app.data.EffectParameters
import com.filmfx.app.ui.components.PrecisionSlider

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUri: String? = null,
    projectId: Long? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    
    // Auto-load project or URI when opened
    LaunchedEffect(projectId, imageUri) {
        if (projectId != null) {
            viewModel.loadProject(projectId)
        } else if (imageUri != null) {
            viewModel.setOriginalUri(imageUri)
        }
    }

    val effectParams by viewModel.effectParams.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()

    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedPresets by remember { mutableStateOf(setOf<Long>()) }

    // Proper Back Navigation
    BackHandler {
        viewModel.commitState()
        viewModel.clearCurrentProject()
        onNavigateBack()
    }

    // SAF Launchers for Presets
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let { viewModel.exportPresetsToBundle(presets.filter { it.id in selectedPresets }, it) }
            selectedPresets = setOf()
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.importPresets(it) }
        }
    )
    
    // Derived selectedImageUri
    val selectedImageUri = currentProject?.originalUri?.let { Uri.parse(it) }
    
    var hasImage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load or decode image from URI
    LaunchedEffect(selectedImageUri) {
        selectedImageUri?.let { uri ->
            try {
                // Decode bitmap
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        com.filmfx.app.utils.ImageUtils.loadAndDownsampleImage(context, uri, 1080)
                    } catch (e: OutOfMemoryError) {
                        System.gc()
                        com.filmfx.app.utils.ImageUtils.loadAndDownsampleImage(context, uri, 720)
                    }
                }
                
                if (bmp != null) {
                    loadedBitmap = bmp
                    Log.d("FilmFX", "Bitmap loaded successfully")
                } else {
                    errorMessage = "Failed to load image."
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("FilmFX", "Error loading image", e)
                errorMessage = "Error loading image: ${e.message}"
            }
        }
    }
    
    var showResetConfirmation by remember { mutableStateOf(false) }
    var gpuImageView: GPUImageView? = null

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset All Edits?") },
            text = { Text("This will revert all changes to their default values. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAll()
                        showResetConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val filterChain = remember(uiState.showBefore) {
        val params = if (uiState.showBefore) {
            EffectParameters.DEFAULT.copy(
                transformScale = effectParams.transformScale,
                transformOffsetX = effectParams.transformOffsetX,
                transformOffsetY = effectParams.transformOffsetY,
                transformRotation = effectParams.transformRotation
            )
        } else {
            effectParams
        }
        EngineContext.buildFilterGroup(params)
    }

    // SideEffect to update uniforms for existing filters
    SideEffect {
        val params = if (uiState.showBefore) {
            EffectParameters.DEFAULT.copy(
                transformScale = effectParams.transformScale,
                transformOffsetX = effectParams.transformOffsetX,
                transformOffsetY = effectParams.transformOffsetY,
                transformRotation = effectParams.transformRotation
            )
        } else {
            effectParams
        }
        EngineContext.updateFilterGroup(filterChain, params)
        gpuImageView?.requestRender()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Preview Area (Photo-centric)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!hasImage && errorMessage == null && loadedBitmap == null) {
                    Text("Loading...", color = Color.Gray)
                } else if (errorMessage != null) {
                    Text(errorMessage ?: "Error", color = Color.Red, modifier = Modifier.padding(16.dp))
                } else {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    var lastUpTime = 0L
                                    while (true) {
                                        val down = awaitFirstDown()
                                        
                                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                                        var lastEvent = currentEvent
                                        var isMultiTouch = false
                                        var isMoved = false
                                        
                                        val result = withTimeoutOrNull(longPressTimeout) {
                                            do {
                                                lastEvent = awaitPointerEvent()
                                                if (lastEvent.changes.size > 1) {
                                                    isMultiTouch = true
                                                    return@withTimeoutOrNull null
                                                }
                                                val moved = lastEvent.changes.any { 
                                                    val diff = it.position - it.previousPosition
                                                    diff.getDistance() > 10f 
                                                  }
                                                if (moved) {
                                                    isMoved = true
                                                    return@withTimeoutOrNull null 
                                                }
                                            } while (lastEvent.changes.any { it.pressed })
                                            lastEvent
                                        }

                                        if (result == null) {
                                            // Timeout reached OR move OR multi-touch
                                            if (!isMultiTouch && !isMoved && lastEvent.changes.any { it.pressed }) {
                                                // Real hold
                                                viewModel.updateUiState { it.copy(showBefore = true) }
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                
                                                do {
                                                    lastEvent = awaitPointerEvent()
                                                    if (lastEvent.changes.size > 1) break
                                                } while (lastEvent.changes.any { it.pressed })
                                                
                                                viewModel.updateUiState { it.copy(showBefore = false) }
                                            }
                                        } else {
                                            // Release before timeout
                                            val upTime = System.currentTimeMillis()
                                            if (upTime - lastUpTime < viewConfiguration.doubleTapTimeoutMillis) {
                                                // DOUBLE TAP
                                                viewModel.updateParams { 
                                                    it.copy(
                                                        transformScale = 1.0f,
                                                        transformOffsetX = 0f,
                                                        transformOffsetY = 0f,
                                                        transformRotation = 0f
                                                    )
                                                }
                                                viewModel.commitState()
                                                lastUpTime = 0L
                                            } else {
                                                // Single tap candidate - wait for double tap timeout
                                                lastUpTime = upTime
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(viewConfiguration.doubleTapTimeoutMillis)
                                                    if (lastUpTime == upTime) {
                                                        // It was a single tap
                                                        viewModel.updateUiState { it.copy(isUiVisible = !it.isUiVisible) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput("transform") {
                                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, rotationChange ->
                                    val viewWidth = view.width.toFloat()
                                    val viewHeight = view.height.toFloat()
                                    if (viewWidth <= 0 || viewHeight <= 0) return@detectTransformGestures

                                    viewModel.updateParams { current ->
                                        val newScale = (current.transformScale * zoom).coerceIn(0.5f, 5.0f)
                                        val newOffsetX = current.transformOffsetX + (pan.x / viewWidth)
                                        val newOffsetY = current.transformOffsetY + (pan.y / viewHeight)
                                        
                                        var newRotation = current.transformRotation
                                        if (rotationChange != 0f) {
                                            val rawRotation = (current.transformRotation - rotationChange) % 360f
                                            val normalizedRot = if (rawRotation < 0) rawRotation + 360f else rawRotation
                                            
                                            val threshold = 3f
                                            val snapPoints = listOf(0f, 90f, 180f, 270f, 360f)
                                            
                                            var snapped = false
                                            for (snap in snapPoints) {
                                                if (Math.abs(normalizedRot - snap) < threshold) {
                                                    if (current.transformRotation != snap) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                    newRotation = snap
                                                    snapped = true
                                                    break
                                                }
                                            }
                                            if (!snapped) {
                                                newRotation = normalizedRot
                                            }
                                        }

                                        current.copy(
                                            transformScale = newScale,
                                            transformOffsetX = newOffsetX,
                                            transformOffsetY = newOffsetY,
                                            transformRotation = newRotation
                                        )
                                    }
                                }
                            },
                        factory = { ctx ->
                            GPUImageView(ctx).apply {
                                gpuImageView = this
                                this.setScaleType(GPUImage.ScaleType.CENTER_INSIDE)
                                this.setBackgroundColor(android.graphics.Color.BLACK)
                                this.filter = filterChain
                            }
                        },
                        update = { view ->
                            val viewWidth = view.width.toFloat()
                            val viewHeight = view.height.toFloat()
                            
                            // Ensure the view has the correct filter chain
                            if (view.filter != filterChain) {
                                view.filter = filterChain
                            }
                            
                            // Re-apply resolution-dependent uniforms
                            if (viewWidth > 0 && viewHeight > 0) {
                                EngineContext.updateFilterGroup(filterChain, effectParams, viewWidth, viewHeight)
                            }

                            loadedBitmap?.let { bmp ->
                                try {
                                    view.setImage(bmp)
                                    loadedBitmap = null
                                    hasImage = true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Renderer crashed", Toast.LENGTH_LONG).show()
                                    hasImage = false
                                }
                            }
                            view.requestRender()
                        }
                    )
                }
            }
        }

        // Top Header Tool (Auto-hides)
        AnimatedVisibility(
            visible = uiState.isUiVisible && !uiState.isSliderDragging,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { 
                        viewModel.commitState()
                        viewModel.clearCurrentProject()
                        onNavigateBack() 
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val canUndo by viewModel.canUndo.collectAsState()
                    val canRedo by viewModel.canRedo.collectAsState()
                    val undoText by viewModel.undoText.collectAsState()
                    val redoText by viewModel.redoText.collectAsState()

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = canUndo) { viewModel.undo() }) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (canUndo) Color.White else Color.DarkGray, modifier = Modifier.size(24.dp))
                        Text(if (canUndo) undoText else "", color = Color.Gray, fontSize = 8.sp, maxLines = 1, modifier = Modifier.widthIn(max = 60.dp), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = canRedo) { viewModel.redo() }) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (canRedo) Color.White else Color.DarkGray, modifier = Modifier.size(24.dp))
                        Text(if (canRedo) redoText else "", color = Color.Gray, fontSize = 8.sp, maxLines = 1, modifier = Modifier.widthIn(max = 60.dp), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showResetConfirmation = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White, modifier = Modifier.size(24.dp))
                        Text("Reset", color = Color.Gray, fontSize = 8.sp, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = hasImage && uiState.exportProgress == null) { viewModel.exportImage() }) {
                        if (uiState.exportProgress != null) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                                CircularProgressIndicator(
                                    progress = uiState.exportProgress!! / 100f,
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.Cyan,
                                    trackColor = Color.DarkGray,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export", tint = if (hasImage) Color.White else Color.DarkGray, modifier = Modifier.size(24.dp))
                        }
                        Text(if (uiState.exportProgress != null) "${uiState.exportProgress}%" else "Export", color = Color.Gray, fontSize = 8.sp, maxLines = 1)
                    }
                }
            }
        }

        // Overlaying UI: Drawer and Ribbon
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = uiState.isUiVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    // 1. Sliding Drawer
                    AnimatedVisibility(
                        visible = uiState.isDrawerExpanded && uiState.activeTool != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.padding(bottom = 80.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = (configuration.screenHeightDp * 0.4f).dp),
                            color = Color.Black.copy(alpha = if (uiState.isSliderDragging) 0f else 0.9f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            tonalElevation = 8.dp
                        ) {
                            val toolScroll = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 20.dp)
                                    .fillMaxWidth()
                                    .verticalScrollbar(toolScroll)
                                    .verticalScroll(toolScroll),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (uiState.activeTool) {
                                    ToolType.ATMOSPHERE -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Dehaze", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                            SliderRow(label = "Amount", value = effectParams.dehazeAmount, range = -1f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(dehazeAmount = v) } }
                                            SliderRow(label = "Haze Tint", value = effectParams.dehazeAtmosphereNeutralize, range = 0f..1f, defaultValue = 0.5f) { v -> viewModel.updateParams { it.copy(dehazeAtmosphereNeutralize = v) } }
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Film Blur", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                            SliderRow(label = "Blur Amount", value = effectParams.blurAmount, range = 0f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(blurAmount = v) } }
                                            SwitchRow(label = "Tilt-Shift Mode", checked = effectParams.blurIsTiltShift) { v -> viewModel.updateParams { it.copy(blurIsTiltShift = v) } }
                                            if (effectParams.blurIsTiltShift) {
                                                SliderRow(label = "TS Focus Y", value = effectParams.blurTiltShiftFocus, range = 0f..1f, defaultValue = 0.5f) { v -> viewModel.updateParams { it.copy(blurTiltShiftFocus = v) } }
                                            }
                                        }
                                    }
                                    ToolType.COLOR -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SliderRow(label = "Temperature", value = effectParams.colorTemperature, range = 2000f..12000f, defaultValue = 6000f) { v -> viewModel.updateParams { it.copy(colorTemperature = v) } }
                                            SliderRow(label = "Tint", value = effectParams.colorTint, range = -100f..100f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(colorTint = v) } }
                                            SliderRow(label = "Richness", value = effectParams.colorRichness, range = 0f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(colorRichness = v) } }
                                            SliderRow(label = "Saturation", value = effectParams.colorSaturation, range = 0f..2f, defaultValue = 1f) { v -> viewModel.updateParams { it.copy(colorSaturation = v) } }
                                            SliderRow(label = "SubSat", value = effectParams.colorSubtractiveSat, range = 0f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(colorSubtractiveSat = v) } }
                                        }
                                    }
                                    ToolType.LIGHT -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SliderRow(label = "Exposure", value = effectParams.toneMapExposure, range = -2f..2f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(toneMapExposure = v) } }
                                            SliderRow(label = "Contrast", value = effectParams.toneMapContrast, range = 0.5f..2.0f, defaultValue = 1f) { v -> viewModel.updateParams { it.copy(toneMapContrast = v) } }
                                            SliderRow(label = "Shadows", value = effectParams.toneMapShadows, range = -1f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(toneMapShadows = v) } }
                                            SliderRow(label = "Highlights", value = effectParams.toneMapHighlights, range = -1f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(toneMapHighlights = v) } }
                                            SliderRow(label = "Toe", value = effectParams.toneMapToe, range = -1f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(toneMapToe = v) } }
                                            SliderRow(label = "Shoulder", value = effectParams.toneMapShoulder, range = -1f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(toneMapShoulder = v) } }
                                        }
                                    }
                                    ToolType.SPLIT_TONING -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Shadow H/S", color = Color.Cyan, style = MaterialTheme.typography.labelSmall)
                                                Text("Highlight H/S", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                JoystickPad(label = "Shadows", hue = effectParams.splitToneShadowHue, saturation = effectParams.splitToneShadowSat) { h, s -> 
                                                    viewModel.updateParams { it.copy(splitToneShadowHue = h, splitToneShadowSat = s) }
                                                }
                                                JoystickPad(label = "Highlights", hue = effectParams.splitToneHighlightHue, saturation = effectParams.splitToneHighlightSat) { h, s ->
                                                    viewModel.updateParams { it.copy(splitToneHighlightHue = h, splitToneHighlightSat = s) }
                                                }
                                            }
                                            SliderRow(label = "Balance", value = effectParams.splitToneBalance, range = -1f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(splitToneBalance = v) } }
                                            SliderRow(label = "Impact", value = effectParams.splitToneImpact, range = 0f..1f, defaultValue = 1f) { v -> viewModel.updateParams { it.copy(splitToneImpact = v) } }
                                        }
                                    }
                                    ToolType.HALATION -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SliderRow(label = "Intensity", value = effectParams.halationIntensity, range = 0f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(halationIntensity = v) } }
                                            SliderRow(label = "Threshold", value = effectParams.halationThreshold, range = 0f..1f, defaultValue = 0.8f) { v -> viewModel.updateParams { it.copy(halationThreshold = v) } }
                                            SliderRow(label = "Spread", value = effectParams.halationSpread, range = 0f..1f, defaultValue = 0.5f) { v -> viewModel.updateParams { it.copy(halationSpread = v) } }
                                            SliderRow(label = "Hue", value = effectParams.halationHue, range = 0f..1f, defaultValue = 0.5f) { v -> viewModel.updateParams { it.copy(halationHue = v) } }
                                            SliderRow(label = "Saturation", value = effectParams.halationSaturation, range = 0f..1f, defaultValue = 0.25f) { v -> viewModel.updateParams { it.copy(halationSaturation = v) } }
                                            SwitchRow(label = "Show Mask", checked = effectParams.halationShowMask) { v -> viewModel.updateParams { it.copy(halationShowMask = v) } }
                                        }
                                    }
                                    ToolType.BLOOM -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SliderRow(label = "Intensity", value = effectParams.bloomIntensity, range = 0f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(bloomIntensity = v) } }
                                            SliderRow(label = "Threshold", value = effectParams.bloomThreshold, range = 0f..1f, defaultValue = 0.8f) { v -> viewModel.updateParams { it.copy(bloomThreshold = v) } }
                                            SliderRow(label = "Spread", value = effectParams.bloomSpread, range = 0f..1f, defaultValue = 0.5f) { v -> viewModel.updateParams { it.copy(bloomSpread = v) } }
                                            SliderRow(label = "Opacity", value = effectParams.bloomOpacity, range = 0f..1f, defaultValue = 1.0f) { v -> viewModel.updateParams { it.copy(bloomOpacity = v) } }
                                            SliderRow(label = "Black Haze", value = effectParams.bloomBlackPoint, range = 0f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(bloomBlackPoint = v) } }
                                            SliderRow(label = "Highlight Protection", value = effectParams.bloomHighlightProtection, range = 0f..1f, defaultValue = 1f) { v -> viewModel.updateParams { it.copy(bloomHighlightProtection = v) } }
                                            SwitchRow(label = "Soft Light Blend", checked = effectParams.bloomSoftLight) { v -> viewModel.updateParams { it.copy(bloomSoftLight = v) } }
                                        }
                                    }
                                    ToolType.VIGNETTE -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Vignette", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                            SliderRow(label = "Intensity", value = effectParams.vignetteIntensity, range = -1f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(vignetteIntensity = v) } }
                                            SliderRow(label = "Radius", value = effectParams.vignetteRadius, range = 0f..1f, defaultValue = 0.8f) { v -> viewModel.updateParams { it.copy(vignetteRadius = v) } }
                                            SliderRow(label = "Feather", value = effectParams.vignetteFeather, range = 0f..1f, defaultValue = 0.5f) { v -> viewModel.updateParams { it.copy(vignetteFeather = v) } }
                                        }
                                    }
                                    ToolType.DETAIL -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SliderRow(label = "Grain Intensity", value = effectParams.grainIntensity, range = 0f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(grainIntensity = v) } }
                                            SliderRow(label = "Grain Size", value = effectParams.grainSize, range = 0.1f..3.0f, defaultValue = 1.0f) { v -> viewModel.updateParams { it.copy(grainSize = v) } }
                                            SliderRow(label = "Grain Softness", value = effectParams.grainSoftness, range = 0f..1f, defaultValue = 0.5f) { v -> viewModel.updateParams { it.copy(grainSoftness = v) } }
                                            SliderRow(label = "Clumping", value = effectParams.grainClumpiness, range = 0f..1f, defaultValue = 0f) { v -> viewModel.updateParams { it.copy(grainClumpiness = v) } }
                                            SliderRow(label = "Shadow Floor", value = effectParams.grainShadowCoverage, range = 0f..2.0f, defaultValue = 0.1f) { v -> viewModel.updateParams { it.copy(grainShadowCoverage = v) } }
                                            SliderRow(label = "Highlight Fade", value = effectParams.grainHighlightFade, range = 0.1f..3.0f, defaultValue = 0.95f) { v -> viewModel.updateParams { it.copy(grainHighlightFade = v) } }
                                            SwitchRow(label = "Color Noise", checked = effectParams.grainColorNoise) { v -> viewModel.updateParams { it.copy(grainColorNoise = v) } }
                                            SliderRow(label = "Chroma Intensity", value = effectParams.grainChromaIntensity, range = 0f..1f, defaultValue = 0.5f) { v -> viewModel.updateParams { it.copy(grainChromaIntensity = v) } }
                                        }
                                    }
                                    ToolType.PRESETS -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                if (selectedPresets.isNotEmpty()) {
                                                    IconButton(onClick = { selectedPresets = setOf() }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Cancel Selection", tint = Color.White)
                                                    }
                                                    Text("${selectedPresets.size} Selected", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        var showDeleteConfirm by remember { mutableStateOf(false) }
                                                        IconButton(onClick = { showDeleteConfirm = true }) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                                                        }
                                                        IconButton(onClick = { exportLauncher.launch("presets_export.filmfx") }) {
                                                            Icon(Icons.Default.Upload, contentDescription = "Export Selected", tint = Color.Cyan)
                                                        }
                                                        if (showDeleteConfirm) {
                                                            AlertDialog(
                                                                onDismissRequest = { showDeleteConfirm = false },
                                                                title = { Text("Delete Presets?") },
                                                                text = { Text("Are you sure you want to delete ${selectedPresets.size} presets?") },
                                                                confirmButton = { Button(onClick = { viewModel.bulkDeletePresets(presets.filter { it.id in selectedPresets }); selectedPresets = setOf(); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete") } },
                                                                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    Text("Presets", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) {
                                                            Text("Import", color = Color.Gray)
                                                        }
                                                        
                                                        var showSaveDialog by remember { mutableStateOf(false) }
                                                        TextButton(onClick = { showSaveDialog = true }) {
                                                            Text("Save Current", color = Color.Cyan)
                                                        }
                                                        if (showSaveDialog) {
                                                            var presetName by remember { mutableStateOf("") }
                                                            AlertDialog(
                                                                onDismissRequest = { showSaveDialog = false },
                                                                title = { Text("Save Preset") },
                                                                text = { OutlinedTextField(value = presetName, onValueChange = { presetName = it }, label = { Text("Name") }, singleLine = true) },
                                                                confirmButton = { Button(onClick = { viewModel.savePreset(presetName); showSaveDialog = false }) { Text("Save") } },
                                                                dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(presets.size) { index ->
                                                    val preset = presets[index]
                                                    val isSelected = selectedPresets.contains(preset.id)
                                                    Surface(
                                                        modifier = Modifier.width(100.dp).height(100.dp).run {
                                                            if (selectedPresets.isNotEmpty()) {
                                                                clickable { 
                                                                    selectedPresets = if (isSelected) selectedPresets - preset.id else selectedPresets + preset.id
                                                                }
                                                            } else {
                                                                combinedClickable(
                                                                    onClick = { viewModel.loadPreset(preset) },
                                                                    onLongClick = { selectedPresets = selectedPresets + preset.id }
                                                                )
                                                            }
                                                        },
                                                        color = if (isSelected) Color.DarkGray.copy(alpha = 0.5f) else Color.DarkGray,
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                                        border = if (isSelected) BorderStroke(2.dp, Color.Cyan) else null
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(preset.name, color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }

                    // 2. Thumbnail Ribbon
                    AnimatedVisibility(
                        visible = uiState.isUiVisible && !uiState.isSliderDragging,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            color = Color.Black.copy(alpha = 0.9f),
                            tonalElevation = 12.dp
                        ) {
                            val ribbonScroll = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(ribbonScroll)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ToolNavItem(
                                    "COLOR", 
                                    Icons.Default.Palette, 
                                    uiState.activeTool == ToolType.COLOR,
                                    viewModel.isEffectModified(ToolType.COLOR, effectParams),
                                    effectParams.isColorEnabled,
                                    onClick = {
                                        if (uiState.activeTool == ToolType.COLOR) viewModel.updateUiState { it.copy(isDrawerExpanded = !it.isDrawerExpanded) } 
                                        else { viewModel.setActiveTool(ToolType.COLOR); viewModel.updateUiState { it.copy(isDrawerExpanded = true) } }
                                    },
                                    onLongClick = { viewModel.toggleEffect(ToolType.COLOR) }
                                )
                                ToolNavItem(
                                    "LIGHT", 
                                    Icons.Default.Brightness6, 
                                    uiState.activeTool == ToolType.LIGHT,
                                    viewModel.isEffectModified(ToolType.LIGHT, effectParams),
                                    effectParams.isLightEnabled,
                                    onClick = {
                                        if (uiState.activeTool == ToolType.LIGHT) viewModel.updateUiState { it.copy(isDrawerExpanded = !it.isDrawerExpanded) } 
                                        else { viewModel.setActiveTool(ToolType.LIGHT); viewModel.updateUiState { it.copy(isDrawerExpanded = true) } }
                                    },
                                    onLongClick = { viewModel.toggleEffect(ToolType.LIGHT) }
                                )
                                ToolNavItem(
                                    "ATMOSPHERE", 
                                    Icons.Default.CloudQueue, 
                                    uiState.activeTool == ToolType.ATMOSPHERE,
                                    viewModel.isEffectModified(ToolType.ATMOSPHERE, effectParams),
                                    effectParams.isAtmosphereEnabled,
                                    onClick = {
                                        if (uiState.activeTool == ToolType.ATMOSPHERE) viewModel.updateUiState { it.copy(isDrawerExpanded = !it.isDrawerExpanded) } 
                                        else { viewModel.setActiveTool(ToolType.ATMOSPHERE); viewModel.updateUiState { it.copy(isDrawerExpanded = true) } }
                                    },
                                    onLongClick = { viewModel.toggleEffect(ToolType.ATMOSPHERE) }
                                )
                                ToolNavItem(
                                    "SPLIT TONING", 
                                    Icons.Default.ColorLens, 
                                    uiState.activeTool == ToolType.SPLIT_TONING,
                                    viewModel.isEffectModified(ToolType.SPLIT_TONING, effectParams),
                                    effectParams.isSplitToningEnabled,
                                    onClick = {
                                        if (uiState.activeTool == ToolType.SPLIT_TONING) viewModel.updateUiState { it.copy(isDrawerExpanded = !it.isDrawerExpanded) } 
                                        else { viewModel.setActiveTool(ToolType.SPLIT_TONING); viewModel.updateUiState { it.copy(isDrawerExpanded = true) } }
                                    },
                                    onLongClick = { viewModel.toggleEffect(ToolType.SPLIT_TONING) }
                                )
                                ToolNavItem(
                                    "HALATION", 
                                    Icons.Default.AutoAwesome, 
                                    uiState.activeTool == ToolType.HALATION,
                                    viewModel.isEffectModified(ToolType.HALATION, effectParams),
                                    effectParams.isHalationEnabled,
                                    onClick = {
                                        if (uiState.activeTool == ToolType.HALATION) viewModel.updateUiState { it.copy(isDrawerExpanded = !it.isDrawerExpanded) } 
                                        else { viewModel.setActiveTool(ToolType.HALATION); viewModel.updateUiState { it.copy(isDrawerExpanded = true) } }
                                    },
                                    onLongClick = { viewModel.toggleEffect(ToolType.HALATION) }
                                )
                                ToolNavItem(
                                    "BLOOM", 
                                    Icons.Default.LensBlur, 
                                    uiState.activeTool == ToolType.BLOOM,
                                    viewModel.isEffectModified(ToolType.BLOOM, effectParams),
                                    effectParams.isBloomEnabled,
                                    onClick = {
                                        if (uiState.activeTool == ToolType.BLOOM) viewModel.updateUiState { it.copy(isDrawerExpanded = !it.isDrawerExpanded) } 
                                        else { viewModel.setActiveTool(ToolType.BLOOM); viewModel.updateUiState { it.copy(isDrawerExpanded = true) } }
                                    },
                                    onLongClick = { viewModel.toggleEffect(ToolType.BLOOM) }
                                )
                                ToolNavItem(
                                    "VIGNETTE", 
                                    Icons.Default.RadioButtonUnchecked, 
                                    uiState.activeTool == ToolType.VIGNETTE,
                                    viewModel.isEffectModified(ToolType.VIGNETTE, effectParams),
                                    effectParams.isVignetteEnabled,
                                    onClick = {
                                        if (uiState.activeTool == ToolType.VIGNETTE) viewModel.updateUiState { it.copy(isDrawerExpanded = !it.isDrawerExpanded) } 
                                        else { viewModel.setActiveTool(ToolType.VIGNETTE); viewModel.updateUiState { it.copy(isDrawerExpanded = true) } }
                                    },
                                    onLongClick = { viewModel.toggleEffect(ToolType.VIGNETTE) }
                                )
                                ToolNavItem(
                                    "DETAIL", 
                                    Icons.Default.Grain, 
                                    uiState.activeTool == ToolType.DETAIL,
                                    viewModel.isEffectModified(ToolType.DETAIL, effectParams),
                                    effectParams.isGrainEnabled,
                                    onClick = {
                                        if (uiState.activeTool == ToolType.DETAIL) viewModel.updateUiState { it.copy(isDrawerExpanded = !it.isDrawerExpanded) } 
                                        else { viewModel.setActiveTool(ToolType.DETAIL); viewModel.updateUiState { it.copy(isDrawerExpanded = true) } }
                                    },
                                    onLongClick = { viewModel.toggleEffect(ToolType.DETAIL) }
                                )
                                ToolNavItem(
                                    "PRESETS", 
                                    Icons.Default.Style, 
                                    uiState.activeTool == ToolType.PRESETS,
                                    isModified = false,
                                    isEnabled = true,
                                    onClick = {
                                        if (uiState.activeTool == ToolType.PRESETS) viewModel.updateUiState { it.copy(isDrawerExpanded = !it.isDrawerExpanded) } 
                                        else { viewModel.setActiveTool(ToolType.PRESETS); viewModel.updateUiState { it.copy(isDrawerExpanded = true) } }
                                    },
                                    onLongClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ToolNavItem(name: String, icon: ImageVector, isSelected: Boolean, isModified: Boolean, isEnabled: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon, 
                contentDescription = name, 
                tint = if (isSelected) Color.White else if (isEnabled) Color.Gray else Color.DarkGray.copy(alpha = 0.5f), 
                modifier = Modifier.size(24.dp)
            )
            if (isModified) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color.Cyan, CircleShape)
                        .offset(x = 2.dp, y = (-2).dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name, 
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), 
            color = if (isSelected) Color.White else if (isEnabled) Color.Gray else Color.Gray.copy(alpha = 0.5f), 
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, 
            maxLines = 1
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        Switch(checked = checked, onCheckedChange = { hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCheckedChange(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Cyan))
    }
}

@Composable
fun JoystickPad(label: String, hue: Float, saturation: Float, viewModel: EditorViewModel = viewModel(), onValueChange: (Float, Float) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    var isDraggingThis by remember { mutableStateOf(false) }
    var lastCenteredState by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }
    
    // For relative movement & zoomed pin
    var dragTouchPos by remember { mutableStateOf(Offset.Zero) }
    
    val currentHue by rememberUpdatedState(hue)
    val currentSat by rememberUpdatedState(saturation)
    
    val alpha by animateFloatAsState(
        targetValue = if (uiState.isSliderDragging && !isDraggingThis) 0.1f else 1f,
        label = "JoystickAlpha"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer(alpha = alpha)
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        // HSL Readout (Now above the disk)
        Text(
            text = "H: ${hue.toInt()}°  S: ${(saturation * 100).toInt()}%",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(contentAlignment = Alignment.Center) {
            // Main Joystick Disk
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(brush = Brush.sweepGradient(colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown()
                                val now = System.currentTimeMillis()
                                
                                // Double Tap Reset Detection
                                if (now - lastTapTime < 300) {
                                    onValueChange(0f, 0f)
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lastTapTime = 0L 
                                    // Consume the down so we don't start a drag
                                    down.consume()
                                    continue
                                }
                                lastTapTime = now
                                
                                isDraggingThis = true
                                viewModel.setSliderDragging(true)
                                dragTouchPos = down.position
                                
                                // Store current "virtual" handle position in pixels relative to center
                                // using updatedState to catch the EXACT current values at moment of touch
                                val maxR = 60.dp.toPx()
                                var virtualHandleX = Math.cos(Math.toRadians(currentHue.toDouble())).toFloat() * currentSat * maxR
                                var virtualHandleY = Math.sin(Math.toRadians(currentHue.toDouble())).toFloat() * currentSat * maxR

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    
                                    if (change.pressed) {
                                        val delta = change.position - change.previousPosition
                                        if (delta != Offset.Zero) {
                                            change.consume()
                                            dragTouchPos = change.position

                                            // Apply 40% speed reduction (0.6 sensitivity)
                                            val sensitivity = 0.6f
                                            virtualHandleX += delta.x * sensitivity
                                            virtualHandleY += delta.y * sensitivity

                                            val r = Math.sqrt((virtualHandleX * virtualHandleX + virtualHandleY * virtualHandleY).toDouble()).toFloat()
                                            var sat = (r / maxR).coerceIn(0f, 1f)
                                            
                                            // Snap to center
                                            val isCurrentlyNearCenter = (sat < 0.10f)
                                            if (isCurrentlyNearCenter) {
                                                sat = 0f
                                                if (!lastCenteredState) { 
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    lastCenteredState = true 
                                                }
                                            } else { 
                                                lastCenteredState = false 
                                            }

                                            val rawHue = Math.toDegrees(Math.atan2(virtualHandleY.toDouble(), virtualHandleX.toDouble())).toFloat()
                                            val hueNormalized = if (rawHue < 0) rawHue + 360f else rawHue
                                            
                                            onValueChange(hueNormalized, sat)
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                
                                isDraggingThis = false
                                viewModel.setSliderDragging(false)
                                viewModel.commitState()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Handle Reticle (Circle with center dot)
                val handleX = Math.cos(Math.toRadians(hue.toDouble())).toFloat() * saturation * 45.dp.value
                val handleY = Math.sin(Math.toRadians(hue.toDouble())).toFloat() * saturation * 45.dp.value
                Box(
                    modifier = Modifier
                        .offset(x = handleX.dp, y = handleY.dp)
                        .size(24.dp)
                        // Vibrant Glass Effect Internals
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            // Brightening fill (More intense for "Vibrant" look)
                            drawCircle(
                                color = Color.White.copy(alpha = 0.35f),
                                blendMode = androidx.compose.ui.graphics.BlendMode.Overlay
                            )
                            // Luminous highlights
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                                    center = Offset(size.width * 0.3f, size.height * 0.3f),
                                    radius = size.width * 0.6f
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                            )
                            drawContent()
                        }
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Precision center dot (White)
                    Box(
                        modifier = Modifier
                            .size(2.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, defaultValue: Float, viewModel: EditorViewModel = viewModel(), onValueChange: (Float) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var isDraggingThis by remember { mutableStateOf(false) }
    
    val alpha by animateFloatAsState(targetValue = if (uiState.isSliderDragging && !isDraggingThis) 0.1f else 1f)
    Column(
        modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = alpha).padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
            Text(String.format("%.2f", value), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
        PrecisionSlider(
            value = value,
            valueRange = range,
            defaultValue = defaultValue,
            onValueChange = onValueChange,
            onDraggingChanged = { dragging ->
                isDraggingThis = dragging
                viewModel.setSliderDragging(dragging)
                if(!dragging) viewModel.commitState()
            }
        )
    }
}

fun Modifier.verticalScrollbar(scrollState: ScrollState, width: androidx.compose.ui.unit.Dp = 3.dp): Modifier = drawWithContent {
    drawContent()
    if (scrollState.maxValue > 0 && scrollState.isScrollInProgress) {
        val viewPortHeight = size.height
        val totalHeight = scrollState.maxValue + viewPortHeight
        val indicatorHeight = (viewPortHeight / totalHeight) * viewPortHeight
        val indicatorOffset = (scrollState.value / totalHeight) * viewPortHeight
        drawRoundRect(color = Color.White.copy(alpha = 0.4f), topLeft = Offset(size.width - width.toPx(), indicatorOffset), size = Size(width.toPx(), indicatorHeight), cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2))
    }
}
