package com.example.longexposure.gl


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.opengl.GLES20
import android.opengl.GLException
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class Renderer(private val context: Context, private val onCameraTextureCreated: (CameraXTexture) -> Unit) : GLSurfaceView.Renderer {

    enum class Mode {
        LongExposure,
        Preview
    }

    private var toScreenTexSamplerHandle: Int = 0
    private var toScreenTexCoordHandle: Int = 0
    private var toScreenPosCoordHandle: Int = 0

    private var previewTexSamplerHandle: Int = 0
    private var previewTexCoordHandle: Int = 0
    private var previewPosCoordHandle: Int = 0
    private var previewStMatrix: Int = 0

    private var toTextureStMatrix: Int = 0
    private var toTextureTexSamplerHandle: Int = 0
    private var toTextureOldTexSamplerHandle: Int = 0
    private var toTextureTexCoordHandle: Int = 0
    private var toTexturePosCoordHandle: Int = 0

    private lateinit var texVerticesBuffer: FloatBuffer
    private lateinit var texturePosVerticesBuffer: FloatBuffer

    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    private var toScreenProgram: Int = 0
    private var toTextureProgram: Int = 0
    private var previewProgram: Int = 0

    private val cameraXTexture = CameraXTexture(CAMERA_TEXTURE_UNIT_NUM, context)
    private val renderTexture = FrameBufferTexture(640, 480, RENDER_BUFFER_TEXTURE_UNIT_NUM)
    private val prevFrameTexture = FrameBufferTexture(640, 480, PREV_FRAME_TEXTURE_UNIT_NUM)

    private var mode = Mode.Preview
    @Volatile
    private var newMode: Mode? = null

    private val previewVertexShaderSource = """
attribute vec4 a_position;
attribute vec2 a_texcoord;
varying vec2 v_texcoord;
void main() {
  gl_Position = a_position;
  v_texcoord = a_texcoord;
}
"""

    private val previewFragmentShaderSource = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform texType0 tex_sampler;
uniform mat4 stMatrix;
varying vec2 v_texcoord;
void main() {
    gl_FragColor = texture2D(tex_sampler, (stMatrix * vec4(v_texcoord.xy, 0, 1)).xy);
}
"""

    private val toScreenVertexShaderSource = previewVertexShaderSource

    private val toScreenFragmentShaderSource = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform texType0 tex_sampler;
varying vec2 v_texcoord;
void main() {
    gl_FragColor = texture2D(tex_sampler, v_texcoord);
}
"""

    private val toTextureVertexShaderSource = previewVertexShaderSource

    private val toTextureFragmentShaderSource = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform mat4 stMatrix;
uniform texType0 tex_sampler;
uniform texType1 old_tex_sampler;
varying vec2 v_texcoord;
void main() {
    vec4 color = texture2D(tex_sampler, (stMatrix * vec4(v_texcoord.xy, 0, 1)).xy);
    vec4 oldColor = texture2D(old_tex_sampler, v_texcoord);
    float oldBrightness = oldColor.r * 0.2126 + oldColor.g * 0.7152 + oldColor.b * 0.0722 + oldColor.a;
    float newBrightness = color.r * 0.2126 + color.g * 0.7152 + color.b * 0.0722 + color.a;
    if (newBrightness > oldBrightness) {
        gl_FragColor = mix(color, oldColor, 0.01);
    } else {
        gl_FragColor = mix(oldColor, color, 0.01);
    }
}
"""

    private val texVertices = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f)

    private val posVertices = floatArrayOf(-1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f)

    private val bitmapRequests = mutableListOf<(Bitmap)->Unit>()

    fun requestBitmap(callback: (Bitmap)->Unit) {
        bitmapRequests.add(callback)
    }

    fun setMode(mode: Mode) {
        newMode = mode
    }

    private fun updateMode() {
        newMode?.let {
            if (mode != it) {
                mode = it
                newMode = null
                prevFrameTexture.reset()
                renderTexture.reset()
            }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // create texture for framebuffer
        renderTexture.create()

        // create texture for previous frame
        prevFrameTexture.create()

        // create texture for camera
        cameraXTexture.create()
        onCameraTextureCreated(cameraXTexture)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        // create toScreenProgram
        toScreenProgram = Gl.createProgram(toScreenVertexShaderSource, toScreenFragmentShaderSource, listOf(renderTexture))

        // create toTextureProgram
        toTextureProgram = Gl.createProgram(toTextureVertexShaderSource, toTextureFragmentShaderSource, listOf(cameraXTexture, prevFrameTexture))

        // create previewProgram
        previewProgram = Gl.createProgram(previewVertexShaderSource, previewFragmentShaderSource, listOf(cameraXTexture))

        // bind toScreenProgram handlers
        toScreenTexSamplerHandle = GLES20.glGetUniformLocation(toScreenProgram, "tex_sampler")
        toScreenTexCoordHandle = GLES20.glGetAttribLocation(toScreenProgram, "a_texcoord")
        toScreenPosCoordHandle = GLES20.glGetAttribLocation(toScreenProgram, "a_position")

        // bind toTextureProgram handlers
        toTextureStMatrix = GLES20.glGetUniformLocation(toTextureProgram, "stMatrix")
        toTextureTexSamplerHandle = GLES20.glGetUniformLocation(toTextureProgram, "tex_sampler")
        toTextureOldTexSamplerHandle = GLES20.glGetUniformLocation(toTextureProgram,
            "old_tex_sampler")
        toTextureTexCoordHandle = GLES20.glGetAttribLocation(toTextureProgram, "a_texcoord")
        toTexturePosCoordHandle = GLES20.glGetAttribLocation(toTextureProgram, "a_position")

        // bind previewProgram handlers
        previewStMatrix = GLES20.glGetUniformLocation(previewProgram, "stMatrix")
        previewTexSamplerHandle = GLES20.glGetUniformLocation(previewProgram, "tex_sampler")
        previewTexCoordHandle = GLES20.glGetAttribLocation(previewProgram, "a_texcoord")
        previewPosCoordHandle = GLES20.glGetAttribLocation(previewProgram, "a_position")

        // Setup coordinate buffers
        texVerticesBuffer = ByteBuffer.allocateDirect(
            texVertices.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        texVerticesBuffer.put(texVertices).position(0)
        texturePosVerticesBuffer = ByteBuffer.allocateDirect(
            posVertices.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        texturePosVerticesBuffer.put(posVertices).position(0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Gl.checkError("glViewport")

        GLES20.glDisable(GLES20.GL_BLEND)
        viewWidth = width
        viewHeight = height

        renderTexture.changeSize(width, height)
        prevFrameTexture.changeSize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        cameraXTexture.update()

        updateMode()

        when (mode) {
            Mode.LongExposure -> renderLongExposure()
            Mode.Preview -> renderPreview()
        }

        if (bitmapRequests.isNotEmpty()) {
            saveBitmap(Rect(0, 0, viewWidth, viewHeight), bitmapRequests)
            bitmapRequests.clear()
        }
    }

    private fun renderPreview() {
        // render to screen
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        GLES20.glUseProgram(previewProgram)
        Gl.checkError("glUseProgram")
        GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        renderTexture(cameraXTexture, previewTexCoordHandle, previewPosCoordHandle, previewTexSamplerHandle, previewStMatrix)
    }

    private fun renderLongExposure() {
        // render to buffer
        renderTexture.render {
            GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(toTextureProgram)
            Gl.checkError("glUseProgram")
            renderTextureMix(cameraXTexture, prevFrameTexture, toTextureTexCoordHandle, toTexturePosCoordHandle, toTextureTexSamplerHandle, toTextureOldTexSamplerHandle, toTextureStMatrix)
        }

        // copy from buffer texture to previous frame texture
        prevFrameTexture.render {
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(toScreenProgram)
            Gl.checkError("glUseProgram")
            renderTexture(renderTexture, toScreenTexCoordHandle, toScreenPosCoordHandle, toScreenTexSamplerHandle, null)
        }

        // render to screen
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        GLES20.glUseProgram(toScreenProgram)
        Gl.checkError("glUseProgram")
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        renderTexture(renderTexture, toScreenTexCoordHandle, toScreenPosCoordHandle, toScreenTexSamplerHandle, null)
    }

    private fun renderTextureMix(texture: Texture, oldTexture: Texture, texCoord: Int, posCoord: Int, texSampler: Int, oldTexSampler: Int, stMatrixHandler: Int) {
        // Set the vertex attributes
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false,
            0, texVerticesBuffer)
        GLES20.glEnableVertexAttribArray(texCoord)
        GLES20.glVertexAttribPointer(posCoord, 2, GLES20.GL_FLOAT, false,
            0, texturePosVerticesBuffer)
        GLES20.glEnableVertexAttribArray(posCoord)

        GLES20.glUniformMatrix4fv(stMatrixHandler, 1, false, cameraXTexture.stMatrix, 0)
        Gl.checkError("vertex attribute setup")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texture.textureUnitNum)
        Gl.checkError("glActiveTexture")
        GLES20.glBindTexture(texture.textureType, texture.textureId)
        Gl.checkError("glBindTexture")
        GLES20.glUniform1i(texSampler, texture.textureUnitNum)
        Gl.checkError("glUniform1i")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + oldTexture.textureUnitNum)
        Gl.checkError("glActiveTexture")
        GLES20.glBindTexture(oldTexture.textureType, oldTexture.textureId)
        Gl.checkError("glBindTexture")
        GLES20.glUniform1i(oldTexSampler, oldTexture.textureUnitNum)
        Gl.checkError("glUniform1i")

        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun renderTexture(texture: Texture, texCoord: Int, posCoord: Int, texSampler: Int, stMatrixHandler: Int?) {
        // Set the vertex attributes
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false,
            0, texVerticesBuffer)
        GLES20.glEnableVertexAttribArray(texCoord)
        GLES20.glVertexAttribPointer(posCoord, 2, GLES20.GL_FLOAT, false,
            0, texturePosVerticesBuffer)
        GLES20.glEnableVertexAttribArray(posCoord)

        stMatrixHandler?.let {
            GLES20.glUniformMatrix4fv(stMatrixHandler, 1, false, cameraXTexture.stMatrix, 0)
        }

        Gl.checkError("vertex attribute setup")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texture.textureUnitNum)
        Gl.checkError("glActiveTexture")
        GLES20.glBindTexture(texture.textureType, texture.textureId)
        Gl.checkError("glBindTexture")
        GLES20.glUniform1i(texSampler, texture.textureUnitNum)
        Gl.checkError("glUniform1i")

        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun saveBitmap(frame: Rect, onBitmapSaved: List<(Bitmap) -> Unit>) {
        val x = frame.left
        val y = frame.top
        val w = frame.width()
        val h = frame.height()

        val bitmapBuffer = IntArray(w * h)
        val bitmapSource = IntArray(w * h)
        val intBuffer: IntBuffer = IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)
        try {
            GLES20.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer)
            var offset1: Int
            var offset2: Int
            for (i in 0 until h) {
                offset1 = i * w
                offset2 = (h - i - 1) * w
                for (j in 0 until w) {
                    val texturePixel = bitmapBuffer[offset1 + j]
                    val blue = texturePixel shr 16 and 0xff
                    val red = texturePixel shl 16 and 0x00ff0000
                    val pixel = texturePixel and -0xff0100 or red or blue
                    bitmapSource[offset2 + j] = pixel
                }
            }

            val bitmap = Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
            for (callback in onBitmapSaved) {
                callback.invoke(bitmap)
            }
        } catch (e: GLException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val FLOAT_SIZE_BYTES = 4
        private const val CAMERA_TEXTURE_UNIT_NUM = 1
        private const val PREV_FRAME_TEXTURE_UNIT_NUM = 2
        private const val RENDER_BUFFER_TEXTURE_UNIT_NUM = 0
    }

}