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

    override fun onInit() {
        super.onInit()
        exposureLocation = android.opengl.GLES20.glGetUniformLocation(program, "exposure")
        contrastLocation = android.opengl.GLES20.glGetUniformLocation(program, "contrast")
        shoulderLocation = android.opengl.GLES20.glGetUniformLocation(program, "shoulder")
        toeLocation = android.opengl.GLES20.glGetUniformLocation(program, "toe")
    }

    override fun onInitialized() {
        super.onInitialized()
        exposure = 0.0f
        contrast = 1.0f
        shoulder = 0.0f
        toe = 0.0f
    }

    companion object {
        const val TONE_MAP_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            
            uniform highp float exposure;
            uniform highp float contrast;
            uniform highp float shoulder;
            uniform highp float toe;
            
            highp vec3 parametricToneMap(highp vec3 x) {
                // 1. Exposure (EV stops)
                x *= pow(2.0, exposure);
                
                // 2. Midtone Contrast (Pivot 0.5)
                highp float c = contrast;
                highp float p = 0.5;
                // Simple sigmoid-like curve: (x^c) / (x^c + p^c * s)
                // s acts as the shoulder rolloff. If shoulder is 1.0 (Safety), s is small.
                highp float s = max(0.01, 1.0 - shoulder);
                
                highp vec3 x_c = pow(x, vec3(c));
                highp float p_c = pow(p, c);
                
                highp vec3 toneMapped = x_c / (x_c + p_c * s);
                
                // 3. Shadow Toe (Lifting/Crushing)
                // Increase toe moves the curve up or down in low ranges
                toneMapped = pow(toneMapped, vec3(1.0 + toe * 0.5));
                
                return clamp(toneMapped, 0.0, 1.0);
            }
            
            void main() {
                highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                
                // 1. Apply Parametric Tone Map
                highp vec3 toneMapped = parametricToneMap(textureColor.rgb);
                
                // 2. Highlight Desaturation (Film-like rolloff to white)
                highp float luminance = dot(toneMapped, vec3(0.2126, 0.7152, 0.0722));
                highp float desatFactor = smoothstep(0.8, 1.0, luminance);
                toneMapped = mix(toneMapped, vec3(luminance), desatFactor * 0.5);
                
                // 3. Linear to sRGB conversion (gamma 2.2 approximate)
                highp vec3 srgbColor = pow(toneMapped, vec3(1.0 / 2.2));
                
                gl_FragColor = vec4(srgbColor, textureColor.a);
            }
        """
    }
}

