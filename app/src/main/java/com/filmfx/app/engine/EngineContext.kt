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
    
    object Parity {
        const val TINT_OFFSET = 0.0f
        const val SAT_OFFSET = 0.0f
        const val HALATION_THRESHOLD_OFFSET = 0.0f
        const val SPREAD_SCALE = 1.0f
        const val TOE_OFFSET = 0.0f 
        
        const val GLOW_INTENSITY_SCALE = 1.0f
        const val GLOW_BASE_BOOST = 0.0f
        
        const val GRAIN_EXPORT_SCALE = 1.0f 
        const val GRAIN_PREVIEW_SCALE = 1.0f
        
        const val GRAIN_SIZE_SCALE = 1.0f
        const val VIGNETTE_RADIUS_BOOST = 0.0f
        const val BLUR_SCALE = 1.0f
    }

    companion object {
        fun buildFilterGroup(
            params: EffectParameters,
            viewWidth: Float = 0f,
            viewHeight: Float = 0f,
            forceExportMode: Boolean = false
        ): GPUImageFilterGroup {
            val filters = createFilterList(params, viewWidth, viewHeight, forceExportMode)
            return GPUImageFilterGroup(filters)
        }

        fun updateFilterGroup(
            group: GPUImageFilterGroup,
            params: EffectParameters,
            viewWidth: Float = 0f,
            viewHeight: Float = 0f,
            forceExportMode: Boolean = false
        ) {
            val filters = group.filters
            val isExport = forceExportMode || viewHeight > 3000f
            
            filters.forEach { filter ->
                when (filter) {
                    is FilmPipelineFilter -> {
                        val colorEnabled = params.isColorEnabled
                        filter.temperature = if (colorEnabled) params.colorTemperature else 6000f
                        filter.tint = if (colorEnabled) params.colorTint + Parity.TINT_OFFSET else 0f
                        filter.saturation = if (colorEnabled) params.colorSaturation + Parity.SAT_OFFSET else 1f
                        filter.richness = if (colorEnabled) params.colorRichness else 0f
                        filter.subtractiveSat = if (colorEnabled) params.colorSubtractiveSat else 0f

                        val atmosEnabled = params.isAtmosphereEnabled
                        filter.dehazeAmount = if (atmosEnabled) params.dehazeAmount else 0f
                        filter.dehazeNeutralize = if (atmosEnabled) params.dehazeAtmosphereNeutralize else 0.5f

                        val splitEnabled = params.isSplitToningEnabled
                        filter.highlightHue = if (splitEnabled) params.splitToneHighlightHue else 0f
                        filter.highlightSat = if (splitEnabled) params.splitToneHighlightSat else 0f
                        filter.shadowHue = if (splitEnabled) params.splitToneShadowHue else 0f
                        filter.shadowSat = if (splitEnabled) params.splitToneShadowSat else 0f
                        filter.balance = if (splitEnabled) params.splitToneBalance else 0f
                        filter.impact = if (splitEnabled) params.splitToneImpact else 0f

                        val lightEnabled = params.isLightEnabled
                        filter.exposure = if (lightEnabled) params.toneMapExposure else 0f
                        filter.contrast = if (lightEnabled) params.toneMapContrast else 1f
                        filter.shoulder = if (lightEnabled) params.toneMapShoulder else 0f
                        filter.toe = if (lightEnabled) params.toneMapToe + Parity.TOE_OFFSET else 0f
                        filter.highlights = if (lightEnabled) params.toneMapHighlights else 0f
                        filter.shadows = if (lightEnabled) params.toneMapShadows else 0f
                    }
                    is GlowFilter -> {
                        val glowIntensityScale = Parity.GLOW_INTENSITY_SCALE
                        val glowBaseBoost = Parity.GLOW_BASE_BOOST
                        val spreadScale = Parity.SPREAD_SCALE
                        val halationThresholdOffset = Parity.HALATION_THRESHOLD_OFFSET
                        
                        val halationEnabled = params.isHalationEnabled
                        filter.halationIntensity = if (halationEnabled) (params.halationIntensity + glowBaseBoost) * glowIntensityScale else 0f
                        filter.halationThreshold = params.halationThreshold + halationThresholdOffset
                        filter.halationSpread = params.halationSpread * spreadScale
                        filter.halationHue = params.halationHue
                        filter.halationSaturation = params.halationSaturation
                        filter.showMaskOnly = params.halationShowMask
                        
                        val bloomEnabled = params.isBloomEnabled
                        filter.bloomIntensity = if (bloomEnabled) (params.bloomIntensity + glowBaseBoost) * glowIntensityScale else 0f
                        filter.bloomThreshold = params.bloomThreshold + halationThresholdOffset
                        filter.bloomSpread = params.bloomSpread * spreadScale
                        filter.bloomOpacity = params.bloomOpacity
                        filter.glowBlackPoint = params.bloomBlackPoint
                        filter.glowHighlightProtection = params.bloomHighlightProtection
                        filter.blendMode = if (params.bloomSoftLight) 1 else 0
                    }
                    is FilmBlurFilter -> {
                        val enabled = params.isAtmosphereEnabled
                        filter.amount = if (enabled) params.blurAmount * Parity.BLUR_SCALE else 0f
                        filter.isTiltShift = params.blurIsTiltShift
                        filter.focus = params.blurTiltShiftFocus
                        if (viewWidth > 0 && viewHeight > 0) filter.aspectRatio = viewWidth / viewHeight
                    }
                    is VignetteFilter -> {
                        val enabled = params.isVignetteEnabled
                        filter.intensity = if (enabled) params.vignetteIntensity else 0f
                        filter.radius = params.vignetteRadius + Parity.VIGNETTE_RADIUS_BOOST
                        filter.softness = params.vignetteFeather
                        if (viewWidth > 0 && viewHeight > 0) filter.aspectRatio = viewWidth / viewHeight
                    }
                    is GrainFilter -> {
                        val enabled = params.isGrainEnabled
                        val isExportMode = forceExportMode || viewHeight > 3000f
                        val grainIntensityScale = if (isExportMode) Parity.GRAIN_EXPORT_SCALE else Parity.GRAIN_PREVIEW_SCALE
                        
                        filter.intensity = if (enabled) params.grainIntensity * grainIntensityScale else 0f
                        filter.size = params.grainSize * Parity.GRAIN_SIZE_SCALE
                        filter.softness = params.grainSoftness
                        filter.clumpiness = params.grainClumpiness
                        filter.shadowCoverage = params.grainShadowCoverage
                        filter.highlightFade = params.grainHighlightFade
                        filter.colorNoiseToggle = params.grainColorNoise
                        filter.chromaIntensity = params.grainChromaIntensity
                    }
                    is SpatialTransformFilter -> {
                        if (viewWidth > 0 && viewHeight > 0) filter.aspect = viewWidth / viewHeight
                        if (!isExport) {
                            filter.scale = params.transformScale
                            filter.offsetX = params.transformOffsetX
                            filter.offsetY = params.transformOffsetY
                            filter.rotation = params.transformRotation
                        } else {
                            filter.scale = 1.0f
                            filter.offsetX = 0.0f
                            filter.offsetY = 0.0f
                            filter.rotation = 0.0f
                        }
                    }
                }
            }
        }

        private fun createFilterList(
            params: EffectParameters,
            viewWidth: Float,
            viewHeight: Float,
            isExport: Boolean
        ): List<jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter> {
            val filmPipelineFilter = FilmPipelineFilter().apply {
                temperature = params.colorTemperature
                tint = params.colorTint + Parity.TINT_OFFSET
                saturation = params.colorSaturation + Parity.SAT_OFFSET
                richness = params.colorRichness
                subtractiveSat = params.colorSubtractiveSat
                
                dehazeAmount = params.dehazeAmount
                dehazeNeutralize = params.dehazeAtmosphereNeutralize
                
                highlightHue = params.splitToneHighlightHue
                highlightSat = params.splitToneHighlightSat
                shadowHue = params.splitToneShadowHue
                shadowSat = params.splitToneShadowSat
                balance = params.splitToneBalance
                impact = params.splitToneImpact
                
                exposure = params.toneMapExposure
                contrast = params.toneMapContrast
                shoulder = params.toneMapShoulder
                toe = params.toneMapToe + Parity.TOE_OFFSET
                highlights = params.toneMapHighlights
                shadows = params.toneMapShadows
            }
            
            val refHeight = 2000.0f

            val glowFilter = GlowFilter().apply {
                referenceHeight = refHeight
                halationIntensity = (params.halationIntensity + Parity.GLOW_BASE_BOOST) * Parity.GLOW_INTENSITY_SCALE
                halationThreshold = params.halationThreshold + Parity.HALATION_THRESHOLD_OFFSET
                halationSpread = params.halationSpread * Parity.SPREAD_SCALE
                halationHue = params.halationHue
                halationSaturation = params.halationSaturation
                showMaskOnly = params.halationShowMask
                
                bloomIntensity = (params.bloomIntensity + Parity.GLOW_BASE_BOOST) * Parity.GLOW_INTENSITY_SCALE
                bloomThreshold = params.bloomThreshold + Parity.HALATION_THRESHOLD_OFFSET
                bloomSpread = params.bloomSpread * Parity.SPREAD_SCALE
                bloomOpacity = params.bloomOpacity
                glowBlackPoint = params.bloomBlackPoint
                glowHighlightProtection = params.bloomHighlightProtection
                blendMode = if (params.bloomSoftLight) 1 else 0
            }
            val filmBlurFilter = FilmBlurFilter().apply {
                amount = params.blurAmount * Parity.BLUR_SCALE
                isTiltShift = params.blurIsTiltShift
                focus = params.blurTiltShiftFocus
                if (viewWidth > 0 && viewHeight > 0) aspectRatio = viewWidth / viewHeight
            }
            val grainFilter = GrainFilter().apply {
                referenceHeight = refHeight
                val grainIntensityScale = if (isExport) Parity.GRAIN_EXPORT_SCALE else Parity.GRAIN_PREVIEW_SCALE
                intensity = params.grainIntensity * grainIntensityScale
                size = params.grainSize * Parity.GRAIN_SIZE_SCALE
                softness = params.grainSoftness
                clumpiness = params.grainClumpiness
                shadowCoverage = params.grainShadowCoverage
                highlightFade = params.grainHighlightFade
                colorNoiseToggle = params.grainColorNoise
                chromaIntensity = params.grainChromaIntensity
            }
            val vignetteFilter = VignetteFilter().apply {
                intensity = params.vignetteIntensity
                radius = params.vignetteRadius + Parity.VIGNETTE_RADIUS_BOOST
                softness = params.vignetteFeather
                if (viewWidth > 0 && viewHeight > 0) aspectRatio = viewWidth / viewHeight
            }
            val spatialTransformFilter = SpatialTransformFilter().apply {
                if (viewWidth > 0 && viewHeight > 0) aspect = viewWidth / viewHeight
                if (!isExport) {
                    scale = params.transformScale
                    offsetX = params.transformOffsetX
                    offsetY = params.transformOffsetY
                    rotation = params.transformRotation
                }
            }

            return listOf(
                filmPipelineFilter,
                glowFilter,
                filmBlurFilter,
                vignetteFilter,
                grainFilter,
                spatialTransformFilter
            )
        }
    }
}
