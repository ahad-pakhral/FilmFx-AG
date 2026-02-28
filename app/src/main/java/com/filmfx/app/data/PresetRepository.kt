package com.filmfx.app.data

import kotlinx.coroutines.flow.Flow

class PresetRepository(private val presetDao: PresetDao) {
    
    val allPresets: Flow<List<Preset>> = presetDao.getAllPresets()

    suspend fun savePresetWithName(name: String, parametersJson: String) {
        val existing = presetDao.getPresetByName(name)
        if (existing != null) {
            presetDao.updatePreset(existing.copy(parametersJson = parametersJson, createdAt = System.currentTimeMillis()))
        } else {
            presetDao.insertPreset(Preset(name = name, parametersJson = parametersJson))
        }
    }

    suspend fun deletePreset(preset: Preset) {
        presetDao.deletePreset(preset)
    }
}
