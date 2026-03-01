package com.filmfx.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val originalUri: String,
    val parametersJson: String,
    val previewUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

@Serializable
data class EffectParameters(
    // Split Toning
    val splitToneShadowHue: Float = 0f,
    val splitToneShadowSat: Float = 0f,
    val splitToneHighlightHue: Float = 0f,
    val splitToneHighlightSat: Float = 0f,
    val splitToneBalance: Float = 0f,
    val splitToneImpact: Float = 1.0f,

    // Tone Map (Light)
    val toneMapExposure: Float = 0f,
    val toneMapContrast: Float = 1f,
    val toneMapShoulder: Float = 0f,
    val toneMapToe: Float = 0f,
    val toneMapHighlights: Float = 0f,
    val toneMapShadows: Float = 0f,

    // Halation
    val halationIntensity: Float = 0f,
    val halationThreshold: Float = 0.8f,
    val halationSpread: Float = 0.5f,
    val halationHue: Float = 0.5f,
    val halationSaturation: Float = 0.25f,
    val halationShowMask: Boolean = false,

    // Bloom
    val bloomIntensity: Float = 0f,
    val bloomThreshold: Float = 0.8f,
    val bloomSpread: Float = 0.5f,
    val bloomOpacity: Float = 1.0f,
    val bloomBlackPoint: Float = 0.0f,
    val bloomHighlightProtection: Float = 1.0f,
    val bloomSoftLight: Boolean = false,

    // Detail (Grain)
    val grainIntensity: Float = 0f,
    val grainSize: Float = 1.0f,
    val grainRoughness: Float = 0.5f,
    val grainSoftness: Float = 0.5f,
    val grainClumpiness: Float = 0.0f,
    val grainShadowCoverage: Float = 0.1f,
    val grainHighlightFade: Float = 0.95f,
    val grainColorNoise: Boolean = true,
    val grainChromaIntensity: Float = 0.5f,

    // Vignette
    val vignetteIntensity: Float = 0f,
    val vignetteRadius: Float = 0.8f,
    val vignetteFeather: Float = 0.5f,
    val vignetteCenterX: Float = 0.5f,
    val vignetteCenterY: Float = 0.5f,
    val vignetteRoundness: Float = 1.0f,
    val vignetteSlope: Float = 1.5f,

    // Color (Phase 6.1)
    val colorTemperature: Float = 6000f, // Kelvin: 2000 to 12000
    val colorTint: Float = 0f, // -100 to 100
    val colorSaturation: Float = 1.0f, // 0.0 to 2.0
    val colorRichness: Float = 0.0f, // 0.0 to 1.0
    val colorSubtractiveSat: Float = 0.0f, // 0.0 to 1.0

    // Atmosphere (Phase 6.1)
    val dehazeAmount: Float = 0.0f, // -1.0 to 1.0 (Negative = Add Haze)
    val dehazeAtmosphereNeutralize: Float = 0.5f, // 0.0 to 1.0
    val blurAmount: Float = 0.0f, // 0.0 to 1.0
    val blurIsTiltShift: Boolean = false,
    val blurTiltShiftFocus: Float = 0.5f, // 0.0 to 1.0 (Y axis focus point)
    
    // Toggles
    val isColorEnabled: Boolean = true,
    val isLightEnabled: Boolean = true,
    val isAtmosphereEnabled: Boolean = true,
    val isSplitToningEnabled: Boolean = true,
    val isHalationEnabled: Boolean = true,
    val isBloomEnabled: Boolean = true,
    val isVignetteEnabled: Boolean = true,
    val isGrainEnabled: Boolean = true,

    // Spatial (Canvas Alignment)
    val transformScale: Float = 1.0f,
    val transformOffsetX: Float = 0.0f,
    val transformOffsetY: Float = 0.0f,
    val transformRotation: Float = 0.0f
) {
    companion object {
        val DEFAULT = EffectParameters()
    }
}
