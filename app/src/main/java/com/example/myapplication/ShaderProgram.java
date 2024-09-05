package com.example.myapplication;

import static android.content.ContentValues.TAG;

import android.opengl.GLES32;
import android.util.Log;

public class ShaderProgram {
    private static final String TAG = "ShaderProgram";
    private int programId;

    public ShaderProgram(String vertexShaderCode, String fragmentShaderCode) {
        if (GLES32.glGetError() != GLES32.GL_NO_ERROR) {
            Log.e(TAG, "OpenGL context error detected before shader creation.");
            return;
        }

        int vertexShader = loadShader(GLES32.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES32.GL_FRAGMENT_SHADER, fragmentShaderCode);

        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Failed to compile shaders.");
            return;
        }

        programId = GLES32.glCreateProgram();
        if (programId == 0) {
            Log.e(TAG, "Error creating shader program.");
            return;
        }

        GLES32.glAttachShader(programId, vertexShader);
        GLES32.glAttachShader(programId, fragmentShader);
        GLES32.glLinkProgram(programId);

        int[] linkStatus = new int[1];
        GLES32.glGetProgramiv(programId, GLES32.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Error linking program: " + GLES32.glGetProgramInfoLog(programId));
            GLES32.glDeleteProgram(programId);
            programId = 0;
        } else {
            Log.i(TAG, "Shader program linked successfully.");
        }

        GLES32.glDeleteShader(vertexShader);
        GLES32.glDeleteShader(fragmentShader);
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES32.glCreateShader(type);
        if (shader == 0) {
            Log.e(TAG, "Error creating shader of type: " + type);
            return 0;
        }

        GLES32.glShaderSource(shader, shaderCode);
        GLES32.glCompileShader(shader);

        int[] compileStatus = new int[1];
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES32.glGetShaderInfoLog(shader));
            GLES32.glDeleteShader(shader);
            shader = 0;
        } else {
            Log.i(TAG, "Shader compiled successfully.");
        }

        return shader;
    }

    public int getProgramId() {
        return programId;
    }
}
