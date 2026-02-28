package com.filmfx.app.engine.filters

import android.opengl.GLES20
import android.opengl.GLES30
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GlowFilter : GPUImageFilter() {

    private val copyFilter = GPUImageFilter() // Lock pass for OES -> 2D 
    private val halationExtractFilter = HalationExtractFilter()
    
    private val halationBlur = Array(8) { i -> GlowBlurFilter(isVertical = i % 2 == 0) }
    private val bloomExtractFilter = BloomExtractFilter()
    private val bloomBlur = Array(8) { i -> GlowBlurFilter(isVertical = i % 2 == 0) }
    
    private val blendFilter = GlowBlendFilter()

    private var frameBuffers: IntArray? = null
    private var frameBufferTextures: IntArray? = null

    private val glCubeBuffer: FloatBuffer
    private val glTextureBuffer: FloatBuffer
    
    init {
        val cube = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )
        glCubeBuffer = ByteBuffer.allocateDirect(cube.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(cube).position(0)
            }
            
        val tex = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
        glTextureBuffer = ByteBuffer.allocateDirect(tex.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(tex).position(0)
            }
    }

    override fun onInit() {
        super.onInit()
        copyFilter.ifNeedInit()
        halationExtractFilter.ifNeedInit()
        halationBlur.forEach { it.ifNeedInit() }
        bloomExtractFilter.ifNeedInit()
        bloomBlur.forEach { it.ifNeedInit() }
        blendFilter.ifNeedInit()
    }

    override fun onDestroy() {
        destroyFramebuffers()
        copyFilter.destroy()
        halationExtractFilter.destroy()
        halationBlur.forEach { it.destroy() }
        bloomExtractFilter.destroy()
        bloomBlur.forEach { it.destroy() }
        blendFilter.destroy()
        super.onDestroy()
    }

    private fun destroyFramebuffers() {
        frameBufferTextures?.let {
            GLES20.glDeleteTextures(it.size, it, 0)
            frameBufferTextures = null
        }
        frameBuffers?.let {
            GLES20.glDeleteFramebuffers(it.size, it, 0)
            frameBuffers = null
        }
    }

    override fun onOutputSizeChanged(width: Int, height: Int) {
        super.onOutputSizeChanged(width, height)
        destroyFramebuffers()
        
        val fboCount = 19
        val fbos = IntArray(fboCount)
        val textures = IntArray(fboCount)
        
        val targetH = if (referenceHeight > 10f) referenceHeight else height.toFloat()
        // Determine how much to scale down the actual image height to match targetH
        val scale = minOf(1f, targetH / height.toFloat())
        
        val workW = maxOf((width * scale).toInt(), 1)
        val workH = maxOf((height * scale).toInt(), 1)
        
        val halfW = maxOf(workW / 2, 1)
        val halfH = maxOf(workH / 2, 1)
        val quarterW = maxOf(workW / 4, 1)
        val quarterH = maxOf(workH / 4, 1)
        val eighthW = maxOf(workW / 8, 1)
        val eighthH = maxOf(workH / 8, 1)
        val sixteenthW = maxOf(workW / 16, 1)
        val sixteenthH = maxOf(workH / 16, 1)

        val sizes = arrayOf(
            Pair(width, height),       // 0: Base copy
            Pair(workW, workH),        // 1: Halation Extract
            Pair(halfW, halfH), Pair(halfW, halfH),      // 2,3: Halation Scale 1
            Pair(quarterW, quarterH), Pair(quarterW, quarterH),  // 4,5: Halation Scale 2
            Pair(eighthW, eighthH), Pair(eighthW, eighthH),    // 6,7: Halation Scale 3
            Pair(sixteenthW, sixteenthH), Pair(sixteenthW, sixteenthH), // 8,9: Halation Scale 4
            Pair(workW, workH),        // 10: Bloom Extract
            Pair(halfW, halfH), Pair(halfW, halfH),      // 11,12: Bloom Scale 1
            Pair(quarterW, quarterH), Pair(quarterW, quarterH),  // 13,14: Bloom Scale 2
            Pair(eighthW, eighthH), Pair(eighthW, eighthH),    // 15,16: Bloom Scale 3
            Pair(sixteenthW, sixteenthH), Pair(sixteenthW, sixteenthH)  // 17,18: Bloom Scale 4
        )

        for (i in 0 until fboCount) {
            GLES20.glGenFramebuffers(1, fbos, i)
            GLES20.glGenTextures(1, textures, i)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            
            // Use GLES30 for high-precision half-float textures
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, 
                sizes[i].first, sizes[i].second, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
            
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, 
                GLES20.GL_TEXTURE_2D, textures[i], 0)
            
            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                android.util.Log.e("GlowFilter", "FBO $i incomplete: $status")
            }
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        
        frameBuffers = fbos
        frameBufferTextures = textures
        
        copyFilter.onOutputSizeChanged(sizes[0].first, sizes[0].second)
        
        halationExtractFilter.onOutputSizeChanged(sizes[1].first, sizes[1].second)
        halationExtractFilter.setInputTextureSize(sizes[0].first, sizes[0].second)
        for (i in 0..7) {
            halationBlur[i].onOutputSizeChanged(sizes[i + 2].first, sizes[i + 2].second)
            halationBlur[i].setInputTextureSize(sizes[i + 1].first, sizes[i + 1].second)
        }
        
        bloomExtractFilter.onOutputSizeChanged(sizes[10].first, sizes[10].second)
        bloomExtractFilter.setInputTextureSize(sizes[0].first, sizes[0].second)
        for (i in 0..7) {
            bloomBlur[i].onOutputSizeChanged(sizes[i + 11].first, sizes[i + 11].second)
            bloomBlur[i].setInputTextureSize(sizes[i + 10].first, sizes[i + 10].second)
        }
        
        blendFilter.onOutputSizeChanged(width, height)
    }

    // -- State Mapping --

    var referenceHeight: Float = 0f
        set(value) {
            field = value
            halationSpread = halationSpread
            bloomSpread = bloomSpread
        }

    var halationIntensity: Float = 0.0f
        set(value) { field = value; blendFilter.halationIntensity = value }

    var bloomIntensity: Float = 0.0f
        set(value) { field = value; blendFilter.bloomIntensity = value }

    var bloomOpacity: Float = 1.0f
        set(value) { field = value; blendFilter.bloomOpacity = value }

    var halationThreshold: Float = 0.8f
        set(value) { field = value; halationExtractFilter.threshold = value }

    var bloomThreshold: Float = 0.8f
        set(value) { field = value; bloomExtractFilter.threshold = value }

    var halationSpread: Float = 0.0f
        set(value) {
            field = value
            // Quadratic response with a generous base for visibility at 0.0
            val q = (value * value) * 1.5f + 0.1f
            halationBlur[0].radius = 1.0f * q;  halationBlur[1].radius = 1.0f * q
            halationBlur[2].radius = 3.0f * q;  halationBlur[3].radius = 3.0f * q
            halationBlur[4].radius = 8.0f * q;  halationBlur[5].radius = 8.0f * q
            halationBlur[6].radius = 16.0f * q; halationBlur[7].radius = 16.0f * q
        }

    var bloomSpread: Float = 0.0f
        set(value) {
            field = value
            val q = (value * value) * 1.5f + 0.1f
            bloomBlur[0].radius = 1.5f * q;  bloomBlur[1].radius = 1.5f * q
            bloomBlur[2].radius = 5.0f * q;  bloomBlur[3].radius = 5.0f * q
            bloomBlur[4].radius = 12.0f * q; bloomBlur[5].radius = 12.0f * q
            bloomBlur[6].radius = 25.0f * q; bloomBlur[7].radius = 25.0f * q
        }
        
    var showMaskOnly: Boolean = false
        set(value) { field = value; blendFilter.showMaskOnly = value }

    var halationHue: Float = 0.5f
        set(value) { field = value; blendFilter.halationHue = (value - 0.5f) * 2.0f }

    var halationSaturation: Float = 0.25f
        set(value) { field = value; blendFilter.halationSaturation = value * 4.0f }

    var blendMode: Int = 1
        set(value) { field = value; blendFilter.blendMode = value }
        
    var glowBlackPoint: Float = 0.0f
        set(value) { field = value; blendFilter.glowBlackPoint = value }

    var glowHighlightProtection: Float = 1.0f
        set(value) { field = value; blendFilter.glowHighlightProtection = value }

    override fun onDraw(textureId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        val fbos = frameBuffers ?: return
        val texs = frameBufferTextures ?: return

        val previousFbo = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFbo, 0)
        val previousViewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0)

        // Helper
        fun renderPass(filter: GPUImageFilter, inputTex: Int, fboIndex: Int) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[fboIndex])
            GLES20.glViewport(0, 0, filter.outputWidth, filter.outputHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            filter.onDraw(inputTex, glCubeBuffer, glTextureBuffer)
        }

        // Pass 0: Lock original (FBO 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0])
        GLES20.glViewport(0, 0, copyFilter.outputWidth, copyFilter.outputHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        copyFilter.onDraw(textureId, cubeBuffer, textureBuffer)

        // Halation Pipeline (9-tap Multi-scale H/V)
        renderPass(halationExtractFilter, texs[0], 1)
        var currentTex = texs[1]
        for (i in 0..7) { renderPass(halationBlur[i], currentTex, i + 2); currentTex = texs[i + 2] }
        // Final result in texs[9]

        // Bloom Pipeline (9-tap Multi-scale H/V)
        renderPass(bloomExtractFilter, texs[0], 10)
        currentTex = texs[10]
        for (i in 0..7) { renderPass(bloomBlur[i], currentTex, i + 11); currentTex = texs[i + 11] }
        // Final result in texs[18]

        // Blend Pass
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFbo[0])
        GLES20.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
        
        blendFilter.setOriginalAndBloomTextures(texs[0], texs[18])
        // Halation mask is passed as the primary input for the onDraw cycle
        blendFilter.onDraw(texs[9], glCubeBuffer, glTextureBuffer)
    }
}

// --- Multi-Scale Sub-Filters ---

private class HalationExtractFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    """
precision highp float;
    varying vec2 textureCoordinate;
    uniform sampler2D inputImageTexture;
    uniform float threshold;
    uniform float texelWidth;
    uniform float texelHeight;

    float luma(vec3 color) { return dot(color, vec3(0.2126, 0.7152, 0.0722)); }

    void main() {
        // Sample in a 2x2 grid to catch sub-pixel highlights that might be missed during downsampling
        vec3 c0 = texture2D(inputImageTexture, textureCoordinate).rgb;
        vec3 c1 = texture2D(inputImageTexture, textureCoordinate + vec2(texelWidth, 0.0)).rgb;
        vec3 c2 = texture2D(inputImageTexture, textureCoordinate + vec2(0.0, texelHeight)).rgb;
        vec3 c3 = texture2D(inputImageTexture, textureCoordinate + vec2(texelWidth, texelHeight)).rgb;
        
        vec3 color = max(max(c0, c1), max(c2, c3));
        float luminance = luma(color);
        // Wider smoothstep for more sensitive extraction (0.1 padding)
        float extract = smoothstep(threshold - 0.1, threshold + 0.1, luminance);
        gl_FragColor = vec4(extract, extract * 0.22, extract * 0.04, 1.0);
    }
    """.trimIndent()
) {
    private var thresholdLoc = -1
    private var texelWidthLoc = -1
    private var texelHeightLoc = -1
    
    var threshold: Float = 0.8f
        set(value) { field = value; setFloat(thresholdLoc, value) }

    override fun onInit() {
        super.onInit()
        thresholdLoc = GLES20.glGetUniformLocation(program, "threshold")
        texelWidthLoc = GLES20.glGetUniformLocation(program, "texelWidth")
        texelHeightLoc = GLES20.glGetUniformLocation(program, "texelHeight")
    }
    override fun onInitialized() {
        super.onInitialized()
        setFloat(thresholdLoc, threshold)
    }
    fun setInputTextureSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            setFloat(texelWidthLoc, 1f / width)
            setFloat(texelHeightLoc, 1f / height)
        }
    }
}

private class BloomExtractFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    """
precision highp float;
    varying vec2 textureCoordinate;
    uniform sampler2D inputImageTexture;
    uniform float threshold;
    uniform float texelWidth;
    uniform float texelHeight;

    float luma(vec3 color) { return dot(color, vec3(0.2126, 0.7152, 0.0722)); }

    void main() {
        // Max-pooling specularity extraction
        vec3 c0 = texture2D(inputImageTexture, textureCoordinate).rgb;
        vec3 c1 = texture2D(inputImageTexture, textureCoordinate + vec2(texelWidth, 0.0)).rgb;
        vec3 c2 = texture2D(inputImageTexture, textureCoordinate + vec2(0.0, texelHeight)).rgb;
        vec3 c3 = texture2D(inputImageTexture, textureCoordinate + vec2(texelWidth, texelHeight)).rgb;
        
        vec3 color = max(max(c0, c1), max(c2, c3));
        float luminance = luma(color);
        // Wider smoothstep for more sensitive extraction
        float weight = smoothstep(threshold - 0.1, threshold + 0.1, luminance);
        // Boost bloom extraction to feel robust with Screen blend
        gl_FragColor = vec4(color * weight * 1.5, 1.0);
    }
    """.trimIndent()
) {
    private var thresholdLoc = -1
    private var texelWidthLoc = -1
    private var texelHeightLoc = -1
    
    var threshold: Float = 0.8f
        set(value) { field = value; setFloat(thresholdLoc, value) }

    override fun onInit() {
        super.onInit()
        thresholdLoc = GLES20.glGetUniformLocation(program, "threshold")
        texelWidthLoc = GLES20.glGetUniformLocation(program, "texelWidth")
        texelHeightLoc = GLES20.glGetUniformLocation(program, "texelHeight")
    }
    override fun onInitialized() {
        super.onInitialized()
        setFloat(thresholdLoc, threshold)
    }
    fun setInputTextureSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            setFloat(texelWidthLoc, 1f / width)
            setFloat(texelHeightLoc, 1f / height)
        }
    }
}

private class GlowBlurFilter(val isVertical: Boolean) : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    """
    precision highp float;
    varying vec2 textureCoordinate;
    uniform sampler2D inputImageTexture;
    uniform float texelWidth;
    uniform float texelHeight;
    uniform float radius;

    void main() {
        vec2 texelSize = vec2(texelWidth, texelHeight);
        vec2 direction = ${if (isVertical) "vec2(0.0, 1.0)" else "vec2(1.0, 0.0)"};
        
        // 9-Tap sampling with optimized Gaussian weights
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
) {
    private var texelWidthLoc = -1
    private var texelHeightLoc = -1
    private var radiusLoc = -1
    
    var radius: Float = 1.0f
        set(value) { field = value; setFloat(radiusLoc, value) }

    override fun onInit() {
        super.onInit()
        texelWidthLoc = GLES20.glGetUniformLocation(program, "texelWidth")
        texelHeightLoc = GLES20.glGetUniformLocation(program, "texelHeight")
        radiusLoc = GLES20.glGetUniformLocation(program, "radius")
    }
    
    override fun onInitialized() {
        super.onInitialized()
        setFloat(radiusLoc, radius)
    }

    fun setInputTextureSize(width: Int, height: Int) {
        setFloat(texelWidthLoc, 1f / width)
        setFloat(texelHeightLoc, 1f / height)
    }
}

private class GlowBlendFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    """
precision highp float;
    varying vec2 textureCoordinate;

    uniform sampler2D inputImageTexture;  // Halation mask
    uniform sampler2D inputImageTexture2; // Base image
    uniform sampler2D inputImageTexture3; // Bloom mask

    uniform float halationIntensity;
    uniform float bloomIntensity;
    uniform float bloomOpacity;
    uniform float showMaskOnly;
    uniform float halationHue;
    uniform float halationSaturation;
    uniform int blendMode; // 0 = Additive, 1 = Soft Light
    uniform float glowBlackPoint;
    uniform float glowHighlightProtection;

    vec3 hueShift(vec3 color, float hue) {
        const vec3 k = vec3(0.57735, 0.57735, 0.57735);
        float cosAngle = cos(hue);
        return color * cosAngle + cross(k, color) * sin(hue) + k * dot(k, color) * (1.0 - cosAngle);
    }

    void main() {
        vec4 haloColor = texture2D(inputImageTexture, textureCoordinate);
        vec4 baseColor = texture2D(inputImageTexture2, textureCoordinate);
        vec4 bloomColor = texture2D(inputImageTexture3, textureCoordinate);
        
        vec3 gradedHalo = hueShift(haloColor.rgb, halationHue * 0.5);
        float haloLuma = dot(gradedHalo, vec3(0.2126, 0.7152, 0.0722));
        gradedHalo = mix(vec3(haloLuma), gradedHalo, halationSaturation);
        
        vec3 halation = gradedHalo * halationIntensity;
        vec3 bloom = bloomColor.rgb * bloomIntensity;
        vec3 base = baseColor.rgb;
        
        float luma = dot(base, vec3(0.2126, 0.7152, 0.0722));
        
        // Black Point / Haze Control
        float shadowProtection = smoothstep(0.0, glowBlackPoint + 0.001, luma);
        bloom *= shadowProtection;
        
        vec3 totalGlow = halation + bloom;
        
        // 1. Calculate base image with ONLY halation and base (Screen Blend)
        vec3 baseWithHalation = 1.0 - (1.0 - base) * (1.0 - halation);
        
        // 2. Calculate image with FULL glow (Screen Blend bloom over baseWithHalation)
        vec3 screenFull = 1.0 - (1.0 - baseWithHalation) * (1.0 - bloom);
        vec3 additiveFull = baseWithHalation + bloom;
        vec3 softLightFull = (1.0 - 2.0 * bloom) * (baseWithHalation * baseWithHalation) + (2.0 * bloom * baseWithHalation);
        
        vec3 fullGlowImage = (blendMode == 1) ? softLightFull : screenFull;
        
        // 3. Opacity drives the blend between the Halation-only image and the Full-Glow image
        vec3 finalColor = mix(baseWithHalation, fullGlowImage, bloomOpacity);
        
        // Highlight Protection (Core Detail) applies over the final result
        float protection = pow(luma, 3.0) * glowHighlightProtection;
        finalColor = mix(finalColor, base, clamp(protection, 0.0, 1.0));

        if (showMaskOnly > 0.5) {
            gl_FragColor = vec4(clamp(totalGlow, 0.0, 1.0), baseColor.a);
        } else {
            gl_FragColor = vec4(clamp(finalColor, 0.0, 1.0), baseColor.a);
        }
    }
    """.trimIndent()
) {
    private var halationIntensityLoc = -1
    private var bloomIntensityLoc = -1
    private var bloomOpacityLoc = -1
    private var showMaskOnlyLoc = -1
    private var halationHueLoc = -1
    private var halationSaturationLoc = -1
    private var blendModeLoc = -1
    private var glowBlackPointLoc = -1
    private var glowHighlightProtectionLoc = -1
    
    private var customTexture2Loc = -1
    private var customTexture3Loc = -1

    private var originalTextureId = -1
    private var bloomTextureId = -1

    var halationIntensity = 0f
        set(value) { field = value; setFloat(halationIntensityLoc, value) }
    var bloomIntensity = 0f
        set(value) { field = value; setFloat(bloomIntensityLoc, value) }
    var bloomOpacity = 1f
        set(value) { field = value; setFloat(bloomOpacityLoc, value) }
    var showMaskOnly = false
        set(value) { field = value; setFloat(showMaskOnlyLoc, if(value) 1f else 0f) }
    var halationHue = 0f
        set(value) { field = value; setFloat(halationHueLoc, value) }
    var halationSaturation = 1f
        set(value) { field = value; setFloat(halationSaturationLoc, value) }
    var blendMode = 1
        set(value) { field = value; setInteger(blendModeLoc, value) }
    var glowBlackPoint = 0f
        set(value) { field = value; setFloat(glowBlackPointLoc, value) }
    var glowHighlightProtection = 1f
        set(value) { field = value; setFloat(glowHighlightProtectionLoc, value) }

    override fun onInit() {
        super.onInit()
        halationIntensityLoc = GLES20.glGetUniformLocation(program, "halationIntensity")
        bloomIntensityLoc = GLES20.glGetUniformLocation(program, "bloomIntensity")
        bloomOpacityLoc = GLES20.glGetUniformLocation(program, "bloomOpacity")
        showMaskOnlyLoc = GLES20.glGetUniformLocation(program, "showMaskOnly")
        halationHueLoc = GLES20.glGetUniformLocation(program, "halationHue")
        halationSaturationLoc = GLES20.glGetUniformLocation(program, "halationSaturation")
        blendModeLoc = GLES20.glGetUniformLocation(program, "blendMode")
        glowBlackPointLoc = GLES20.glGetUniformLocation(program, "glowBlackPoint")
        glowHighlightProtectionLoc = GLES20.glGetUniformLocation(program, "glowHighlightProtection")
        
        customTexture2Loc = GLES20.glGetUniformLocation(program, "inputImageTexture2")
        customTexture3Loc = GLES20.glGetUniformLocation(program, "inputImageTexture3")
    }

    override fun onInitialized() {
        super.onInitialized()
        setFloat(halationIntensityLoc, halationIntensity)
        setFloat(bloomIntensityLoc, bloomIntensity)
        setFloat(bloomOpacityLoc, bloomOpacity)
        setFloat(showMaskOnlyLoc, if (showMaskOnly) 1f else 0f)
        setFloat(halationHueLoc, halationHue)
        setFloat(halationSaturationLoc, halationSaturation)
        setInteger(blendModeLoc, blendMode)
        setFloat(glowBlackPointLoc, glowBlackPoint)
        setFloat(glowHighlightProtectionLoc, glowHighlightProtection)
    }

    fun setOriginalAndBloomTextures(origId: Int, bloomId: Int) {
        originalTextureId = origId
        bloomTextureId = bloomId
    }

    override fun onDrawArraysPre() {
        super.onDrawArraysPre()
        if (originalTextureId != -1 && customTexture2Loc != -1) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, originalTextureId)
            GLES20.glUniform1i(customTexture2Loc, 3)
        }
        if (bloomTextureId != -1 && customTexture3Loc != -1) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE4)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomTextureId)
            GLES20.glUniform1i(customTexture3Loc, 4)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }
}
