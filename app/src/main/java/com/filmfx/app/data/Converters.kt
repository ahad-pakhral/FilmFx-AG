package com.filmfx.app.data

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromEffectParameters(parameters: EffectParameters): String {
        return json.encodeToString(parameters)
    }

    @TypeConverter
    fun toEffectParameters(jsonString: String): EffectParameters {
        return try {
            json.decodeFromString<EffectParameters>(jsonString)
        } catch (e: Exception) {
            EffectParameters.DEFAULT
        }
    }
}
