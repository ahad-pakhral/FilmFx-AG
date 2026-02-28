package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

class LinearizeFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    LINEARIZE_FRAGMENT_SHADER
) {
    companion object {
        const val LINEARIZE_FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            
            highp vec3 sRGB_to_Linear(highp vec3 c) {
                highp vec3 lower = c / 12.92;
                highp vec3 upper = pow((c + 0.055) / 1.055, vec3(2.4));
                return mix(lower, upper, step(vec3(0.04045), c));
            }

            void main() {
                highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                // Official sRGB to Linear conversion (Piecewise with Linear Toe)
                highp vec3 linearColor = sRGB_to_Linear(textureColor.rgb);
                gl_FragColor = vec4(linearColor, textureColor.a);
            }
        """
    }
}
