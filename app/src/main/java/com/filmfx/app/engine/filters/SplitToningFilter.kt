package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

class SplitToningFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    SPLIT_TONING_FRAGMENT_SHADER
) {
    private var highlightHueLocation: Int = -1
    private var highlightSatLocation: Int = -1
    private var shadowHueLocation: Int = -1
    private var shadowSatLocation: Int = -1
    private var balanceLocation: Int = -1
    private var impactLocation: Int = -1

    var highlightHue: Float = 0.0f
        set(value) {
            field = value
            setFloat(highlightHueLocation, value)
        }

    var highlightSat: Float = 0.0f
        set(value) {
            field = value
            setFloat(highlightSatLocation, value)
        }

    var shadowHue: Float = 0.0f
        set(value) {
            field = value
            setFloat(shadowHueLocation, value)
        }

    var shadowSat: Float = 0.0f
        set(value) {
            field = value
            setFloat(shadowSatLocation, value)
        }

    var balance: Float = 0.0f
        set(value) {
            field = value
            setFloat(balanceLocation, value)
        }

    var impact: Float = 1.0f
        set(value) {
            field = value
            setFloat(impactLocation, value)
        }

    override fun onInit() {
        super.onInit()
        highlightHueLocation = android.opengl.GLES20.glGetUniformLocation(program, "highlightHue")
        highlightSatLocation = android.opengl.GLES20.glGetUniformLocation(program, "highlightSat")
        shadowHueLocation = android.opengl.GLES20.glGetUniformLocation(program, "shadowHue")
        shadowSatLocation = android.opengl.GLES20.glGetUniformLocation(program, "shadowSat")
        balanceLocation = android.opengl.GLES20.glGetUniformLocation(program, "balance")
        impactLocation = android.opengl.GLES20.glGetUniformLocation(program, "impact")
    }

    override fun onInitialized() {
        super.onInitialized()
        highlightHue = 0.0f
        highlightSat = 0.0f
        shadowHue = 0.0f
        shadowSat = 0.0f
        balance = 0.0f
        impact = 1.0f
    }

    companion object {
        const val SPLIT_TONING_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;

            uniform highp float highlightHue;
            uniform highp float highlightSat;
            uniform highp float shadowHue;
            uniform highp float shadowSat;
            uniform highp float balance;
            uniform highp float impact;

            const highp float PI = 3.14159265359;

            // Oklab conversion matrices
            highp vec3 linear_srgb_to_oklab(highp vec3 c) {
                highp float l = 0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b;
                highp float m = 0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b;
                highp float s = 0.0883024619 * c.r + 0.2817188976 * c.g + 0.6299787005 * c.b;

                highp float l_ = pow(l, 1.0/3.0);
                highp float m_ = pow(m, 1.0/3.0);
                highp float s_ = pow(s, 1.0/3.0);

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

            void main() {
                highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                highp vec3 labOriginal = linear_srgb_to_oklab(textureColor.rgb);
                highp vec3 lab = labOriginal;

                // Normalised lightness for mask
                highp float L = lab.x;
                
                // 1. Soft Bell-Curve Blending
                // balance shifts the midpoint of the split. Range -1 to 1.
                highp float midpoint = 0.5 + (balance * 0.4); 
                highp float softness = 0.2;
                
                // Weighted masks using smoothstep for highlight and shadow
                highp float hWeight = smoothstep(midpoint - softness, midpoint + softness, L);
                highp float sWeight = 1.0 - hWeight;

                // 2. Apply Tints (a, b shifts)
                highp float hAngle = highlightHue * PI / 180.0;
                highp float hA = cos(hAngle) * highlightSat * 0.2;
                highp float hB = sin(hAngle) * highlightSat * 0.2;

                highp float sAngle = shadowHue * PI / 180.0;
                highp float sA = cos(sAngle) * shadowSat * 0.2;
                highp float sB = sin(sAngle) * shadowSat * 0.2;

                // Apply shifts weighted by masks
                lab.y += hA * hWeight + sA * sWeight;
                lab.z += hB * hWeight + sB * sWeight;

                // 3. Global Impact Mix
                lab = mix(labOriginal, lab, impact);

                highp vec3 result = oklab_to_linear_srgb(lab);
                gl_FragColor = vec4(result, textureColor.a);
            }
        """
    }
}
