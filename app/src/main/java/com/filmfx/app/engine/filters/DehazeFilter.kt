package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

class DehazeFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    DEHAZE_FRAGMENT_SHADER
) {
    private var amountLocation: Int = -1
    private var neutralizeLocation: Int = -1

    var amount: Float = 0.0f
        set(value) {
            field = value
            setFloat(amountLocation, value)
        }

    var neutralize: Float = 0.5f
        set(value) {
            field = value
            setFloat(neutralizeLocation, value)
        }

    override fun onInit() {
        super.onInit()
        amountLocation = android.opengl.GLES20.glGetUniformLocation(program, "amount")
        neutralizeLocation = android.opengl.GLES20.glGetUniformLocation(program, "neutralize")
    }

    override fun onInitialized() {
        super.onInitialized()
        amount = 0.0f
        neutralize = 0.5f
    }

    companion object {
        const val DEHAZE_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;

            uniform highp float amount;      // 0.0 to 1.0
            uniform highp float neutralize;  // 0.0 to 1.0

            void main() {
                highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                highp vec3 rgb = textureColor.rgb;
                
                // Simplified Single-Pass Dark Channel Estimation
                // In a true DCP, we'd sample a local patch. Here we use per-pixel for speed.
                highp float darkChannel = min(min(rgb.r, rgb.g), rgb.b);
                
                // Estimate haze transmission map
                // As amount increases, we assume more of the dark channel is haze.
                highp float transmission = 1.0 - (amount * darkChannel * 0.95); // max 95% removal
                transmission = max(transmission, 0.1); // Prevent div by zero/artifacts
                
                // Atmospheric Light Estimation (A)
                // We assume A is bright gray/white, leaning towards the pixel's max component
                highp float maxChannel = max(max(rgb.r, rgb.g), rgb.b);
                highp vec3 A = mix(vec3(1.0), vec3(maxChannel), 0.5); // Blend white with local luminance
                
                // Auto-Neutralization: Shift 'A' towards neutral gray based on slider
                highp float lumaA = dot(A, vec3(0.299, 0.587, 0.114));
                A = mix(A, vec3(lumaA), neutralize);
                
                // Radiance Recovery formula: J(x) = (I(x) - A) / max(t(x), t0) + A
                highp vec3 recovered = ((rgb - A) / transmission) + A;
                
                // Protect highlights from blowing out entirely
                // Recovered can go > 1.0 or < 0.0, so we clamp softly
                recovered = clamp(recovered, 0.0, 1.0);
                
                gl_FragColor = vec4(mix(rgb, recovered, step(0.01, amount)), textureColor.a);
            }
        """
    }
}
