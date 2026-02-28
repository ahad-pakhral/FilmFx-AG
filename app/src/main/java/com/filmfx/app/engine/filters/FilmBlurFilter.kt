package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

class FilmBlurFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    BLUR_FRAGMENT_SHADER
) {
    private var amountLocation: Int = -1
    private var isTiltShiftLocation: Int = -1
    private var focusLocation: Int = -1
    private var aspectRatioLocation: Int = -1

    var exportScale: Float = 1.0f
        set(value) {
            field = value
            // Re-trigger amount setter to apply the scale
            amount = amount
        }

    var amount: Float = 0.0f
        set(value) {
            field = value
            setFloat(amountLocation, value * exportScale)
        }

    var isTiltShift: Boolean = false
        set(value) {
            field = value
            setInteger(isTiltShiftLocation, if (value) 1 else 0)
        }

    var focus: Float = 0.5f
        set(value) {
            field = value
            setFloat(focusLocation, value)
        }

    var aspectRatio: Float = 1.0f
        set(value) {
            field = value
            setFloat(aspectRatioLocation, value)
        }

    override fun onInit() {
        super.onInit()
        amountLocation = android.opengl.GLES20.glGetUniformLocation(program, "amount")
        isTiltShiftLocation = android.opengl.GLES20.glGetUniformLocation(program, "isTiltShift")
        focusLocation = android.opengl.GLES20.glGetUniformLocation(program, "focus")
        aspectRatioLocation = android.opengl.GLES20.glGetUniformLocation(program, "aspectRatio")
    }

    override fun onInitialized() {
        super.onInitialized()
        amount = amount
        isTiltShift = isTiltShift
        focus = focus
        aspectRatio = aspectRatio
    }

    companion object {
        val BLUR_FRAGMENT_SHADER = """
precision highp float;
varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;

            uniform highp float amount;
            uniform int isTiltShift;
            uniform highp float focus;
            uniform highp float aspectRatio;

            void main() {
                highp vec4 originalColor = texture2D(inputImageTexture, textureCoordinate);
                
                if (amount < 0.01) {
                    gl_FragColor = originalColor;
                    return;
                }

                highp float actualBlurAmount = amount;
                
                // Tilt Shift falloff mask
                if (isTiltShift == 1) {
                    // Calculate distance to the focus band (horizontal line)
                    highp float distFromFocus = abs(textureCoordinate.y - focus);
                    // Create a soft boundary
                    highp float blurMask = smoothstep(0.1, 0.4, distFromFocus);
                    actualBlurAmount = amount * blurMask;
                }

                if (actualBlurAmount < 0.01) {
                    gl_FragColor = originalColor;
                    return;
                }

                // Un-premultiply for sampling math
                highp vec3 baseRgb = originalColor.rgb / max(originalColor.a, 0.0001);

                highp vec3 blurred = vec3(0.0);
                highp float radius = actualBlurAmount * 0.02; // max 2% screen width
                
                // Safety check for aspectRatio (Infinity/NaN protection)
                highp float safeAspect = (aspectRatio > 0.0 && aspectRatio < 10.0) ? aspectRatio : 1.0;
                highp vec2 pSize = vec2(radius, radius * safeAspect);
                
                // Add noise-based rotation to dither the Poisson disk and remove swatchy directional artifacts
                highp float noise = fract(sin(dot(textureCoordinate, vec2(12.9898, 78.233))) * 43758.5453);
                highp float theta = noise * 6.2831853;
                highp float c = cos(theta);
                highp float s = sin(theta);
                highp mat2 rot = mat2(c, -s, s, c);
                
                highp vec4 sampleColor;
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(-0.94201624, -0.39906216) * pSize); blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(0.94558609, -0.76890725) * pSize);   blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(-0.094184101, -0.92938870) * pSize); blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(0.34495938, 0.29387760) * pSize);    blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(-0.91588581, 0.45771432) * pSize);   blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(-0.81544232, -0.87912464) * pSize);  blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(-0.38277543, 0.27676845) * pSize);   blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(0.97484398, 0.75648379) * pSize);    blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(0.44323325, -0.97511554) * pSize);   blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(0.53742981, -0.47373420) * pSize);   blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(-0.26496911, -0.41893023) * pSize);  blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(0.79197514, 0.19090188) * pSize);    blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(-0.24188840, 0.99706507) * pSize);   blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(-0.81409955, 0.91437590) * pSize);   blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(0.19984126, 0.78641367) * pSize);    blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);
                sampleColor = texture2D(inputImageTexture, textureCoordinate + rot * vec2(0.14383161, -0.14100790) * pSize);   blurred += sampleColor.rgb / max(sampleColor.a, 0.0001);

                blurred /= 16.0;

                // Soft Focus Style: Mix blurred layer over original, acting as a screen/lighten blend
                highp vec3 finalRgb = mix(baseRgb, blurred, 0.5 + (actualBlurAmount * 0.5));
                
                // Re-premultiply alpha
                gl_FragColor = vec4(finalRgb * originalColor.a, originalColor.a);
            }
        """.trimIndent()
    }
}
