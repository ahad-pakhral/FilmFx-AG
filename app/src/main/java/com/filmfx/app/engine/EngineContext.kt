package com.filmfx.app.engine

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.Log
import com.filmfx.app.data.EffectParameters
import com.filmfx.app.engine.filters.*
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup

class EngineContext {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    
    var fboId: Int = 0
        private set
    var textureId: Int = 0
        private set

    fun initialize() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("unable to initialize EGL14")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
        
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs, 0)

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
        
        Log.d("EngineContext", "EGL Context Initialized successfully")
    }

    fun setupOffscreenFbo(inputWidth: Int, inputHeight: Int) {
        // Cap at 1080p roughly (1920x1080)
        val maxDim = 1080
        val scale = if (inputWidth > maxDim || inputHeight > maxDim) {
            maxDim.toFloat() / Math.max(inputWidth, inputHeight)
        } else 1.0f
        
        val targetWidth = (inputWidth * scale).toInt()
        val targetHeight = (inputHeight * scale).toInt()

        val fboArray = IntArray(1)
        GLES30.glGenFramebuffers(1, fboArray, 0)
        fboId = fboArray[0]

        val texArray = IntArray(1)
        GLES30.glGenTextures(1, texArray, 0)
        textureId = texArray[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, targetWidth, targetHeight, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, textureId, 0)

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not complete: $status")
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        
        Log.d("EngineContext", "Offscreen FBO created at ${targetWidth}x${targetHeight}")
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
    
    companion object {
        fun buildFilterGroup(params: EffectParameters, viewWidth: Float = 0f, viewHeight: Float = 0f): GPUImageFilterGroup {
            val linearizeFilter = LinearizeFilter()
            val colorFilter = ColorFilter().apply {
                temperature = params.colorTemperature
                tint = params.colorTint
                saturation = params.colorSaturation
                richness = params.colorRichness
                subtractiveSat = params.colorSubtractiveSat
            }
            val toneMapFilter = ToneMapFilter().apply {
                exposure = params.toneMapExposure
                contrast = params.toneMapContrast
                shoulder = params.toneMapShoulder
                toe = params.toneMapToe
            }
            val dehazeFilter = DehazeFilter().apply {
                amount = params.dehazeAmount
                neutralize = params.dehazeAtmosphereNeutralize
            }
            val splitToningFilter = SplitToningFilter().apply {
                highlightHue = params.splitToneHighlightHue
                highlightSat = params.splitToneHighlightSat
                shadowHue = params.splitToneShadowHue
                shadowSat = params.splitToneShadowSat
                balance = params.splitToneBalance
                impact = params.splitToneImpact
            }
            val glowFilter = GlowFilter().apply {
                // Halation
                halationIntensity = params.halationIntensity
                halationThreshold = params.halationThreshold
                halationSpread = params.halationSpread
                halationHue = params.halationHue
                halationSaturation = params.halationSaturation
                showMaskOnly = params.halationShowMask
                // Bloom
                bloomIntensity = params.bloomIntensity
                bloomThreshold = params.bloomThreshold
                bloomSpread = params.bloomSpread
                bloomOpacity = params.bloomOpacity
                glowBlackPoint = params.bloomBlackPoint
                glowHighlightProtection = params.bloomHighlightProtection
                blendMode = if (params.bloomSoftLight) 1 else 0
            }
            val filmBlurFilter = FilmBlurFilter().apply {
                amount = params.blurAmount
                isTiltShift = params.blurIsTiltShift
                focus = params.blurTiltShiftFocus
                if (viewWidth > 0 && viewHeight > 0) aspectRatio = viewWidth / viewHeight
            }
            val grainFilter = GrainFilter().apply {
                intensity = params.grainIntensity
                size = params.grainSize
                softness = params.grainSoftness
                clumpiness = params.grainClumpiness
                shadowCoverage = params.grainShadowCoverage
                highlightFade = params.grainHighlightFade
                colorNoiseToggle = params.grainColorNoise
                chromaIntensity = params.grainChromaIntensity
            }
            val vignetteFilter = VignetteFilter().apply {
                intensity = params.vignetteIntensity
                radius = params.vignetteRadius
                softness = params.vignetteFeather
            }
            val spatialTransformFilter = SpatialTransformFilter().apply {
                if (viewWidth > 0 && viewHeight > 0) aspect = viewWidth / viewHeight
            }

            return GPUImageFilterGroup(listOf(
                linearizeFilter,
                colorFilter,
                toneMapFilter,
                dehazeFilter,
                splitToningFilter,
                glowFilter,
                filmBlurFilter,
                grainFilter,
                vignetteFilter,
                spatialTransformFilter
            ))
        }
    }
}
