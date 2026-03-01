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

            // 1. IMPROVED STABLE HASH FUNCTION (NON-PERIODIC)
            // Replaces the old sin/fract hash which created wormy, streaky artifacts.
            // Uses dot products and large prime-like constants for stable, fast hashing.
            float hash21(vec2 p) {
                p = fract(p * vec3(.1031, .1030, .0973).xy);
                p += dot(p, p.yx + 33.33);
                return fract((p.x + p.y) * p.x);
            }

            // 2. Cinematic Overlay Blending Math (Same as before)
            float blendOverlay(float base, float blend) {
                return (base < 0.5) ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
            }
            vec3 overlay(vec3 base, vec3 blend) {
                return vec3(
                    blendOverlay(base.r, blend.r),
                    blendOverlay(base.g, blend.g),
                    blendOverlay(base.b, blend.b)
                );
            }

            void main() {
                vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                vec3 rgb = textureColor.rgb / max(textureColor.a, 0.0001);
                float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

                // 3. Film Density Curve (Midtone targeted)
                float shiftedLuma = clamp(luma + shift * 0.4, 0.0, 1.0);
                float bellCurve = 4.0 * shiftedLuma * (1.0 - shiftedLuma);
                float density = mix(bellCurve, 1.0 - shiftedLuma, shadowCoverage);
                density = pow(density, max(highlightFade, 0.1));
                density *= smoothstep(0.0, 0.05, luma); // Protect deepest blacks

                // 4. Calculate Screen Pixels and Group Them
                float crystalSize = max(size * 1.5, 0.5); // Tune this multiplier for slider response
                vec2 pixelCoord = textureCoordinate * resolution.xy;

                // 5. IMPROVED LAYERED NOISE GENERATION (Fixes Repetition)
                // We use multiple, differently scaled crystal grids to break up tiling.

                // Pass 1: Base crystal grid
                vec2 grainUv1 = floor(pixelCoord / crystalSize);
                float noise1 = hash21(grainUv1 + seed);

                // Pass 2: Slightly differently scaled grid to break up repetitive tiling patterns.
                // Multiplying by a prime-related number (1.337) maximizes randomness in overlapping.
                vec2 largeCoordOffset = vec2(37.3, 41.7);
                vec2 grainUv2 = floor((pixelCoord + largeCoordOffset) / (crystalSize * 1.337));
                float noise2 = hash21(grainUv2 - (seed * 11.0)); // Vary the seed per layer

                // Combine them using the 'clumpiness' parameter to influence weight.
                float combinedNoise = mix(noise1, noise2, clumpiness * 0.6 + 0.2);

                // Add a final very subtle layer of raw micro-hash to randomize edges
                float microHash = hash21(pixelCoord / 0.77);
                combinedNoise = mix(combinedNoise, microHash, 0.1); 

                // Softness blurs edges while keeping crystal structure intact.
                combinedNoise = mix(combinedNoise, 0.5, softness * 0.4);

                // 6. Chroma Integration
                float grainR = combinedNoise;
                // Generate distinct seeds per channel using modified input UVs
                float grainG = mix(grainR, hash21(grainUv1 + vec2(11.2, 13.4) + seed), colorNoiseToggle * chromaIntensity);
                float grainB = mix(grainR, hash21(grainUv2 + vec2(15.6, 17.8) - seed), colorNoiseToggle * chromaIntensity);
                vec3 grainTexture = vec3(grainR, grainG, grainB);

                // 7. Apply Density, Intensity, and Opacity
                float finalIntensity = intensity * density * opacity;
                vec3 finalGrainOverlay = mix(vec3(0.5), grainTexture, finalIntensity);

                // 8. Cinematic Overlay Composite
                vec3 result = overlay(rgb, finalGrainOverlay);

                gl_FragColor = vec4(clamp(result * textureColor.a, 0.0, 1.0), textureColor.a);
            }
        """
    }
}
