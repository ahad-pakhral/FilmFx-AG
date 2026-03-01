package com.filmfx.app.engine.filters

import android.graphics.PointF
import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * Advanced VignetteFilter for cinematic film emulation.
 * Simulates true optical light falloff with controls for anamorphic squeeze and center shifting.
 */
class VignetteFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    VIGNETTE_FRAGMENT_SHADER
) {
    private var intensityLocation: Int = -1
    private var radiusLocation: Int = -1
    private var softnessLocation: Int = -1
    private var centerLocation: Int = -1
    private var roundnessLocation: Int = -1
    private var slopeLocation: Int = -1
    private var aspectRatioLocation: Int = -1

    // 0.0 to 1.0 (How dark the corners get. 0.0 = no effect)
    var intensity: Float = 0.5f
        set(value) {
            field = value
            setFloat(intensityLocation, value)
        }

    // 0.0 to 1.0 (Size of the protected center area)
    var radius: Float = 0.75f
        set(value) {
            field = value
            setFloat(radiusLocation, value)
        }

    // 0.0 to 1.0 (How gradual the gradient is)
    var softness: Float = 0.5f
        set(value) {
            field = value
            setFloat(softnessLocation, value)
        }

    // X, Y coordinates for the center of the vignette (Default is dead center: 0.5, 0.5)
    var center: PointF = PointF(0.5f, 0.5f)
        set(value) {
            field = value
            setPoint(centerLocation, value)
        }

    // 0.0 (Oval/Screen Shape) to 1.0 (Perfect Circle/Spherical Lens)
    var roundness: Float = 1.0f
        set(value) {
            field = value
            setFloat(roundnessLocation, value)
        }

    // Gamma curve of the falloff. (1.0 = linear. 1.5+ = cinematic exponential curve)
    var slope: Float = 1.5f
        set(value) {
            field = value
            setFloat(slopeLocation, value)
        }

    // Image Width / Image Height
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
        centerLocation = GLES20.glGetUniformLocation(program, "center")
        roundnessLocation = GLES20.glGetUniformLocation(program, "roundness")
        slopeLocation = GLES20.glGetUniformLocation(program, "slope")
        aspectRatioLocation = GLES20.glGetUniformLocation(program, "aspectRatio")
    }

    override fun onInitialized() {
        super.onInitialized()
        // Push default values to the shader once it's compiled
        intensity = intensity
        radius = radius
        softness = softness
        center = center
        roundness = roundness
        slope = slope
        aspectRatio = aspectRatio
    }

    companion object {
        const val VIGNETTE_FRAGMENT_SHADER = """
            precision mediump float;
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            
            uniform float intensity;
            uniform float radius;
            uniform float softness;
            uniform vec2 center;
            uniform float roundness;
            uniform float slope;
            uniform float aspectRatio;
            
            void main() {
                vec4 color = texture2D(inputImageTexture, textureCoordinate);
                
                // 1. Calculate distance from the user-defined center
                vec2 coord = textureCoordinate - center;
                
                // 2. Apply Roundness / Aspect Ratio correction
                // Scales the X coordinate by the aspect ratio to create a perfect physical circle.
                // 'mix' allows blending between an anamorphic oval and a spherical circle.
                vec2 aspectScale = vec2(aspectRatio, 1.0); 
                vec2 shapedCoord = mix(coord, coord * aspectScale, roundness);
                
                float dist = length(shapedCoord);
                
                // 3. Base gradient using smoothstep
                float vignette = smoothstep(radius, radius - softness, dist);
                
                // 4. Falloff Slope (Gamma correction for organic roll-off)
                vignette = pow(vignette, slope);
                
                // 5. Exposure Reduction Blending
                // Simulates physical light loss rather than mixing flat black over the image
                float exposureMultiplier = mix(1.0, vignette, intensity);
                
                gl_FragColor = vec4(color.rgb * exposureMultiplier, color.a);
            }
        """
    }
}
