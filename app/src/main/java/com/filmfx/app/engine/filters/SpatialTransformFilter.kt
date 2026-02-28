package com.filmfx.app.engine.filters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

class SpatialTransformFilter : GPUImageFilter(
    NO_FILTER_VERTEX_SHADER,
    TRANSFORM_FRAGMENT_SHADER
) {
    private var scaleLocation: Int = -1
    private var offsetLocation: Int = -1
    private var rotationLocation: Int = -1
    private var aspectLocation: Int = -1

    var scale: Float = 1.0f
        set(value) {
            field = value
            setFloat(scaleLocation, value)
        }

    var offsetX: Float = 0.0f
        set(value) {
            field = value
            updateOffset()
        }

    var offsetY: Float = 0.0f
        set(value) {
            field = value
            updateOffset()
        }

    var rotation: Float = 0.0f
        set(value) {
            field = value
            // Convert degrees to radians for GL
            setFloat(rotationLocation, (value * Math.PI / 180.0).toFloat())
        }

    var aspect: Float = 1.0f
        set(value) {
            field = value
            setFloat(aspectLocation, value)
        }

    private fun updateOffset() {
        setPoint(offsetLocation, android.graphics.PointF(offsetX, offsetY))
    }

    override fun onInit() {
        super.onInit()
        scaleLocation = android.opengl.GLES20.glGetUniformLocation(program, "scale")
        offsetLocation = android.opengl.GLES20.glGetUniformLocation(program, "offset")
        rotationLocation = android.opengl.GLES20.glGetUniformLocation(program, "rotation")
        aspectLocation = android.opengl.GLES20.glGetUniformLocation(program, "aspect")
    }

    override fun onInitialized() {
        super.onInitialized()
        scale = scale
        offsetX = offsetX
        offsetY = offsetY
        rotation = rotation
        aspect = aspect
    }

    companion object {
        val TRANSFORM_FRAGMENT_SHADER = """
precision highp float;
varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            
            uniform highp float scale;
            uniform highp vec2 offset;
            uniform highp float rotation; // radians
            uniform highp float aspect;   // viewport aspect ratio (w/h)
            
            void main() {
                // 1. Move to center (-0.5 to 0.5)
                highp vec2 uv = textureCoordinate - 0.5;
                
                // 2. Pan and Scale in SCREEN SPACE (unrotated)
                // Panning: dragging right (+x) means we sample from the left (-x)
                // Panning: dragging down (+y screen) means we sample from higher up (+y GL)
                uv.x = uv.x / scale - offset.x;
                uv.y = uv.y / scale + offset.y;
                
                // 3. Correct for aspect ratio to make space "square" for rotation
                uv.y = uv.y / aspect;
                
                // 4. Rotate
                // clockwise user rotation rotates image clockwise by sampling CCW
                highp float s = sin(rotation);
                highp float c = cos(rotation);
                highp mat2 rotMat = mat2(c, -s, s, c);
                uv = rotMat * uv;
                
                // 5. Revert aspect correction
                uv.y = uv.y * aspect;
                
                // 6. Move back to (0.0 to 1.0)
                uv = uv + 0.5;
                
                // 7. Sampling with edge clipping (Black borders)
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
                } else {
                    gl_FragColor = texture2D(inputImageTexture, uv);
                }
            }
        """.trimIndent()
    }
}
