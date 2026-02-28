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
    private var radiusLocation: Int = -1
    private var softnessLocation: Int = -1
    private var aspectRatioLocation: Int = -1

    var intensity: Float = 0.0f
        set(value) {
            field = value
            setFloat(intensityLocation, value)
        }

    var radius: Float = 0.8f
        set(value) {
            field = value
            setFloat(radiusLocation, value)
        }

    var softness: Float = 0.5f
        set(value) {
            field = value
            setFloat(softnessLocation, value)
        }

    var aspectRatio: Float = 1.0f
        set(value) {
            field = value
            setFloat(aspectRatioLocation, value)
        }

    override fun onInit() {
        super.onInit()
        intensityLocation = GLES20.glGetUniformLocation(program, "intensity")
        radiusLocation = GLES20.glGetUniformLocation(program, "radius")
        softnessLocation = GLES20.glGetUniformLocation(program, "softness")
        aspectRatioLocation = GLES20.glGetUniformLocation(program, "aspectRatio")
    }

    override fun onInitialized() {
        super.onInitialized()
        intensity = intensity
        radius = radius
        softness = softness
        aspectRatio = aspectRatio
    }

    companion object {
        const val VIGNETTE_FRAGMENT_SHADER = """
            precision mediump float;
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            
            uniform float intensity; // 0.0 to 1.0 (or negative for white)
            uniform float radius;    // Midpoint
            uniform float softness;  // Feathering
            uniform float aspectRatio; // For elliptical scaling
            
            void main() {
                vec4 color = texture2D(inputImageTexture, textureCoordinate);
                
                // 1. Calculate the distance from center
                vec2 center = vec2(0.5, 0.5);
                vec2 coord = textureCoordinate - center;
                
                // Scale the y-coordinate by aspectRatio to make the radial gradient elliptical
                // (This perfectly matches the underlying image shape regardless of Viewport stretching)
                coord.y *= aspectRatio;
                
                float dist = length(coord);
                
                // 2. Apply the vignette curve
                // smoothstep creates the 'feathering' between the radius and the edge
                float vignette = smoothstep(radius, radius - softness, dist);
                
                // 3. Cinematic Blending
                // Instead of just multiplying by black, we 'darken' the image 
                // This preserves some shadow detail in the corners.
                vec3 finalColor = mix(color.rgb * (1.0 - intensity), color.rgb, vignette);
                
                gl_FragColor = vec4(finalColor, color.a);
            }
        """
    }
}
