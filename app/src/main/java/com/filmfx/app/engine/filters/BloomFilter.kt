package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import android.opengl.GLES20

/**
 * BloomFilter simulates light scattering from bright highlights.
 */
class BloomFilter : GPUImageFilterGroup() {

    private val thresholdFilter = BloomThresholdFilter()
    private val horizontalBlur = BloomBlurFilter(false)
    private val verticalBlur = BloomBlurFilter(true)
    private val compositeFilter = BloomCompositeFilter()

    init {
        addFilter(thresholdFilter)
        addFilter(horizontalBlur)
        addFilter(verticalBlur)
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

    private class BloomThresholdFilter : GPUImageFilter(
        NO_FILTER_VERTEX_SHADER,
        """
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform sampler2D inputImageTexture;
        uniform float threshold;

        void main() {
            vec4 color = texture2D(inputImageTexture, textureCoordinate);
            float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            float weight = smoothstep(threshold, threshold + 0.1, luminance);
            gl_FragColor = vec4(color.rgb * weight, color.a);
        }
        """.trimIndent()
    ) {
        private var thresholdLocation: Int = -1
        private var threshold: Float = 0.8f

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

    private class BloomBlurFilter(val isVertical: Boolean) : GPUImageFilter(
        NO_FILTER_VERTEX_SHADER,
        """
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform sampler2D inputImageTexture;
        uniform float texelWidthOffset;
        uniform float texelHeightOffset;

        void main() {
            vec2 offset = isVertical ? vec2(0.0, texelHeightOffset * 2.5) : vec2(texelWidthOffset * 2.5, 0.0);
            vec4 color = texture2D(inputImageTexture, textureCoordinate) * 0.227027;
            color += texture2D(inputImageTexture, textureCoordinate + offset * 1.38461538) * 0.31621622;
            color += texture2D(inputImageTexture, textureCoordinate - offset * 1.38461538) * 0.31621622;
            color += texture2D(inputImageTexture, textureCoordinate + offset * 3.23076923) * 0.07027027;
            color += texture2D(inputImageTexture, textureCoordinate - offset * 3.23076923) * 0.07027027;
            gl_FragColor = color;
        }
        """.trimIndent()
    )

    private class BloomCompositeFilter : GPUImageFilter(
        NO_FILTER_VERTEX_SHADER,
        """
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform sampler2D inputImageTexture; // Blurred glow
        uniform sampler2D inputImageTexture2; // Original image
        uniform float intensity;

        void main() {
            vec4 blur = texture2D(inputImageTexture, textureCoordinate);
            vec4 original = texture2D(inputImageTexture2, textureCoordinate);
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
}
