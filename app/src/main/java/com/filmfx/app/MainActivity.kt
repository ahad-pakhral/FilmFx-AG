package com.filmfx.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.filmfx.app.engine.EngineContext
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
            
            // For now just exit the splash visual by setting content
            setContent()
        }
    }

    private fun setContent() {
        // Will be replaced by Compose UI in future plans
        setContentView(android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.DKGRAY)
        })
    }
}
