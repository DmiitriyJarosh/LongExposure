package com.example.longexposure.gl

import android.opengl.GLES20
import android.util.Log

object Gl {

    private const val TAG = "GL"

    fun createProgram(shaders: List<Int>): Int {
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            return 0
        }

        shaders.forEach {
            GLES20.glAttachShader(program, it)
            checkError("glAttachShader")
        }

        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Could not link program: ")
            Log.e(TAG, GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    fun createProgram(vertexShaderSource: String, fragmentShaderSource: String, textures: List<Texture>? = null): Int {
        val fragmentShaderSubstitution = if (textures != null && textures.isNotEmpty()) {
            val textureRegex = "texType\\d+".toRegex()
            fragmentShaderSource.replace(textureRegex) {
                val firstDigitPosition = it.value.indexOfFirst(Char::isDigit)
                if (firstDigitPosition == -1) {
                    Log.e(TAG, "'texType' have to be followed by num in shader source!")
                    return@replace ""
                }
                val textureNum = it.value.subSequence(firstDigitPosition, it.value.length).toString().toInt()

                if (textureNum >= textures.size) {
                    Log.e(TAG, "'texType' index have to be in range of textures list indices!")
                    return@replace ""
                }
                return@replace textures[textureNum].textureTypeForShader
            }
        } else {
            fragmentShaderSource
        }

        if (fragmentShaderSubstitution.isEmpty()) {
            return 0
        }

        val vertexShader: Int = createShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader: Int = createShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSubstitution)

        return createProgram(listOf(vertexShader, fragmentShader))
    }

    fun createShader(shaderType: Int, shaderSource: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        if (shader == 0) {
            return 0
        }
        GLES20.glShaderSource(shader, shaderSource)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Could not compile shader $shaderType:")
            Log.e(TAG, GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun checkError(op: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
        }
    }
}