package com.filmfx.app.data

import kotlinx.serialization.Serializable

@Serializable
data class PresetBundle(
    val version: Int = 1,
    val presets: List<Preset>
)
