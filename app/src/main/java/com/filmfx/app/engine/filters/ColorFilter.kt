package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

class ColorFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    COLOR_FRAGMENT_SHADER
) {
    private var temperatureLocation: Int = -1
    private var tintLocation: Int = -1
    private var saturationLocation: Int = -1
    private var richnessLocation: Int = -1
    private var subtractiveSatLocation: Int = -1

    var temperature: Float = 6000.0f
        set(value) {
            field = value
            setFloat(temperatureLocation, value)
        }

    var tint: Float = 0.0f
        set(value) {
            field = value
            setFloat(tintLocation, value)
        }

    var saturation: Float = 1.0f
        set(value) {
            field = value
            setFloat(saturationLocation, value)
        }

    var richness: Float = 0.0f
        set(value) {
            field = value
            setFloat(richnessLocation, value)
        }

    var subtractiveSat: Float = 0.0f
        set(value) {
            field = value
            setFloat(subtractiveSatLocation, value)
        }

    override fun onInit() {
        super.onInit()
        temperatureLocation = android.opengl.GLES20.glGetUniformLocation(program, "temperature")
        tintLocation = android.opengl.GLES20.glGetUniformLocation(program, "tint")
        saturationLocation = android.opengl.GLES20.glGetUniformLocation(program, "saturation")
        richnessLocation = android.opengl.GLES20.glGetUniformLocation(program, "richness")
        subtractiveSatLocation = android.opengl.GLES20.glGetUniformLocation(program, "subtractiveSat")
    }

    override fun onInitialized() {
        super.onInitialized()
        temperature = temperature
        tint = tint
        saturation = saturation
        richness = richness
        subtractiveSat = subtractiveSat
    }

    companion object {
        val COLOR_FRAGMENT_SHADER = """
precision highp float;
varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;

            uniform highp float temperature;
            uniform highp float tint;
            uniform highp float saturation;
            uniform highp float richness;
            uniform highp float subtractiveSat;

            // Oklab conversion matrices
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

            void main() {
                highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                
                // Un-premultiply alpha for math correctness
                highp vec3 rgb = textureColor.rgb / max(textureColor.a, 0.0001);
                
                // 1. Temperature & Tint
                highp vec3 illuminant = kelvinToRGB(temperature);
                highp vec3 reference = kelvinToRGB(6000.0);
                // Tint adds to green channel (-100 to 100 mapped to -0.1 to 0.1)
                illuminant.g *= (1.0 + (tint / 100.0) * 0.1); 
                
                highp vec3 correction = reference / max(illuminant, vec3(0.0001));
                rgb *= correction;
                
                // Convert to Oklab
                highp vec3 lab = linear_srgb_to_oklab(rgb);
                
                // 2. Richness (boost saturation only in midtones/shadows)
                highp float richnessWeight = smoothstep(0.1, 0.3, lab.x) * (1.0 - smoothstep(0.6, 0.9, lab.x));
                lab.y *= 1.0 + (richness * richnessWeight);
                lab.z *= 1.0 + (richness * richnessWeight);
                
                // 3. Global Saturation
                lab.y *= saturation;
                lab.z *= saturation;
                
                // 4. Subtractive Saturation (Film Density)
                // Oklab chroma is typically 0.0 to 0.3. Subtract heavily from L for a dense film look.
                highp float chroma = length(vec2(lab.y, lab.z));
                lab.x = lab.x - (chroma * subtractiveSat * 1.5);
                
                // Convert back
                highp vec3 result = oklab_to_linear_srgb(lab);
                
                // Re-premultiply alpha (Pipeline remains linear)
                gl_FragColor = vec4(result * textureColor.a, textureColor.a);
            }
        """.trimIndent()
    }
}
