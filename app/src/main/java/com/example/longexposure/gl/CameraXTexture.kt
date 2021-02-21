package com.example.longexposure.gl


import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class CameraXTexture(textureUnitNum: Int, private val context: Context) : Texture(textureUnitNum) {

    override val textureType: Int
        get() = GLES11Ext.GL_TEXTURE_EXTERNAL_OES

    override val textureTypeForShader: String
        get() = "samplerExternalOES"

    private var cameraWidth = 0
    private var cameraHeight = 0

    override val width: Int
        get() = cameraWidth

    override val height: Int
        get() = cameraHeight

    var surfaceTexture: SurfaceTexture? = null

    var glSurfaceProvider: GLSurfaceProvider? = null

    override fun create() {
        super.create()

        val cameraTex = IntArray(1)
        GLES20.glGenTextures(1, cameraTex, 0)
        textureId = cameraTex.first()

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        surfaceTexture = SurfaceTexture(textureId)

        glSurfaceProvider = GLSurfaceProvider(ContextCompat.getMainExecutor(context))
    }

    override fun update() {
        super.update()
        surfaceTexture?.updateTexImage()
        surfaceTexture?.getTransformMatrix(stMatrix)
    }

    fun setCameraSize(width: Int, height: Int) {
        cameraWidth = width
        cameraHeight = height
        surfaceTexture?.setDefaultBufferSize(cameraWidth, cameraHeight)
    }

    inner class GLSurfaceProvider(private val executor: Executor) : Preview.SurfaceProvider {
        override fun onSurfaceRequested(request: SurfaceRequest) {
            // Create the surface and attempt to provide it to the camera.
            val surface = Surface(surfaceTexture)
            val size = request.resolution

            setCameraSize(size.width, size.height)

            // Provide the surface and wait for the result to clean up the surface.
            request.provideSurface(surface, {
                executor.execute(it)
            }) {
                surface.release()
            }
        }
    }
}