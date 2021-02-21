package com.example.longexposure.gl

import android.opengl.GLES20
import android.opengl.Matrix

abstract class Texture(val textureUnitNum: Int) {

    var textureId: Int = -1
        protected set
    var stMatrix = FloatArray(16)
        protected set

    abstract val textureType: Int
    abstract val textureTypeForShader: String

    abstract val width: Int
    abstract val height: Int

    var onSizeChanged: (()->Unit)? = null

    open fun create() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + textureUnitNum)
    }

    open fun update() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + textureUnitNum)
    }

    init {
        Matrix.setIdentityM(stMatrix, 0)
    }
}