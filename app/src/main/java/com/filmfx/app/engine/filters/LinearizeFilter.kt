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
            
            void main() {
                highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);
                // Approximate sRGB to Linear conversion
                highp vec3 linearColor = pow(textureColor.rgb, vec3(2.2));
                gl_FragColor = vec4(linearColor, textureColor.a);
            }
        """
    }
}
