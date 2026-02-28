package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

class ToneMapFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    TONE_MAP_FRAGMENT_SHADER
) {
    private var exposureLocation: Int = -1
    private var contrastLocation: Int = -1
    private var shoulderLocation: Int = -1
    private var toeLocation: Int = -1
    private var highlightsLocation: Int = -1
    private var shadowsLocation: Int = -1

    var exposure: Float = 0.0f
        set(value) {
            field = value
            setFloat(exposureLocation, value)
        }

    var contrast: Float = 1.0f
        set(value) {
            field = value
            setFloat(contrastLocation, value)
        }

    var shoulder: Float = 0.0f
        set(value) {
            field = value
            setFloat(shoulderLocation, value)
        }

    var toe: Float = 0.0f
        set(value) {
            field = value
            setFloat(toeLocation, value)
        }

    var highlights: Float = 0.0f
        set(value) {
            field = value
            setFloat(highlightsLocation, value)
        }

    var shadows: Float = 0.0f
        set(value) {
            field = value
            setFloat(shadowsLocation, value)
        }

    override fun onInit() {
        super.onInit()
        exposureLocation = android.opengl.GLES20.glGetUniformLocation(program, "exposure")
        contrastLocation = android.opengl.GLES20.glGetUniformLocation(program, "contrast")
        shoulderLocation = android.opengl.GLES20.glGetUniformLocation(program, "shoulder")
        toeLocation = android.opengl.GLES20.glGetUniformLocation(program, "toe")
        highlightsLocation = android.opengl.GLES20.glGetUniformLocation(program, "highlights")
        shadowsLocation = android.opengl.GLES20.glGetUniformLocation(program, "shadows")
    }

    override fun onInitialized() {
        super.onInitialized()
        exposure = 0.0f
        contrast = 1.0f
        shoulder = 0.0f
        toe = 0.0f
        highlights = 0.0f
        shadows = 0.0f
    }

    companion object {
        val TONE_MAP_FRAGMENT_SHADER = """
precision highp float;
varying highp vec2 textureCoordinate;
uniform sampler2D inputImageTexture;

uniform highp float exposure;
uniform highp float contrast;
uniform highp float shoulder;
uniform highp float toe;
uniform highp float highlights;
uniform highp float shadows;

// High-frequency noise for dithering
highp float rand(highp vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

highp vec3 parametricToneMap(highp vec3 x) {
    // 1. Exposure (Multiplicative)
    x = x * pow(2.0, exposure);
    
    // 2. Perceptually Uniform Contrast (Log-space)
    // Pivot around 0.18 (standard mid-grey) to protect shadows from crushing
    const highp float pivot = 0.18;
    highp vec3 logX = log2(max(x, 0.0001));
    logX = (logX - log2(pivot)) * contrast + log2(pivot);
    x = max(exp2(logX), 0.0);
    
    // 3. Dedicated Shadows (Soft Lift/Compression)
    // shadows > 0 lifts shadows, shadows < 0 crushes
    highp float sLog = log2(max(length(x), 0.001));
    highp float sWeight = smoothstep(0.0, -4.0, sLog); // Only affect very dark areas
    x += x * (shadows * 0.5 * sWeight);
    
    // 4. Dedicated Highlights (Soft Compression/Expansion)
    // highlights > 0 boosts highlights, highlights < 0 recovers them
    highp float hWeight = smoothstep(0.5, 1.0, length(x));
    x += x * (highlights * 0.3 * hWeight);
    
    // 5. Classic Toe (Shadow Lift)
    highp vec3 shadowMask = clamp(1.0 - (x * 2.0), 0.0, 1.0);
    x += vec3(toe * 0.025) * shadowMask * shadowMask;
    
    // 6. Classic Shoulder (Highlight Roll-off)
    // Now improved to be more aggressive on extreme peaks
    highp vec3 highlightMask = clamp((x - 0.6) * 2.5, 0.0, 1.0);
    x -= vec3(shoulder * 0.4) * highlightMask * highlightMask;
    
    return x;
}

// official sRGB OETF (Gamma)
highp vec3 sRGB_OETF(highp vec3 c) {
    highp vec3 lower = c * 12.92;
    highp vec3 upper = 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055;
    return mix(lower, upper, step(vec3(0.0031308), c));
}

void main() {
    highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
    
    // Un-premultiply alpha for math correctness
    highp vec3 rgb = textureColor.rgb / max(textureColor.a, 0.0001);
    
    // 1. Apply Parametric Tone Map
    highp vec3 toneMapped = parametricToneMap(rgb);
    
    // 2. Highlight Desaturation (Film-like rolloff to white)
    highp float luminance = dot(toneMapped, vec3(0.2126, 0.7152, 0.0722));
    // Only desaturate extreme highlights if they are blown out (>0.8)
    highp float desatFactor = smoothstep(0.8, 1.2, luminance);
    toneMapped = mix(toneMapped, vec3(luminance), desatFactor * 0.5);
    
    // 3. Dithering (to mask 8-bit banding from earlier processing)
    highp float noise = rand(textureCoordinate) - 0.5;
    toneMapped += noise / 255.0;
    
    // 4. Final sRGB OETF (Official Gamma Correction)
    highp vec3 result = sRGB_OETF(max(toneMapped, 0.0));
    
    // Re-premultiply alpha
    gl_FragColor = vec4(clamp(result * textureColor.a, 0.0, 1.0), textureColor.a);
}
""".trimIndent()
    }
}
