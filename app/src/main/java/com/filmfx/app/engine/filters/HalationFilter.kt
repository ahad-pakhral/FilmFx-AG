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
        addFilter(GaussianBlurFilter(isVertical = false, radius = 8.0f))
        addFilter(GaussianBlurFilter(isVertical = true, radius = 8.0f))
        addFilter(GaussianBlurFilter(isVertical = false, radius = 20.0f))
        addFilter(GaussianBlurFilter(isVertical = true, radius = 20.0f))
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
            
            // Extract highlights where Red is dominant
            float brightness = max(0.0, color.r - threshold);
            float bleed = max(0.0, color.r - max(color.g, color.b)) * 0.5;
            float signal = brightness + bleed;
            
            // Output red-orange tint
            gl_FragColor = vec4(signal, signal * 0.25, signal * 0.05, 1.0);
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
            vec4 blur = texture2D(inputImageTexture, textureCoordinate);
            vec4 original = texture2D(inputImageTexture2, textureCoordinate);
            
            // Additive blending for the glow
            gl_FragColor = vec4(original.rgb + (blur.rgb * intensity), original.a);
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
            vec2 offset = (isVertical ? vec2(0.0, texelHeightOffset) : vec2(texelWidthOffset, 0.0)) * (radius / 5.0);
            vec4 color = texture2D(inputImageTexture, textureCoordinate) * 0.227027;
            color += texture2D(inputImageTexture, textureCoordinate + offset * 1.38461538) * 0.31621622;
            color += texture2D(inputImageTexture, textureCoordinate - offset * 1.38461538) * 0.31621622;
            color += texture2D(inputImageTexture, textureCoordinate + offset * 3.23076923) * 0.07027027;
            color += texture2D(inputImageTexture, textureCoordinate - offset * 3.23076923) * 0.07027027;
            gl_FragColor = color;
        }
        """.trimIndent()
    )
}
