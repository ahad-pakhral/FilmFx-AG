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
    val lastModified: Long = System.currentTimeMillis()
)

@Serializable
data class EffectParameters(
    val splitToneShadowHue: Float = 0f,
    val splitToneShadowSat: Float = 0f,
    val splitToneHighlightHue: Float = 0f,
    val splitToneHighlightSat: Float = 0f,
    val splitToneBalance: Float = 0f,
    val toneMapIntensity: Float = 0.5f,
    val toneMapCurvePoint: Float = 0.5f,
    val toneMapShadows: Float = 0f,
    val toneMapHighlights: Float = 0f,
    val toneMapExposure: Float = 0f,
    val toneMapContrast: Float = 1f,
    val halationIntensity: Float = 0.5f,
    val halationTolerance: Float = 0.2f,
    val halationBlurRadius: Float = 2f,
    val grainIntensity: Float = 0.1f,
    val grainSize: Float = 1.0f,
    val grainRoughness: Float = 0.5f,
    val vignetteIntensity: Float = 0f,
    val vignetteRadius: Float = 0.75f,
    val vignetteFeather: Float = 0.5f,
    val basicBrightness: Float = 0f,
    val basicSaturation: Float = 1f,
    val basicWarmth: Float = 0f,
    val basicTint: Float = 0f
) {
    companion object {
        val DEFAULT = EffectParameters()
    }
}
