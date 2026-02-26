package com.filmfx.app.engine.export

object ResolutionFallback {
    private val FALLBACK_STEPS = listOf(
        0.8f,      // -20%
        0.64f,     // -20%
        0.576f,    // -10%
        0.5184f,   // -10%
        0.49248f,  // -5%
        0.467856f, // -5%
        0.444463f, // -5%
        0.422240f, // -5%
        0.401128f, // -5%
        0.381071f, // -5%
        0.362018f, // -5%
        0.343917f, // -5%
        0.326721f, // -5%
        0.310385f, // -5%
        0.294866f  // -5%
    )

    fun getDimensionsForAttempt(originalWidth: Int, originalHeight: Int, attemptIndex: Int): Pair<Int, Int>? {
        if (attemptIndex == 0) return Pair(originalWidth, originalHeight)
        
        val stepIndex = attemptIndex - 1
        if (stepIndex >= FALLBACK_STEPS.size) return null
        
        val scale = FALLBACK_STEPS[stepIndex]
        val newWidth = (originalWidth * scale).toInt()
        val newHeight = (originalHeight * scale).toInt()
        
        // Ensure even dimensions
        val evNewWidth = if (newWidth % 2 != 0) newWidth - 1 else newWidth
        val evNewHeight = if (newHeight % 2 != 0) newHeight - 1 else newHeight
        
        return Pair(evNewWidth, evNewHeight)
    }
}
