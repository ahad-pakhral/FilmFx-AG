package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import android.opengl.GLES20

/**
 * VignetteFilter applies a radial quadratic falloff to darken the corners of the image.
 */
class VignetteFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    VIGNETTE_FRAGMENT_SHADER
) {
    private var intensityLocation: Int = -1
    private var smoothnessLocation: Int = -1

    var intensity: Float = 0.5f
        set(value) {
            field = value
            setFloat(intensityLocation, value)
        }

    var smoothness: Float = 0.5f
        set(value) {
            field = value
            setFloat(smoothnessLocation, value)
        }

    override fun onInit() {
        super.onInit()
        intensityLocation = GLES20.glGetUniformLocation(program, "intensity")
        smoothnessLocation = GLES20.glGetUniformLocation(program, "smoothness")
    }

    override fun onInitialized() {
        super.onInitialized()
        intensity = 0.5f
        smoothness = 0.5f
    }

    companion object {
        const val VIGNETTE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform float intensity;
            uniform float smoothness;

            void main() {
                vec4 color = texture2D(inputImageTexture, textureCoordinate);
                
                // Distance from center (0.5, 0.5)
                vec2 dist = textureCoordinate - vec2(0.5, 0.5);
                
                // Quadratic falloff
                float d = length(dist) * (intensity * 1.5);
                
                // Smooth transition
                // As smoothness increases, the vignette becomes softer/starts earlier
                float vignette = smoothstep(0.8, 0.8 - (smoothness * 0.4), d);
                
                gl_FragColor = vec4(color.rgb * vignette, color.a);
            }
        """
    }
}
