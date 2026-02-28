package com.filmfx.app.engine.filters

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

class GrainFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    GRAIN_FRAGMENT_SHADER
) {
    private var intensityLocation: Int = -1
    private var sizeLocation: Int = -1
    private var softnessLocation: Int = -1
    private var clumpinessLocation: Int = -1
    private var shadowCoverageLocation: Int = -1
    private var highlightFadeLocation: Int = -1
    private var shiftLocation: Int = -1
    private var colorNoiseToggleLocation: Int = -1
    private var chromaIntensityLocation: Int = -1
    private var resolutionLocation: Int = -1
    private var seedLocation: Int = -1
    private var randomnessLocation: Int = -1
    private var opacityLocation: Int = -1

    var intensity: Float = 0.0f
        set(value) {
            field = value
            setFloat(intensityLocation, value)
        }

    var size: Float = 1.0f
        set(value) {
            field = value
            setFloat(sizeLocation, value)
        }

    var softness: Float = 0.5f
        set(value) {
            field = value
            setFloat(softnessLocation, value)
        }

    var clumpiness: Float = 0.0f
        set(value) {
            field = value
            setFloat(clumpinessLocation, value)
        }

    var shadowCoverage: Float = 0.1f
        set(value) {
            field = value
            setFloat(shadowCoverageLocation, value)
        }

    var highlightFade: Float = 0.95f
        set(value) {
            field = value
            setFloat(highlightFadeLocation, value)
        }

    var shift: Float = 0.0f
        set(value) {
            field = value
            setFloat(shiftLocation, value)
        }

    var colorNoiseToggle: Boolean = true
        set(value) {
            field = value
            setFloat(colorNoiseToggleLocation, if (value) 1.0f else 0.0f)
        }

    var chromaIntensity: Float = 0.5f
        set(value) {
            field = value
            setFloat(chromaIntensityLocation, value)
        }

    var randomness: Float = 0.5f
        set(value) {
            field = value
            setFloat(randomnessLocation, value)
        }

    var opacity: Float = 1.0f
        set(value) {
            field = value
            setFloat(opacityLocation, value)
        }

    var seed: Float = 0.0f
        set(value) {
            field = value
            setFloat(seedLocation, value)
        }

    override fun onInit() {
        super.onInit()
        intensityLocation = GLES20.glGetUniformLocation(program, "intensity")
        sizeLocation = GLES20.glGetUniformLocation(program, "size")
        softnessLocation = GLES20.glGetUniformLocation(program, "softness")
        clumpinessLocation = GLES20.glGetUniformLocation(program, "clumpiness")
        shadowCoverageLocation = GLES20.glGetUniformLocation(program, "shadowCoverage")
        highlightFadeLocation = GLES20.glGetUniformLocation(program, "highlightFade")
        shiftLocation = GLES20.glGetUniformLocation(program, "shift")
        colorNoiseToggleLocation = GLES20.glGetUniformLocation(program, "colorNoiseToggle")
        chromaIntensityLocation = GLES20.glGetUniformLocation(program, "chromaIntensity")
        resolutionLocation = GLES20.glGetUniformLocation(program, "resolution")
        seedLocation = GLES20.glGetUniformLocation(program, "seed")
        randomnessLocation = GLES20.glGetUniformLocation(program, "randomness")
        opacityLocation = GLES20.glGetUniformLocation(program, "opacity")
    }

    override fun onInitialized() {
        super.onInitialized()
        intensity = intensity
        size = size
        softness = softness
        clumpiness = clumpiness
        shadowCoverage = shadowCoverage
        highlightFade = highlightFade
        shift = shift
        colorNoiseToggle = colorNoiseToggle
        chromaIntensity = chromaIntensity
        randomness = randomness
        opacity = opacity
        seed = seed
    }

    var referenceHeight: Float = 0f

    override fun onOutputSizeChanged(width: Int, height: Int) {
        super.onOutputSizeChanged(width, height)
        val targetH = if (referenceHeight > 10f) referenceHeight else height.toFloat()
        val aspect = width.toFloat() / height.toFloat()
        val simulatedWidth = targetH * aspect
        
        setPoint(resolutionLocation, android.graphics.PointF(simulatedWidth, targetH))
    }

    companion object {
        const val GRAIN_FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;

            uniform float intensity;
            uniform float size;
            uniform float softness;
            uniform float clumpiness;
            uniform float shadowCoverage;
            uniform float highlightFade;
            uniform float shift;
            uniform float colorNoiseToggle;
            uniform float chromaIntensity;
            uniform vec2 resolution;
            uniform float seed;
            uniform float randomness;
            uniform float opacity;

            // 1. Improved Hash for 4K stability
            vec3 hash33(vec3 p3) {
                p3 = fract(p3 * vec3(.1031, .1030, .0973));
                p3 += dot(p3, p3.yxz + 33.33);
                return fract((p3.xxy + p3.yzz) * p3.zyx) * 2.0 - 1.0;
            }

            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                vec2 u = f * f * (3.0 - 2.0 * f);
                
                // Using 3D hash for better randomness variation with seed
                float a = dot(hash33(vec3(i, seed)).xy, f);
                float b = dot(hash33(vec3(i + vec2(1.0, 0.0), seed)).xy, f - vec2(1.0, 0.0));
                float c = dot(hash33(vec3(i + vec2(0.0, 1.0), seed)).xy, f - vec2(0.0, 1.0));
                float d = dot(hash33(vec3(i + vec2(1.0, 1.0), seed)).xy, f - vec2(1.0, 1.0));
                
                return mix(mix(a, b, u.x), mix(c, d, u.x), u.y) + 0.5;
            }

            void main() {
                vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                
                // 1. Un-premultiply for math correctness
                vec3 rgb = textureColor.rgb / max(textureColor.a, 0.0001);
                float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

                // 2. Grain Density Curve (Perceptual)
                float shiftedLuma = clamp(luma + shift * 0.4, 0.0, 1.0);
                float bellCurve = 4.0 * shiftedLuma * (1.0 - shiftedLuma); 
                float density = mix(bellCurve, 1.0 - shiftedLuma, shadowCoverage);
                density = pow(density, max(highlightFade, 0.1));
                
                // 3. Shadow-Safe Grain (D-Max Protection)
                // Protect the deepest blacks from grain to keep them clean
                density *= smoothstep(0.0, 0.05, luma);

                // 4. Scale factor for resolution independence (2000px Baseline)
                float baseScale = 1.0 / (max(size, 0.01) * 0.3); // Tighter base for finer grain
                vec2 uv = textureCoordinate * (resolution.xy / baseScale);

                // 5. Layered Noise Generation
                // Layer 1: Micro-grit (Very small, high frequency)
                float microNoise = noise(uv * 2.5);
                // Layer 2: Medium grain
                float medNoise = noise(uv);
                // Layer 3: Larger Clumps
                float clumpNoise = noise(uv * 0.3);

                // Blend octaves
                float combinedNoise = mix(microNoise, medNoise, 0.6);
                combinedNoise = mix(combinedNoise, clumpNoise, clumpiness * 0.5);
                
                // Softness dilutes noise contrast
                combinedNoise = mix(combinedNoise, 0.5, softness * 0.7);

                // 6. Chroma Noise
                float grainR = combinedNoise;
                float grainG = mix(grainR, noise(uv * 1.1 + vec2(1.2, 3.4)), colorNoiseToggle * chromaIntensity);
                float grainB = mix(grainR, noise(uv * 0.9 + vec2(5.6, 7.8)), colorNoiseToggle * chromaIntensity);
                
                vec3 finalNoise = vec3(grainR, grainG, grainB) - 0.5;

                // 7. Application in Perceptual Space
                vec3 grain = finalNoise * intensity * density * 4.0 * opacity;
                vec3 result = rgb + grain;

                // 8. Re-premultiply
                gl_FragColor = vec4(clamp(result * textureColor.a, 0.0, 1.0), textureColor.a);
            }
        """
    }
}
