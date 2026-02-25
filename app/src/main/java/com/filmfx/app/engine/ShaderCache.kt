package com.filmfx.app.engine

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import com.filmfx.app.engine.filters.LinearizeFilter
import com.filmfx.app.engine.filters.ToneMapFilter
import android.util.Log

/**
 * Compiles and caches shaders during splash screen to avoid stutter 
 * when the UI becomes active.
 */
class ShaderCache {
    private val precompiledFilters = mutableMapOf<Class<out GPUImageFilter>, GPUImageFilter>()

    fun precompileAll() {
        Log.d("ShaderCache", "Starting shader pre-compilation...")
        val start = System.currentTimeMillis()
        
        precompile(LinearizeFilter::class.java) { LinearizeFilter() }
        precompile(ToneMapFilter::class.java) { ToneMapFilter() }
        // Future shaders (grain, halation, bloom) will be pre-compiled here
        
        val end = System.currentTimeMillis()
        Log.d("ShaderCache", "Finished pre-compilation in ${end - start}ms")
    }

    private fun <T : GPUImageFilter> precompile(clazz: Class<T>, factory: () -> T) {
        val filter = factory()
        filter.init() // This is safe because EngineContext is already current on this thread
        precompiledFilters[clazz] = filter
    }

    fun <T : GPUImageFilter> getFilter(clazz: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return (precompiledFilters[clazz] as? T) ?: throw IllegalStateException("Filter not pre-compiled: ${clazz.simpleName}")
    }

    fun release() {
        precompiledFilters.values.forEach { it.destroy() }
        precompiledFilters.clear()
    }
}
