package com.example.longexposure.gl

import android.opengl.GLES20
import android.util.Log

class FrameBufferTexture(
    private var textureWidth: Int,
    private var textureHeight: Int,
    textureUnitNum: Int
) : Texture(textureUnitNum) {

    override val textureType: Int
        get() = GLES20.GL_TEXTURE_2D

    override val textureTypeForShader: String
        get() = "sampler2D"

    override val width: Int
        get() = textureWidth

    override val height: Int
        get() = textureHeight

    private val frameBuffer = IntArray(1)

    override fun create() {
        super.create()

        GLES20.glGenFramebuffers(1, frameBuffer, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0])

        val renderTex = IntArray(1)
        GLES20.glGenTextures(1, renderTex, 0)
        textureId = renderTex.first()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        // check status
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e("FBO", "Framebuffer init failed")
        }
    }

    fun render(renderAction: () -> Unit) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0)
        renderAction()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun reset() {
        GLES20.glDeleteFramebuffers(1, frameBuffer, 0)
        val renderTex = IntArray(1)
        renderTex[0] = textureId
        GLES20.glDeleteTextures(1, renderTex, 0)

        create()
    }

    fun changeSize(width: Int, height: Int) {
        textureHeight = height
        textureWidth = width
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + textureUnitNum)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
    }
}