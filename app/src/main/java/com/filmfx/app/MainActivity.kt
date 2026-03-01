package com.filmfx.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import com.filmfx.app.engine.EngineContext
import com.filmfx.app.engine.ShaderCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import com.filmfx.app.ui.ProjectsScreen

sealed class Screen {
    object Projects : Screen()
    data class Editor(val imageUri: String? = null, val projectId: Long? = null) : Screen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Task: Immersive Layout - Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Hide system bars (status bar and navigation bar)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        val isInitializing = mutableStateOf(true)
        val progress = mutableStateOf(0f)
        val statusText = mutableStateOf("Starting FilmFX")

        setContent {
            val initializing = isInitializing.value
            val currentProgress = progress.value
            val currentStatus = statusText.value
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Projects) }
            
            androidx.compose.material3.MaterialTheme {
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(), 
                    color = androidx.compose.ui.graphics.Color.Black
                ) {
                    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        when (val screen = currentScreen) {
                            is Screen.Projects -> ProjectsScreen(
                                onNewProject = { uri -> currentScreen = Screen.Editor(imageUri = uri) },
                                onOpenProject = { id -> currentScreen = Screen.Editor(projectId = id) }
                            )
                            is Screen.Editor -> com.filmfx.app.ui.EditorScreen(
                                imageUri = screen.imageUri,
                                projectId = screen.projectId,
                                onNavigateBack = { currentScreen = Screen.Projects }
                            )
                        }

                        if (initializing) {
                            com.filmfx.app.ui.SplashScreen(
                                progress = currentProgress,
                                statusText = currentStatus
                            )
                        }
                    }
                }
            }
        }

        val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("FilmFX", "Initialization error", throwable)
            isInitializing.value = false // Dismiss splash on error to allow user interaction
        }
        
        lifecycleScope.launch(exceptionHandler) {
            withContext(Dispatchers.Default) {
                try {
                    statusText.value = "Initializing Engine"
                    progress.value = 0.1f
                    
                    val engine = EngineContext()
                    engine.initialize()
                    progress.value = 0.4f
                    
                    statusText.value = "Optimizing Shaders"
                    val shaderCache = ShaderCache()
                    shaderCache.precompileAll()
                    progress.value = 0.8f
                    
                    statusText.value = "Ready"
                    progress.value = 1.0f
                    
                    // Small delay for smooth transition
                    kotlinx.coroutines.delay(500)
                } catch (e: Exception) {
                    android.util.Log.e("FilmFX", "Engine initialization failed", e)
                } finally {
                    isInitializing.value = false
                }
            }
        }
    }
}
