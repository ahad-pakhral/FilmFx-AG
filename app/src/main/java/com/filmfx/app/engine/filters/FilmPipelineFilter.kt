package com.filmfx.app.engine.filters

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * High-Precision Monolithic Pipeline Filter.
 *
 * This filter combines all linear-space color operations into a single fragment shader pass.
 * By keeping the color values in high-precision registers throughout the entire chain,
 * we avoid the quantization errors and shadow destruction that occur when passing 
 * linear values through intermediate 8-bit FBO textures.
 */
class FilmPipelineFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    PIPELINE_FRAGMENT_SHADER
) {
    // 1. Color Uniforms
    private var temperatureLoc = -1
    private var tintLoc = -1
    private var saturationLoc = -1
    private var richnessLoc = -1
    private var subtractiveSatLoc = -1

    // 2. Atmosphere Uniforms
    private var dehazeAmountLoc = -1
    private var dehazeNeutralizeLoc = -1

    // 3. Split Toning Uniforms
    private var highlightHueLoc = -1
    private var highlightSatLoc = -1
    private var shadowHueLoc = -1
    private var shadowSatLoc = -1
    private var balanceLoc = -1
    private var impactLoc = -1

    // 4. Tone Map Uniforms
    private var exposureLoc = -1
    private var contrastLoc = -1
    private var shoulderLoc = -1
    private var toeLoc = -1
    private var highlightsLoc = -1
    private var shadowsLoc = -1

    // -- State properties --

    var temperature: Float = 6000f
        set(value) { field = value; setFloat(temperatureLoc, value) }
    var tint: Float = 0f
        set(value) { field = value; setFloat(tintLoc, value) }
    var saturation: Float = 1f
        set(value) { field = value; setFloat(saturationLoc, value) }
    var richness: Float = 0f
        set(value) { field = value; setFloat(richnessLoc, value) }
    var subtractiveSat: Float = 0f
        set(value) { field = value; setFloat(subtractiveSatLoc, value) }

    var dehazeAmount: Float = 0f
        set(value) { field = value; setFloat(dehazeAmountLoc, value) }
    var dehazeNeutralize: Float = 0.5f
        set(value) { field = value; setFloat(dehazeNeutralizeLoc, value) }

    var highlightHue: Float = 0f
        set(value) { field = value; setFloat(highlightHueLoc, value) }
    var highlightSat: Float = 0f
        set(value) { field = value; setFloat(highlightSatLoc, value) }
    var shadowHue: Float = 0f
        set(value) { field = value; setFloat(shadowHueLoc, value) }
    var shadowSat: Float = 0f
        set(value) { field = value; setFloat(shadowSatLoc, value) }
    var balance: Float = 0f
        set(value) { field = value; setFloat(balanceLoc, value) }
    var impact: Float = 1f
        set(value) { field = value; setFloat(impactLoc, value) }

    var exposure: Float = 0f
        set(value) { field = value; setFloat(exposureLoc, value) }
    var contrast: Float = 1f
        set(value) { field = value; setFloat(contrastLoc, value) }
    var shoulder: Float = 0f
        set(value) { field = value; setFloat(shoulderLoc, value) }
    var toe: Float = 0f
        set(value) { field = value; setFloat(toeLoc, value) }
    var highlights: Float = 0f
        set(value) { field = value; setFloat(highlightsLoc, value) }
    var shadows: Float = 0f
        set(value) { field = value; setFloat(shadowsLoc, value) }

    override fun onInit() {
        super.onInit()
        temperatureLoc = GLES20.glGetUniformLocation(program, "temperature")
        tintLoc = GLES20.glGetUniformLocation(program, "tint")
        saturationLoc = GLES20.glGetUniformLocation(program, "saturation")
        richnessLoc = GLES20.glGetUniformLocation(program, "richness")
        subtractiveSatLoc = GLES20.glGetUniformLocation(program, "subtractiveSat")
        
        dehazeAmountLoc = GLES20.glGetUniformLocation(program, "dehazeAmount")
        dehazeNeutralizeLoc = GLES20.glGetUniformLocation(program, "dehazeNeutralize")
        
        highlightHueLoc = GLES20.glGetUniformLocation(program, "highlightHue")
        highlightSatLoc = GLES20.glGetUniformLocation(program, "highlightSat")
        shadowHueLoc = GLES20.glGetUniformLocation(program, "shadowHue")
        shadowSatLoc = GLES20.glGetUniformLocation(program, "shadowSat")
        balanceLoc = GLES20.glGetUniformLocation(program, "balance")
        impactLoc = GLES20.glGetUniformLocation(program, "impact")
        
        exposureLoc = GLES20.glGetUniformLocation(program, "exposure")
        contrastLoc = GLES20.glGetUniformLocation(program, "contrast")
        shoulderLoc = GLES20.glGetUniformLocation(program, "shoulder")
        toeLoc = GLES20.glGetUniformLocation(program, "toe")
        highlightsLoc = GLES20.glGetUniformLocation(program, "highlights")
        shadowsLoc = GLES20.glGetUniformLocation(program, "shadows")
    }

    override fun onInitialized() {
        super.onInitialized()
        // Force refresh all uniforms
        temperature = temperature
        tint = tint
        saturation = saturation
        richness = richness
        subtractiveSat = subtractiveSat
        dehazeAmount = dehazeAmount
        dehazeNeutralize = dehazeNeutralize
        highlightHue = highlightHue
        highlightSat = highlightSat
        shadowHue = shadowHue
        shadowSat = shadowSat
        balance = balance
        impact = impact
        exposure = exposure
        contrast = contrast
        shoulder = shoulder
        toe = toe
        highlights = highlights
        shadows = shadows
    }

    companion object {
        private val PIPELINE_FRAGMENT_SHADER = """
precision highp float;
varying highp vec2 textureCoordinate;
uniform sampler2D inputImageTexture;

// 1. Color Uniforms
uniform highp float temperature;
uniform highp float tint;
uniform highp float saturation;
uniform highp float richness;
uniform highp float subtractiveSat;

// 2. Dehaze Uniforms
uniform highp float dehazeAmount;
uniform highp float dehazeNeutralize;

// 3. Split Toning Uniforms
uniform highp float highlightHue;
uniform highp float highlightSat;
uniform highp float shadowHue;
uniform highp float shadowSat;
uniform highp float balance;
uniform highp float impact;

// 4. Tone Map Uniforms
uniform highp float exposure;
uniform highp float contrast;
uniform highp float shoulder;
uniform highp float toe;
uniform highp float highlights;
uniform highp float shadows;

const highp float PI = 3.14159265359;

// --- Helper Functions ---

highp vec3 sRGB_to_Linear(highp vec3 c) {
    highp vec3 lower = c / 12.92;
    highp vec3 upper = pow((c + 0.055) / 1.055, vec3(2.4));
    return mix(lower, upper, step(vec3(0.04045), c));
}

highp vec3 sRGB_OETF(highp vec3 c) {
    highp vec3 lower = c * 12.92;
    highp vec3 upper = 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055;
    return mix(lower, upper, step(vec3(0.0031308), c));
}

highp vec3 linear_srgb_to_oklab(highp vec3 c) {
    highp float l = 0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b;
    highp float m = 0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b;
    highp float s = 0.0883024619 * c.r + 0.2817188976 * c.g + 0.6299787005 * c.b;
    highp float l_ = pow(max(l, 0.0), 1.0/3.0);
    highp float m_ = pow(max(m, 0.0), 1.0/3.0);
    highp float s_ = pow(max(s, 0.0), 1.0/3.0);
    return vec3(
        0.2104542551 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    );
}

highp vec3 oklab_to_linear_srgb(highp vec3 c) {
    highp float l_ = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
    highp float m_ = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
    highp float s_ = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
    highp float l = l_ * l_ * l_;
    highp float m = m_ * m_ * m_;
    highp float s = s_ * s_ * s_;
    return vec3(
        +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    );
}

highp vec3 kelvinToRGB(highp float kelvin) {
    highp float temp = kelvin / 100.0;
    highp float r, g, b;
    if (temp <= 66.0) {
        r = 1.0;
        g = clamp(0.39008 * log(temp) - 0.63184, 0.0, 1.0);
        b = temp <= 19.0 ? 0.0 : clamp(0.54320 * log(temp - 10.0) - 1.19625, 0.0, 1.0);
    } else {
        r = clamp(1.29294 * pow(temp - 60.0, -0.13320), 0.0, 1.0);
        g = clamp(1.12989 * pow(temp - 60.0, -0.07551), 0.0, 1.0);
        b = 1.0;
    }
    return vec3(r, g, b);
}

highp float rand(highp vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

highp vec3 parametricToneMap(highp vec3 x) {
    // 1. Exposure
    x = x * pow(2.0, exposure);
    // 2. Contrast (Mid-gray pivot)
    const highp float pivot = 0.18;
    highp vec3 logX = log2(max(x, 0.0001));
    logX = (logX - log2(pivot)) * contrast + log2(pivot);
    x = max(exp2(logX), 0.0);
    // 3. Dedicated Shadows
    highp float sLog = log2(max(length(x), 0.001));
    highp float sWeight = smoothstep(0.0, -4.0, sLog);
    x += x * (shadows * 0.5 * sWeight);
    // 4. Dedicated Highlights
    highp float hWeight = smoothstep(0.5, 1.0, length(x));
    x += x * (highlights * 0.3 * hWeight);
    // 5. Classic Toe
    highp vec3 shadowMask = clamp(1.0 - (x * 2.0), 0.0, 1.0);
    x += vec3(toe * 0.025) * shadowMask * shadowMask;
    // 6. Classic Shoulder
    highp vec3 highlightMask = clamp((x - 0.6) * 2.5, 0.0, 1.0);
    x -= vec3(shoulder * 0.4) * highlightMask * highlightMask;
    return x;
}

void main() {
    highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
    // Un-premultiply alpha for math correctness
    highp vec3 rgb = textureColor.rgb / max(textureColor.a, 0.0001);

    // 1. Linearize (Piecewise sRGB)
    rgb = sRGB_to_Linear(rgb);

    // 2. Color Filter (Temp, Tint, Richness, Saturation, Subtractive Sat)
    highp vec3 illuminant = kelvinToRGB(temperature);
    highp vec3 reference = kelvinToRGB(6000.0);
    illuminant.g *= (1.0 + (tint / 100.0) * 0.1); 
    highp vec3 correction = reference / max(illuminant, vec3(0.0001));
    rgb *= correction;
    highp vec3 lab = linear_srgb_to_oklab(rgb);
    highp float richnessWeight = smoothstep(0.1, 0.3, lab.x) * (1.0 - smoothstep(0.6, 0.9, lab.x));
    lab.y *= 1.0 + (richness * richnessWeight);
    lab.z *= 1.0 + (richness * richnessWeight);
    lab.y *= saturation;
    lab.z *= saturation;
    highp float chroma = length(vec2(lab.y, lab.z));
    lab.x = lab.x - (chroma * subtractiveSat * 1.5);
    rgb = oklab_to_linear_srgb(lab);

    // 3. Dehaze
    highp float darkChannel = min(min(rgb.r, rgb.g), rgb.b);
    highp float transmission = max(1.0 - (max(dehazeAmount, 0.0) * darkChannel * 0.95), 0.1);
    highp float maxCh = max(max(rgb.r, rgb.g), rgb.b);
    highp vec3 A = mix(vec3(1.0), vec3(maxCh), 0.5);
    
    // Expressive Haze Tinting: 0.0 (Cool) -> 0.5 (Natural) -> 1.0 (Warm)
    highp vec3 coolHaze = vec3(0.5, 0.65, 0.95) * 1.3; 
    highp vec3 warmHaze = vec3(1.0, 0.85, 0.65) * 1.1;
    if (dehazeNeutralize < 0.45) {
        A = mix(coolHaze, A, smoothstep(0.0, 0.45, dehazeNeutralize));
    } else if (dehazeNeutralize > 0.55) {
        A = mix(A, warmHaze, smoothstep(0.55, 1.0, dehazeNeutralize));
    }
    
    if (dehazeAmount > 0.01) {
        rgb = ((rgb - A) / transmission) + A;
    } else if (dehazeAmount < -0.01) {
        // Negative Dehaze = Add Haze (Atmospheric Scattering)
        highp float hazeFactor = abs(dehazeAmount) * 0.5;
        rgb = mix(rgb, A, hazeFactor);
    }

    // 4. Split Toning
    highp vec3 labSplit = linear_srgb_to_oklab(rgb);
    highp float midpoint = 0.5 + (balance * 0.4); 
    highp float hWeightST = smoothstep(midpoint - 0.2, midpoint + 0.2, labSplit.x);
    highp float sWeightST = 1.0 - hWeightST;
    highp float hAngle = highlightHue * PI / 180.0;
    labSplit.y += cos(hAngle) * highlightSat * 0.2 * hWeightST;
    labSplit.z += sin(hAngle) * highlightSat * 0.2 * hWeightST;
    highp float sAngle = shadowHue * PI / 180.0;
    labSplit.y += cos(sAngle) * shadowSat * 0.2 * sWeightST;
    labSplit.z += sin(sAngle) * shadowSat * 0.2 * sWeightST;
    highp vec3 splitResult = oklab_to_linear_srgb(labSplit);
    rgb = mix(rgb, splitResult, impact);

    // 5. Tone Mapping
    rgb = parametricToneMap(rgb);
    
    // 6. Highlight Desaturation
    highp float luminance = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    highp float desatFactor = smoothstep(0.8, 1.2, luminance);
    rgb = mix(rgb, vec3(luminance), desatFactor * 0.5);

    // 7. Dithering
    highp float ditherNoise = rand(textureCoordinate) - 0.5;
    rgb += ditherNoise / 255.0;

    // 8. sRGB OETF (Official Gamma)
    rgb = sRGB_OETF(max(rgb, 0.0));

    // Re-premultiply alpha
    gl_FragColor = vec4(clamp(rgb * textureColor.a, 0.0, 1.0), textureColor.a);
}
        """.trimIndent()
    }
}
