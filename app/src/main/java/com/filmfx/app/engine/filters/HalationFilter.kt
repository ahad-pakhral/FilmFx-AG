package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import android.opengl.GLES20

/**
 * HalationFilter simulates the reddish-orange glow around bright light sources
 * and high-contrast edges, typical of analog film.
 *
 * It uses a 3-pass multi-scale blur applied to the red channel of bright areas.
 */
class HalationFilter : GPUImageFilterGroup() {

    private val thresholdFilter = HalationThresholdFilter()
    private val blurGroup = GPUImageFilterGroup().apply {
        addFilter(GaussianBlurFilter(isVertical = false, radius = 2.0f))
        addFilter(GaussianBlurFilter(isVertical = true, radius = 2.0f))
        addFilter(GaussianBlurFilter(isVertical = false, radius = 10.0f))
        addFilter(GaussianBlurFilter(isVertical = true, radius = 10.0f))
        addFilter(GaussianBlurFilter(isVertical = false, radius = 25.0f))
        addFilter(GaussianBlurFilter(isVertical = true, radius = 25.0f))
    }
    private val compositeFilter = HalationCompositeFilter()

    init {
        addFilter(thresholdFilter)
        addFilter(blurGroup)
        addFilter(compositeFilter)
    }

    override fun onDraw(textureId: Int, cubeBuffer: java.nio.FloatBuffer, textureBuffer: java.nio.FloatBuffer) {
        // Save the original textureId to use it in the composite filter for blending
        compositeFilter.setSecondaryTexture(textureId)
        super.onDraw(textureId, cubeBuffer, textureBuffer)
    }

    fun setIntensity(intensity: Float) {
        compositeFilter.setIntensity(intensity)
    }

    fun setThreshold(threshold: Float) {
        thresholdFilter.setThreshold(threshold)
    }

    private class HalationThresholdFilter : GPUImageFilter(
        NO_FILTER_VERTEX_SHADER,
        """
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform sampler2D inputImageTexture;
        uniform float threshold;

        void main() {
            vec4 color = texture2D(inputImageTexture, textureCoordinate);
            
            // 1. Calculate Perceptual Luminance (Standard Rec. 709)
            // This ensures blue, white, and green lights trigger halation.
            float lum = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
            
            // 2. Soft-Thresholding
            // Using smoothstep prevents a "hard line" at the edge of the glow.
            float extract = smoothstep(threshold, threshold + 0.15, lum);
            
            // 3. Apply the Analog "Red-Orange" Tint
            // Real film halation is caused by red-wavelength light scattering.
            // This maps the brightness to a warm reddish-orange bloom.
            gl_FragColor = vec4(extract, extract * 0.22, extract * 0.04, 1.0);
        }
        """.trimIndent()
    ) {
        private var thresholdLocation: Int = -1
        private var threshold: Float = 0.6f

        override fun onInit() {
            super.onInit()
            thresholdLocation = GLES20.glGetUniformLocation(program, "threshold")
        }

        override fun onInitialized() {
            super.onInitialized()
            setThreshold(threshold)
        }

        fun setThreshold(value: Float) {
            threshold = value
            setFloat(thresholdLocation, threshold)
        }
    }

    private class HalationCompositeFilter : GPUImageFilter(
        NO_FILTER_VERTEX_SHADER,
        """
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform sampler2D inputImageTexture; // Blurred halo
        uniform sampler2D inputImageTexture2; // Original image
        uniform float intensity;

        void main() {
            vec4 blur = texture2D(inputImageTexture, textureCoordinate); // The Halo
            vec4 original = texture2D(inputImageTexture2, textureCoordinate); // The Original
            
            // Screen Blend Mode: 1 - (1 - A) * (1 - B)
            // This prevents "clipping" and keeps the light feeling transparent and organic.
            vec3 result = 1.0 - (1.0 - original.rgb) * (1.0 - blur.rgb * intensity);
            
            gl_FragColor = vec4(result, original.a);
        }
        """.trimIndent()
    ) {
        private var intensityLocation: Int = -1
        private var secondaryTextureLocation: Int = -1
        private var intensity: Float = 0.5f
        private var secondaryTextureId: Int = GLES20.GL_NONE

        override fun onInit() {
            super.onInit()
            intensityLocation = GLES20.glGetUniformLocation(program, "intensity")
            secondaryTextureLocation = GLES20.glGetUniformLocation(program, "inputImageTexture2")
        }

        override fun onInitialized() {
            super.onInitialized()
            setIntensity(intensity)
        }

        fun setIntensity(value: Float) {
            intensity = value
            setFloat(intensityLocation, intensity)
        }

        fun setSecondaryTexture(textureId: Int) {
            secondaryTextureId = textureId
        }

        override fun onDrawArraysPre() {
            super.onDrawArraysPre()
            if (secondaryTextureId != GLES20.GL_NONE) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, secondaryTextureId)
                GLES20.glUniform1i(secondaryTextureLocation, 3)
            }
        }
    }

    private class GaussianBlurFilter(val isVertical: Boolean, val radius: Float) : GPUImageFilter(
        NO_FILTER_VERTEX_SHADER,
        """
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform sampler2D inputImageTexture;
        uniform float texelWidthOffset;
        uniform float texelHeightOffset;

        void main() {
            // Determine direction and scale based on your 2000px resolution lock.
            vec2 texelSize = vec2(texelWidthOffset, texelHeightOffset);
            vec2 direction = isVertical ? vec2(0.0, 1.0) : vec2(1.0, 0.0);
            
            // 9-Tap sampling with optimized Gaussian weights
            // This removes "ghosting" by sampling more points across the blur radius.
            vec4 color = texture2D(inputImageTexture, textureCoordinate) * 0.1633;
            
            color += texture2D(inputImageTexture, textureCoordinate + (direction * texelSize * 1.2 * radius)) * 0.1531;
            color += texture2D(inputImageTexture, textureCoordinate - (direction * texelSize * 1.2 * radius)) * 0.1531;
            
            color += texture2D(inputImageTexture, textureCoordinate + (direction * texelSize * 2.5 * radius)) * 0.1224;
            color += texture2D(inputImageTexture, textureCoordinate - (direction * texelSize * 2.5 * radius)) * 0.1224;
            
            color += texture2D(inputImageTexture, textureCoordinate + (direction * texelSize * 3.8 * radius)) * 0.0918;
            color += texture2D(inputImageTexture, textureCoordinate - (direction * texelSize * 3.8 * radius)) * 0.0918;
            
            color += texture2D(inputImageTexture, textureCoordinate + (direction * texelSize * 5.2 * radius)) * 0.0510;
            color += texture2D(inputImageTexture, textureCoordinate - (direction * texelSize * 5.2 * radius)) * 0.0510;
            
            gl_FragColor = color;
        }
        """.trimIndent()
    )
}
