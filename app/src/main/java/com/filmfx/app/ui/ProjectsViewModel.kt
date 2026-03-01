package com.filmfx.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filmfx.app.data.AppDatabase
import com.filmfx.app.data.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).projectDao()

    private val _isLoading = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val projects: StateFlow<List<Project>> = dao.getAllProjects()
        .onEach { _isLoading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            cleanupPreview(project)
            dao.deleteProject(project)
        }
    }

    fun bulkDeleteProjects(projectsToDelete: List<Project>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            projectsToDelete.forEach {
                cleanupPreview(it)
                dao.deleteProject(it)
            }
        }
    }

    private fun cleanupPreview(project: Project) {
        try {
            project.previewUri?.let { uriStr ->
                val uri = android.net.Uri.parse(uriStr)
                if (uri.scheme == "file") {
                    val file = java.io.File(uri.path ?: "")
                    if (file.exists()) file.delete()
                }
            }
        } catch (e: Exception) {}
    }
}
