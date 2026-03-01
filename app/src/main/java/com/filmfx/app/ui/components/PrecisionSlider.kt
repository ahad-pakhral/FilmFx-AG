package com.filmfx.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import com.filmfx.app.utils.HapticManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs

@Composable
fun PrecisionSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    defaultValue: Float = valueRange.start + (valueRange.endInclusive - valueRange.start) / 2f,
    trackBrush: androidx.compose.ui.graphics.Brush? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    onDraggingChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val haptic = remember { HapticManager(context, hapticFeedback) }
    val density = LocalDensity.current.density
    val touchSlop = LocalViewConfiguration.current.touchSlop
    
    var isDragging by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }
    
    val currentActualValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentOnDraggingChanged by rememberUpdatedState(onDraggingChanged)
    
    val valueRangeSpace = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / valueRangeSpace).coerceIn(0f, 1f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(valueRange, defaultValue) {
                awaitEachGesture {
                    val downEvent = awaitFirstDown()
                    val downTime = System.currentTimeMillis()
                    val startPos = downEvent.position
                    
                    var sliderActive = false
                    var totalDragDist = 0f
                    var currentInternalValue = currentActualValue
                    var lastHapticValue = currentActualValue
                    val sliderWidth = size.width.toFloat()
                    
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        
                        val dx = change.positionChange().x
                        val dy = abs(change.position.y - (size.height / 2f)) // Vertical distance from center track
                        
                        // 1. Touch Slop Check (Lock axis to determine if scrolling vertically or adjusting slider)
                        if (!sliderActive) {
                            val dist = (change.position - startPos).getDistance()
                            if (dist > touchSlop) {
                                val dxTotal = abs(change.position.x - startPos.x)
                                val dyTotal = abs(change.position.y - startPos.y)
                                if (dxTotal > dyTotal * 0.5f) { // Bias towards horizontal drag for sliders
                                    sliderActive = true
                                    isDragging = true
                                    currentOnDraggingChanged(true)
                                } else {
                                    // Vertical drag detected. Do NOT consume. Break to let vertical scroll container handle it.
                                    break
                                }
                            }
                        }
                        
                        // 2. Process Slider Drag
                        if (sliderActive && change.pressed && change.positionChange() != Offset.Zero) {
                            change.consume()
                            totalDragDist += change.positionChange().getDistance()
                            
                            // Precision Logic: Slow-Drag based on vertical distance
                            val threshold = 40f * density // Requires a larger pull away (approx 15mm depending on screen)
                            val precisionFactor = if (dy <= threshold) {
                                1f
                            } else {
                                val extraDy = dy - threshold
                                1f / (1f + (extraDy / (100f * density)))
                            }
                            
                            val deltaFraction = (dx / sliderWidth) * precisionFactor
                            val deltaValue = deltaFraction * valueRangeSpace
                            
                            currentInternalValue = (currentInternalValue + deltaValue).coerceIn(valueRange)
                            
                            // Snapping logic
                            val snapZone = valueRangeSpace * 0.02f
                            val isNearDefault = abs(currentInternalValue - defaultValue) < snapZone
                            
                            val finalValueToEmit = if (isNearDefault) {
                                if (abs(lastHapticValue - defaultValue) > 0.001f) {
                                    haptic.tick() // Tick
                                    lastHapticValue = defaultValue
                                }
                                defaultValue
                            } else {
                                lastHapticValue = currentInternalValue
                                currentInternalValue
                            }
                            
                            currentOnValueChange(finalValueToEmit)
                        }
                    } while (event.changes.any { it.pressed })
                    
                    // 3. Cleanup & Tap Detection
                    if (isDragging) {
                        isDragging = false
                        currentOnDraggingChanged(false)
                        currentOnValueChangeFinished?.invoke()
                        lastTapTime = 0L // Reset double-tap sequence if dragging occurred
                    } else {
                        // Check if it was a quick tap
                        val upTime = System.currentTimeMillis()
                        if (upTime - downTime < 300) {
                            if (downTime - lastTapTime < 300) {
                                // Double tap confirmed
                                haptic.heavyTick()
                                currentOnValueChange(defaultValue)
                                currentOnValueChangeFinished?.invoke()
                                lastTapTime = 0L
                            } else {
                                lastTapTime = downTime
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 2.dp.toPx()
            val thumbRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()
            
            val centerY = size.height / 2f
            val startX = thumbRadius
            val endX = size.width - thumbRadius
            val trackWidth = endX - startX
            
            // Draw background track
            if (trackBrush != null) {
                drawLine(
                    brush = trackBrush,
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = trackHeight
                )
            } else {
                drawLine(
                    color = Color.DarkGray,
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = trackHeight
                )
            }
            
            val thumbPx = startX + (fraction * trackWidth)
            val defaultFraction = ((defaultValue - valueRange.start) / valueRangeSpace).coerceIn(0f, 1f)
            val defaultPx = startX + (defaultFraction * trackWidth)
            
            // Draw active track from default to current value
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(defaultPx, centerY),
                end = Offset(thumbPx, centerY),
                strokeWidth = trackHeight
            )
            
            // Draw default tick marker
            drawLine(
                color = Color.Gray,
                start = Offset(defaultPx, centerY - 4.dp.toPx()),
                end = Offset(defaultPx, centerY + 4.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
            
            // Draw thumb
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(thumbPx, centerY)
            )
        }
    }
}
