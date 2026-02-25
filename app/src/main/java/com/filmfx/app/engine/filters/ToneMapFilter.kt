package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

class ToneMapFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    TONE_MAP_FRAGMENT_SHADER
) {
    companion object {
        const val TONE_MAP_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            
            // ACES filmic tone mapping curve
            highp vec3 ACESFilm(highp vec3 x) {
                highp float a = 2.51;
                highp float b = 0.03;
                highp float c = 2.43;
                highp float d = 0.59;
                highp float e = 0.14;
                return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
            }
            
            void main() {
                highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                
                // 1. Tone map (linear to linear but compressed highlights)
                highp vec3 toneMapped = ACESFilm(textureColor.rgb);
                
                // 2. Linear to sRGB conversion (gamma 2.2 approximate)
                highp vec3 srgbColor = pow(toneMapped, vec3(1.0 / 2.2));
                
                gl_FragColor = vec4(srgbColor, textureColor.a);
            }
        """
    }
}
