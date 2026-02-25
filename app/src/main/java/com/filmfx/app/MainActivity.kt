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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // This activity acts as the splash screen router while the engine initializes
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                // Task 2 & 3: Initialize the rendering engine
                val engine = EngineContext()
                engine.initialize()
                // Offscreen setup
                engine.setupOffscreenFbo(1920, 1080)
                
                // Task 4: Precompile shaders while splash is up
                val shaderCache = ShaderCache()
                shaderCache.precompileAll()
                
                // TODO: Store engine and cache globally or pass to next screen
                
                // Once initialized, we can transition to the real UI
            }
            
            // We transition to EditorActivity or similar (to be implemented)
            setTheme(R.style.Theme_FilmFX)
            
            setContent {
                com.filmfx.app.ui.EditorScreen()
            }
        }
    }
}
