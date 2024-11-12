package com.example.myapplication;

import android.opengl.GLES32;
import android.util.Log;

/**
 * The {@code ShaderProgram} class encapsulates the creation, compilation,
 * and linking of OpenGL ES shaders into a shader program. It provides
 * methods to compile shaders from source code and to retrieve the program ID.
 */
public class ShaderProgram {
    private static final String TAG = "ShaderProgram";

    // OpenGL shader program ID
    private int programId;

    /**
     * Constructs a new {@code ShaderProgram} by compiling and linking the provided
     * vertex and fragment shader source code.
     *
     * @param vertexShaderCode   The GLSL code for the vertex shader.
     * @param fragmentShaderCode The GLSL code for the fragment shader.
     */
    public ShaderProgram(String vertexShaderCode, String fragmentShaderCode) {
        // Check for OpenGL errors before shader creation
        if (GLES32.glGetError() != GLES32.GL_NO_ERROR) {
            Log.e(TAG, "OpenGL context error detected before shader creation.");
            return;
        }

        // Compile the vertex shader
        int vertexShader = loadShader(GLES32.GL_VERTEX_SHADER, vertexShaderCode);
        // Compile the fragment shader
        int fragmentShader = loadShader(GLES32.GL_FRAGMENT_SHADER, fragmentShaderCode);

        // Create a new shader program
        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Failed to compile shaders.");
            return;
        }

        // Attach the compiled shaders to the program
        programId = GLES32.glCreateProgram();
        if (programId == 0) {
            Log.e(TAG, "Error creating shader program.");
            return;
        }

        // Attach the compiled shaders to the program
        GLES32.glAttachShader(programId, vertexShader);
        GLES32.glAttachShader(programId, fragmentShader);

        // Link the shader program
        GLES32.glLinkProgram(programId);

        // Check for linking errors
        int[] linkStatus = new int[1];
        GLES32.glGetProgramiv(programId, GLES32.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Error linking program: " + GLES32.glGetProgramInfoLog(programId));
            GLES32.glDeleteProgram(programId);
            programId = 0;
        } else {
            Log.i(TAG, "Shader program linked successfully.");
        }

        // Shaders can be deleted after linking
        GLES32.glDeleteShader(vertexShader);
        GLES32.glDeleteShader(fragmentShader);
    }

    /**
     * Compiles a shader of the given type with the provided source code.
     *
     * @param type       The type of shader to be created (vertex or fragment).
     * @param shaderCode The GLSL source code of the shader.
     * @return The shader ID, or 0 if compilation failed.
     */
    private int loadShader(int type, String shaderCode) {
        // Create a new shader object
        int shader = GLES32.glCreateShader(type);
        if (shader == 0) {
            Log.e(TAG, "Error creating shader of type: " + type);
            return 0;
        }

        // Attach the shader source code and compile
        GLES32.glShaderSource(shader, shaderCode);
        GLES32.glCompileShader(shader);

        // Check for compilation errors
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

    /**
     * Returns the OpenGL program ID associated with this shader program.
     *
     * @return The program ID.
     */
    public int getProgramId() {
        return programId;
    }
}
