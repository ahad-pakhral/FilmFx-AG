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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import com.filmfx.app.ui.ProjectsScreen

sealed class Screen {
    object Projects : Screen()
    data class Editor(val imageUri: String? = null, val projectId: Long? = null) : Screen()
    object Debugger : Screen()
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
        
        // This activity acts as the splash screen router while the engine initializes
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                // Task 2 & 3: Initialize the rendering engine
                val engine = EngineContext()
                engine.initialize()
                
                // Task 4: Precompile shaders while splash is up
                val shaderCache = ShaderCache()
                shaderCache.precompileAll()
            }
            
            setContent {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Projects) }
                
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(), 
                    color = androidx.compose.ui.graphics.Color.Black
                ) {
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
                        else -> {}
                    }
                }
            }
        }
    }
}
