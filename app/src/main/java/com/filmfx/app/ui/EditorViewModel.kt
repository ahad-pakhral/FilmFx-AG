package com.filmfx.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.filmfx.app.data.EffectParameters
import com.filmfx.app.data.Project
import com.filmfx.app.data.AppDatabase
import com.filmfx.app.data.Preset
import com.filmfx.app.data.PresetRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.os.Environment
import android.content.Context
import android.widget.Toast
import androidx.work.*
import com.filmfx.app.engine.export.ExportWorker

data class EditorUiState(
    val colorVisible: Boolean = true,
    val atmosphereVisible: Boolean = true,
    val lightVisible: Boolean = true,
    val splitToningVisible: Boolean = true,
    val halationVisible: Boolean = true,
    val bloomVisible: Boolean = true,
    val vignetteVisible: Boolean = true,
    val grainVisible: Boolean = true,
    val presetsVisible: Boolean = true,
    val activeTool: ToolType? = null,
    val isDrawerExpanded: Boolean = false,
    val isUiVisible: Boolean = true,
    val showBefore: Boolean = false,
    val exportProgress: Int? = null,
    val exportError: String? = null,
    val isSliderDragging: Boolean = false
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).projectDao()
    private val presetDao = AppDatabase.getDatabase(application).presetDao()
    private val presetRepo = PresetRepository(presetDao)
    
    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()
    private var saveJob: Job? = null
    
    val presets: StateFlow<List<Preset>> = presetRepo.allPresets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _effectParams = MutableStateFlow(EffectParameters.DEFAULT)
    val effectParams: StateFlow<EffectParameters> = _effectParams.asStateFlow()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<EffectParameters>()
    private val redoStack = mutableListOf<EffectParameters>()
    
    val canUndo = MutableStateFlow(false)
    val canRedo = MutableStateFlow(false)
    val undoText = MutableStateFlow("")
    val redoText = MutableStateFlow("")

    init {
        // Initial state pushed as the base for undo
        undoStack.add(EffectParameters.DEFAULT)
    }

    fun loadProject(id: Long) {
        viewModelScope.launch {
            val project = dao.getProjectById(id)
            if (project != null) {
                _currentProject.value = project
                if (project.parametersJson.isNotEmpty()) {
                    try {
                        val params = Json.decodeFromString<EffectParameters>(project.parametersJson)
                        _effectParams.value = params
                        undoStack.clear()
                        redoStack.clear()
                        undoStack.add(params)
                        updateUndoRedoStates()
                    } catch (e: Exception) {
                        resetAll()
                    }
                } else {
                    resetAll()
                }
            }
        }
    }

    fun setOriginalUri(uri: String) {
        // Only set URI if it's a new project or different URI. Reset state for new image.
        if (_currentProject.value?.originalUri != uri) {
            resetAll() // Clear previous edits for the new image
        }
        if (_currentProject.value == null) {
            val newProj = Project(
                name = "Untitled Project",
                originalUri = uri,
                parametersJson = Json.encodeToString(_effectParams.value)
            )
            viewModelScope.launch {
                val newId = dao.insertProject(newProj)
                _currentProject.value = newProj.copy(id = newId)
            }
        } else {
            _currentProject.value = _currentProject.value?.copy(originalUri = uri)
            scheduleSave()
        }
    }

    fun clearCurrentProject() {
        _currentProject.value = null
    }

    fun updateParams(updater: (EffectParameters) -> EffectParameters) {
        _effectParams.update(updater)
    }

    fun commitState() {
        val currentParams = _effectParams.value
        
        // Don't push duplicate states (checking everything except transforms)
        if (undoStack.isNotEmpty()) {
            val last = undoStack.last()
            val isDuplicate = last.copy(
                transformScale = currentParams.transformScale,
                transformOffsetX = currentParams.transformOffsetX,
                transformOffsetY = currentParams.transformOffsetY,
                transformRotation = currentParams.transformRotation
            ) == currentParams
            if (isDuplicate) return
        }
        
        undoStack.add(currentParams)
        redoStack.clear()
        
        // Limit history size to 50
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        
        // Defer non-critical UI string generation and DB writes
        viewModelScope.launch {
            updateUndoRedoStates()
            scheduleSave()
        }
    }

    fun undo() {
        if (undoStack.size > 1) { // leave at least the root state
            val currentState = undoStack.removeLast()
            redoStack.add(currentState)
            
            val previousState = undoStack.last()
            
            // Preserve current transforms
            val currentScale = _effectParams.value.transformScale
            val currentOffsetX = _effectParams.value.transformOffsetX
            val currentOffsetY = _effectParams.value.transformOffsetY
            val currentRotation = _effectParams.value.transformRotation
            
            _effectParams.value = previousState.copy(
                transformScale = currentScale,
                transformOffsetX = currentOffsetX,
                transformOffsetY = currentOffsetY,
                transformRotation = currentRotation
            )
            
            updateUndoRedoStates()
            scheduleSave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeLast()
            undoStack.add(nextState)
            
            // Preserve current transforms
            val currentScale = _effectParams.value.transformScale
            val currentOffsetX = _effectParams.value.transformOffsetX
            val currentOffsetY = _effectParams.value.transformOffsetY
            val currentRotation = _effectParams.value.transformRotation
            
            _effectParams.value = nextState.copy(
                transformScale = currentScale,
                transformOffsetX = currentOffsetX,
                transformOffsetY = currentOffsetY,
                transformRotation = currentRotation
            )
            
            updateUndoRedoStates()
            scheduleSave()
        }
    }

    fun resetAll() {
        if (_effectParams.value != EffectParameters.DEFAULT) {
            val currentScale = _effectParams.value.transformScale
            val currentOffsetX = _effectParams.value.transformOffsetX
            val currentOffsetY = _effectParams.value.transformOffsetY
            val currentRotation = _effectParams.value.transformRotation
            
            _effectParams.value = EffectParameters.DEFAULT.copy(
                transformScale = currentScale,
                transformOffsetX = currentOffsetX,
                transformOffsetY = currentOffsetY,
                transformRotation = currentRotation
            )
            commitState()
        }
    }

    private fun getDiffText(old: EffectParameters, new: EffectParameters): String {
        // Optimization: Check most common changed fields first (Exposure, Contrast, etc.)
        if (!isNear(old.toneMapExposure, new.toneMapExposure)) return "Exposure"
        if (!isNear(old.toneMapContrast, new.toneMapContrast)) return "Contrast"
        if (!isNear(old.colorTemperature, new.colorTemperature)) return "Temp"
        if (!isNear(old.colorTint, new.colorTint)) return "Tint"
        if (!isNear(old.grainIntensity, new.grainIntensity)) return "Grain"
        if (!isNear(old.splitToneBalance, new.splitToneBalance)) return "SplitTone"
        if (!isNear(old.halationIntensity, new.halationIntensity)) return "Halation"
        if (!isNear(old.bloomIntensity, new.bloomIntensity)) return "Bloom"
        if (!isNear(old.dehazeAmount, new.dehazeAmount)) return "Dehaze"
        if (old.isColorEnabled != new.isColorEnabled) return "Color On/Off"
        if (old.isGrainEnabled != new.isGrainEnabled) return "Grain On/Off"
        return "Edit"
    }

    private fun updateUndoRedoStates() {
        canUndo.value = undoStack.size > 1
        canRedo.value = redoStack.isNotEmpty()
        
        if (undoStack.size > 1) {
            undoText.value = getDiffText(undoStack[undoStack.size-2], undoStack.last())
        }
        if (redoStack.isNotEmpty()) {
            redoText.value = getDiffText(undoStack.last(), redoStack.last())
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1000) // Debounce saves
            _currentProject.value?.let { proj ->
                val updatedProj = proj.copy(
                    parametersJson = Json.encodeToString(_effectParams.value),
                    lastModified = System.currentTimeMillis()
                )
                dao.updateProject(updatedProj)
                _currentProject.value = updatedProj
                
                // Regenerate thumbnail on Save if needed
                // generateThumbnail(updatedProj)
            }
        }
    }

    fun toggleEffect(tool: ToolType) {
        updateParams { current ->
            when (tool) {
                ToolType.COLOR -> current.copy(isColorEnabled = !current.isColorEnabled)
                ToolType.LIGHT -> current.copy(isLightEnabled = !current.isLightEnabled)
                ToolType.ATMOSPHERE -> current.copy(isAtmosphereEnabled = !current.isAtmosphereEnabled)
                ToolType.SPLIT_TONING -> current.copy(isSplitToningEnabled = !current.isSplitToningEnabled)
                ToolType.HALATION -> current.copy(isHalationEnabled = !current.isHalationEnabled)
                ToolType.BLOOM -> current.copy(isBloomEnabled = !current.isBloomEnabled)
                ToolType.VIGNETTE -> current.copy(isVignetteEnabled = !current.isVignetteEnabled)
                ToolType.DETAIL -> current.copy(isGrainEnabled = !current.isGrainEnabled)
                else -> current
            }
        }
        commitState()
    }

    private fun isNear(a: Float, b: Float) = Math.abs(a - b) < 0.001f

    fun isEffectModified(tool: ToolType, p: EffectParameters): Boolean {
        val d = EffectParameters.DEFAULT
        return when (tool) {
            ToolType.COLOR -> !isNear(p.colorTemperature, d.colorTemperature) || !isNear(p.colorTint, d.colorTint) || !isNear(p.colorSaturation, d.colorSaturation) || !isNear(p.colorRichness, d.colorRichness) || !isNear(p.colorSubtractiveSat, d.colorSubtractiveSat)
            ToolType.LIGHT -> !isNear(p.toneMapExposure, d.toneMapExposure) || !isNear(p.toneMapContrast, d.toneMapContrast) || !isNear(p.toneMapShoulder, d.toneMapShoulder) || !isNear(p.toneMapToe, d.toneMapToe) || !isNear(p.toneMapHighlights, d.toneMapHighlights) || !isNear(p.toneMapShadows, d.toneMapShadows)
            ToolType.ATMOSPHERE -> !isNear(p.dehazeAmount, d.dehazeAmount) || !isNear(p.dehazeAtmosphereNeutralize, d.dehazeAtmosphereNeutralize) || !isNear(p.blurAmount, d.blurAmount)
            ToolType.SPLIT_TONING -> !isNear(p.splitToneShadowHue, d.splitToneShadowHue) || !isNear(p.splitToneShadowSat, d.splitToneShadowSat) || !isNear(p.splitToneHighlightHue, d.splitToneHighlightHue) || !isNear(p.splitToneHighlightSat, d.splitToneHighlightSat) || !isNear(p.splitToneBalance, d.splitToneBalance) || !isNear(p.splitToneImpact, d.splitToneImpact)
            ToolType.HALATION -> !isNear(p.halationIntensity, d.halationIntensity) || !isNear(p.halationThreshold, d.halationThreshold) || !isNear(p.halationSpread, d.halationSpread) || !isNear(p.halationHue, d.halationHue) || !isNear(p.halationSaturation, d.halationSaturation)
            ToolType.BLOOM -> !isNear(p.bloomIntensity, d.bloomIntensity) || !isNear(p.bloomThreshold, d.bloomThreshold) || !isNear(p.bloomSpread, d.bloomSpread) || !isNear(p.bloomOpacity, d.bloomOpacity) || !isNear(p.bloomBlackPoint, d.bloomBlackPoint) || !isNear(p.bloomHighlightProtection, d.bloomHighlightProtection)
            ToolType.VIGNETTE -> !isNear(p.vignetteIntensity, d.vignetteIntensity) || !isNear(p.vignetteRadius, d.vignetteRadius) || !isNear(p.vignetteFeather, d.vignetteFeather)
            ToolType.DETAIL -> !isNear(p.grainIntensity, d.grainIntensity) || !isNear(p.grainSize, d.grainSize) || !isNear(p.grainSoftness, d.grainSoftness) || !isNear(p.grainClumpiness, d.grainClumpiness) || !isNear(p.grainShadowCoverage, d.grainShadowCoverage) || !isNear(p.grainHighlightFade, d.grainHighlightFade) || !isNear(p.grainChromaIntensity, d.grainChromaIntensity)
            else -> false
        }
    }

    @Deprecated("Use version with parameters for tracking", ReplaceWith("isEffectModified(tool, effectParams)"))
    fun isEffectModified(tool: ToolType): Boolean = isEffectModified(tool, _effectParams.value)

    fun isEffectEnabled(tool: ToolType): Boolean {
        val p = _effectParams.value
        return when (tool) {
            ToolType.COLOR -> p.isColorEnabled
            ToolType.LIGHT -> p.isLightEnabled
            ToolType.ATMOSPHERE -> p.isAtmosphereEnabled
            ToolType.SPLIT_TONING -> p.isSplitToningEnabled
            ToolType.HALATION -> p.isHalationEnabled
            ToolType.BLOOM -> p.isBloomEnabled
            ToolType.VIGNETTE -> p.isVignetteEnabled
            ToolType.DETAIL -> p.isGrainEnabled
            else -> true
        }
    }

    fun updateUiState(updater: (EditorUiState) -> EditorUiState) {
        _uiState.update(updater)
    }

    fun setSliderDragging(dragging: Boolean) {
        updateUiState { it.copy(isSliderDragging = dragging) }
    }

    fun toggleToolVisibility(tool: ToolType) {
        updateUiState { state ->
            when (tool) {
                ToolType.COLOR -> state.copy(colorVisible = !state.colorVisible)
                ToolType.ATMOSPHERE -> state.copy(atmosphereVisible = !state.atmosphereVisible)
                ToolType.LIGHT -> state.copy(lightVisible = !state.lightVisible)
                ToolType.SPLIT_TONING -> state.copy(splitToningVisible = !state.splitToningVisible)
                ToolType.HALATION -> state.copy(halationVisible = !state.halationVisible)
                ToolType.BLOOM -> state.copy(bloomVisible = !state.bloomVisible)
                ToolType.VIGNETTE -> state.copy(vignetteVisible = !state.vignetteVisible)
                ToolType.DETAIL -> state.copy(grainVisible = !state.grainVisible)
                ToolType.PRESETS -> state.copy(presetsVisible = !state.presetsVisible)
            }
        }
    }
    
    fun setActiveTool(tool: ToolType?) {
        updateUiState { it.copy(activeTool = tool) }
    }
    
    fun savePreset(name: String) {
        viewModelScope.launch {
            // Strip spatial transform data before saving to ensure presets are "style-only"
            val styleOnlyParams = _effectParams.value.copy(
                transformScale = 1.0f,
                transformOffsetX = 0.0f,
                transformOffsetY = 0.0f,
                transformRotation = 0.0f
            )
            val json = Json.encodeToString(styleOnlyParams)
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var uniqueName = name
                var counter = 2
                while(presetDao.getPresetByName(uniqueName) != null) {
                    uniqueName = "$name-$counter"
                    counter++
                }
                presetRepo.savePresetWithName(uniqueName, json)
            }
        }
    }
    
    fun loadPreset(preset: Preset) {
        try {
            val loadedParams = Json.decodeFromString<EffectParameters>(preset.parametersJson)
            // Merge loaded style with CURRENT spatial transform parameters
            val mergedParams = loadedParams.copy(
                transformScale = _effectParams.value.transformScale,
                transformOffsetX = _effectParams.value.transformOffsetX,
                transformOffsetY = _effectParams.value.transformOffsetY,
                transformRotation = _effectParams.value.transformRotation
            )
            _effectParams.value = mergedParams
            commitState()
        } catch (e: Exception) {}
    }
    
    fun deletePreset(preset: Preset) {
        viewModelScope.launch {
            presetRepo.deletePreset(preset)
        }
    }
    
    fun importPresetJson(name: String, json: String) {
        viewModelScope.launch {
            // Validate JSON before saving
            try {
                Json.decodeFromString<EffectParameters>(json)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var uniqueName = name
                    var counter = 2
                    while(presetDao.getPresetByName(uniqueName) != null) {
                        uniqueName = "$name-$counter"
                        counter++
                    }
                    presetRepo.savePresetWithName(uniqueName, json)
                }
            } catch (e: Exception) {}
        }
    }

    fun importPresets(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                if (content != null) {
                    try {
                        // 1. Try as Bundle
                        val bundle = Json.decodeFromString<com.filmfx.app.data.PresetBundle>(content)
                        bundle.presets.forEach { preset ->
                            savePresetWithConflictResolution(preset.name, preset.parametersJson)
                        }
                    } catch (e: Exception) {
                        // 2. Try as Single Preset
                        try {
                            val params = Json.decodeFromString<EffectParameters>(content)
                            var name = "Imported Preset"
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex >= 0) {
                                        name = cursor.getString(nameIndex).substringBeforeLast(".")
                                    }
                                }
                            }
                            savePresetWithConflictResolution(name, content)
                        } catch (e2: Exception) {
                            Log.e("FilmFX", "Failed to parse import content", e2)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FilmFX", "Import failed", e)
            }
        }
    }

    private suspend fun savePresetWithConflictResolution(name: String, json: String) {
        var uniqueName = name
        var counter = 2
        while (presetDao.getPresetByName(uniqueName) != null) {
            uniqueName = "$name-$counter"
            counter++
        }
        presetRepo.savePresetWithName(uniqueName, json)
    }

    fun exportPresetsToBundle(presetsToExport: List<Preset>, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val bundle = com.filmfx.app.data.PresetBundle(presets = presetsToExport)
                val json = Json.encodeToString(bundle)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("FilmFX", "Export failed", e)
            }
        }
    }

    fun bulkDeletePresets(presetsToDelete: List<Preset>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            presetsToDelete.forEach {
                presetRepo.deletePreset(it)
            }
        }
    }

    fun exportImage() {
        Log.d("FilmFX", "exportImage() called")
        val project = currentProject.value
        if (project == null) {
            Log.d("FilmFX", "exportImage() aborted: currentProject is null")
            return
        }
        if (project.originalUri.isEmpty()) {
            Log.d("FilmFX", "exportImage() aborted: originalUri is empty")
            return
        }
        
        Log.d("FilmFX", "exportImage() proceeding with URI: ${project.originalUri}")
        
        val context = getApplication<Application>()
        val workManager = WorkManager.getInstance(context)
        
        val inputData = Data.Builder()
            .putString(ExportWorker.KEY_INPUT_URI, project.originalUri)
            .putString(ExportWorker.KEY_PARAMS, Json.encodeToString(_effectParams.value))
            .build()
            
        val exportRequest = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
            
        workManager.enqueueUniqueWork(
            "export_${project.id}",
            ExistingWorkPolicy.REPLACE,
            exportRequest
        )
        
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(exportRequest.id).collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt(ExportWorker.KEY_PROGRESS, 0)
                            _uiState.update { it.copy(exportProgress = progress, exportError = null) }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _uiState.update { it.copy(exportProgress = 100, exportError = null) }
                            delay(2000)
                            _uiState.update { it.copy(exportProgress = null) }
                        }
                        WorkInfo.State.FAILED -> {
                            val error = workInfo.outputData.getString(ExportWorker.KEY_ERROR) ?: "Export failed due to an unknown error."
                            _uiState.update { it.copy(exportProgress = null, exportError = error) }
                        }
                        WorkInfo.State.CANCELLED -> {
                            _uiState.update { it.copy(exportProgress = null) }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    
    fun clearExportError() {
        _uiState.update { it.copy(exportError = null) }
    }
    
    fun cancelExport() {
        val project = currentProject.value ?: return
        WorkManager.getInstance(getApplication()).cancelUniqueWork("export_${project.id}")
    }
}
