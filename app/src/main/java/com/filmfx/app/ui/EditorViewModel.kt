package com.filmfx.app.ui

import android.app.Application
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
    val detailVisible: Boolean = true,
    val presetsVisible: Boolean = true,
    val activeTool: ToolType? = null,
    val isDrawerExpanded: Boolean = false,
    val isUiVisible: Boolean = true,
    val showBefore: Boolean = false,
    val importConflicts: List<ImportConflict> = emptyList(),
    val exportProgress: Int? = null,
    val exportError: String? = null
)

data class ImportConflict(
    val name: String,
    val json: String,
    val newName: String = name
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).projectDao()
    private val presetDao = AppDatabase.getDatabase(application).presetDao()
    private val presetRepo = PresetRepository(presetDao)
    
    private var currentProject: Project? = null
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

    init {
        // Initial state pushed as the base for undo
        undoStack.add(EffectParameters.DEFAULT)
    }

    fun setProject(project: Project) {
        currentProject = project
        // Setup initial params if json exists
        if (project.parametersJson.isNotEmpty()) {
            try {
                val params = Json.decodeFromString<EffectParameters>(project.parametersJson)
                _effectParams.value = params
                undoStack.clear()
                redoStack.clear()
                undoStack.add(params)
                updateUndoRedoStates()
            } catch (e: Exception) {
                // Let it be default
            }
        }
    }

    fun updateParams(updater: (EffectParameters) -> EffectParameters) {
        _effectParams.update(updater)
    }

    fun commitState() {
        val currentParams = _effectParams.value
        // Don't push duplicate states
        if (undoStack.isNotEmpty() && undoStack.last() == currentParams) return
        
        undoStack.add(currentParams)
        redoStack.clear()
        
        // Limit history size to 50
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        
        updateUndoRedoStates()
        scheduleSave()
    }

    fun undo() {
        if (undoStack.size > 1) { // leave at least the root state
            val currentState = undoStack.removeLast()
            redoStack.add(currentState)
            
            val previousState = undoStack.last()
            _effectParams.value = previousState
            
            updateUndoRedoStates()
            scheduleSave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeLast()
            undoStack.add(nextState)
            
            _effectParams.value = nextState
            
            updateUndoRedoStates()
            scheduleSave()
        }
    }

    fun resetAll() {
        if (_effectParams.value != EffectParameters.DEFAULT) {
            _effectParams.value = EffectParameters.DEFAULT
            commitState()
        }
    }

    private fun updateUndoRedoStates() {
        canUndo.value = undoStack.size > 1
        canRedo.value = redoStack.isNotEmpty()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1000) // Debounce auto-save
            val updatedJson = Json.encodeToString(_effectParams.value)
            
            if (currentProject == null) {
                val newProj = Project(
                    name = "Untitled Project",
                    originalUri = "",
                    parametersJson = updatedJson
                )
                val newId = dao.insertProject(newProj)
                currentProject = newProj.copy(id = newId)
            } else {
                val updatedProj = currentProject!!.copy(
                    parametersJson = updatedJson,
                    lastModified = System.currentTimeMillis()
                )
                dao.updateProject(updatedProj)
                currentProject = updatedProj
            }
        }
    }

    fun updateUiState(updater: (EditorUiState) -> EditorUiState) {
        _uiState.update(updater)
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
                ToolType.DETAIL -> state.copy(detailVisible = !state.detailVisible)
                ToolType.PRESETS -> state.copy(presetsVisible = !state.presetsVisible)
            }
        }
    }
    
    fun setActiveTool(tool: ToolType?) {
        updateUiState { it.copy(activeTool = tool) }
    }
    
    fun savePreset(name: String) {
        viewModelScope.launch {
            val json = Json.encodeToString(_effectParams.value)
            presetRepo.savePresetWithName(name, json)
        }
    }
    
    fun loadPreset(preset: Preset) {
        try {
            val params = Json.decodeFromString<EffectParameters>(preset.parametersJson)
            _effectParams.value = params
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
                presetRepo.savePresetWithName(name, json)
            } catch (e: Exception) {}
        }
    }

    fun startBulkImport(uris: List<android.net.Uri>, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val conflicts = mutableListOf<ImportConflict>()
            uris.forEach { uri ->
                try {
                    val content = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    if (content != null) {
                        Json.decodeFromString<EffectParameters>(content) // Validate
                        
                        var name = "Imported Preset"
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex >= 0) {
                                    name = cursor.getString(nameIndex).substringBeforeLast(".")
                                }
                            }
                        }
                        
                        val existing = presetDao.getPresetByName(name)
                        if (existing != null) {
                            conflicts.add(ImportConflict(name, content))
                        } else {
                            presetRepo.savePresetWithName(name, content)
                        }
                    }
                } catch (e: Exception) {}
            }
            
            if (conflicts.isNotEmpty()) {
                _uiState.update { it.copy(importConflicts = conflicts) }
            }
        }
    }

    fun resolveConflicts(resolutions: List<ImportConflict>) {
        viewModelScope.launch {
            resolutions.forEach { conflict ->
                presetRepo.savePresetWithName(conflict.newName, conflict.json)
            }
            _uiState.update { it.copy(importConflicts = emptyList()) }
        }
    }

    fun cancelConflicts() {
        _uiState.update { it.copy(importConflicts = emptyList()) }
    }

    fun bulkExportPresets(presetsToExport: List<Preset>, context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val contentResolver = context.contentResolver
            
            presetsToExport.forEach { preset ->
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "${preset.name}.json")
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                    }
                    
                    val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }

                    val uri = contentResolver.insert(downloadsUri, values)
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { out ->
                            out.write(preset.parametersJson.toByteArray())
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    fun exportImage() {
        val project = currentProject ?: return
        if (project.originalUri.isEmpty()) return
        
        val context = getApplication<Application>()
        val workManager = WorkManager.getInstance(context)
        
        val inputData = Data.Builder()
            .putString(ExportWorker.KEY_INPUT_URI, project.originalUri)
            .putString(ExportWorker.KEY_PARAMS, project.parametersJson)
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
        
        // Observe progress via LiveData (using observeForever since ViewModel doesn't have LifecycleOwner, or use flow)
        // For simplicity and to avoid memory leaks, we can let UI handle the observation if we expose ID,
        // or we can collect it safely in viewModelScope using Flow extension
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
        val project = currentProject ?: return
        WorkManager.getInstance(getApplication()).cancelUniqueWork("export_${project.id}")
    }
}
