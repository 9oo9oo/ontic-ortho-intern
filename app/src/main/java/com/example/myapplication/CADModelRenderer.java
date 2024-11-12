package com.example.myapplication;

import android.content.Context;
import android.opengl.GLES32;
import android.opengl.Matrix;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code CADModelRenderer} class is responsible for rendering a CAD model using OpenGL ES 3.2.
 * It sets up shaders, buffers, and framebuffers to render the model from multiple viewpoints.
 */
public class CADModelRenderer {

    private static final String TAG = "CADModelRenderer";

    private final Context context;

    // OpenGL handles
    private int frameBuffer;
    private int renderTexture;
    private int depthBuffer;
    private int shaderProgram;
    private int vaoId;
    private int vertexVboId;
    private int normalVboId;
    private int indexBufferId;

    // Buffers for vertex data
    private FloatBuffer vertexBuffer;
    private FloatBuffer normalBuffer;
    private IntBuffer indexBuffer;

    // Shader attribute and uniform locations
    private int mvpMatrixHandle;
    private static final int POSITION_ATTRIBUTE = 0;
    private static final int NORMAL_ATTRIBUTE = 1;

    // Model data
    private final float[] vertices;
    private final float[] normals;
    private final int[] indices;
    private final int numIndices;

    // Matrices
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    // Viewport dimensions
    private final int width = 1024;
    private final int height = 1024;

    /**
     * Constructs a new {@code CADModelRenderer} with the given model data.
     *
     * @param context  The application context.
     * @param vertices The array of vertex coordinates.
     * @param normals  The array of normal vectors.
     * @param faces    The list of face indices.
     */
    public CADModelRenderer(Context context, float[] vertices, float[] normals, List<int[]> faces) {
        this.context = context;
        this.vertices = vertices;
        this.normals = normals;

        // Convert face indices to a flat int array
        numIndices = faces.size() * 3;
        indices = new int[numIndices];
        int idx = 0;
        for (int[] face : faces) {
            indices[idx++] = face[0] - 1;
            indices[idx++] = face[1] - 1;
            indices[idx++] = face[2] - 1;
        }
    }

    /**
     * Initializes OpenGL resources such as shaders, buffers, and framebuffers.
     * Should be called after an OpenGL context has been created.
     */
    public void initOpenGL() {
        initBuffers();
        initShaders();
        initFrameBuffer();
    }

    /**
     * Initializes vertex buffers and uploads data to the GPU.
     */
    private void initBuffers() {
        // Create buffers for vertex data
        vertexBuffer = ByteBuffer.allocateDirect(vertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(vertices).position(0);

        // Initialize normal buffer
        normalBuffer = ByteBuffer.allocateDirect(normals.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        normalBuffer.put(normals).position(0);

        // Initialize index buffer
        indexBuffer = ByteBuffer.allocateDirect(indices.length * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        indexBuffer.put(indices).position(0);

        // Generate and bind VAO (Vertex Array Object)
        int[] vaos = new int[1];
        GLES32.glGenVertexArrays(1, vaos, 0);
        vaoId = vaos[0];
        GLES32.glBindVertexArray(vaoId);

        // Generate VBOs (Vertex Buffer Objects) for vertices, normals, and indices
        int[] vbos = new int[3];
        GLES32.glGenBuffers(3, vbos, 0);
        vertexVboId = vbos[0];
        normalVboId = vbos[1];
        indexBufferId = vbos[2];

        // Bind and set vertex buffer data
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vertexVboId);
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, vertexBuffer, GLES32.GL_STATIC_DRAW);
        GLES32.glEnableVertexAttribArray(0);
        GLES32.glVertexAttribPointer(0, 3, GLES32.GL_FLOAT, false, 0, 0);

        // Bind and set normal buffer data
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, normalVboId);
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, normals.length * Float.BYTES, normalBuffer, GLES32.GL_STATIC_DRAW);
        GLES32.glEnableVertexAttribArray(1); // location = 1
        GLES32.glVertexAttribPointer(1, 3, GLES32.GL_FLOAT, false, 0, 0);

        // Bind and set index buffer data
        GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
        GLES32.glBufferData(GLES32.GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, indexBuffer, GLES32.GL_STATIC_DRAW);

        // Unbind VAO and buffers to prevent accidental modification
        GLES32.glBindVertexArray(0);
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0);
        GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Initializes shaders and links them into a shader program.
     */
    private void initShaders() {
        // Vertex Shader
        String vertexShaderCode =
                "#version 320 es\n" +
                        "layout(location = 0) in vec3 aPosition;" +
                        "layout(location = 1) in vec3 aNormal;" +
                        "uniform mat4 uMVPMatrix;" +
                        "out vec3 vNormal;" +
                        "void main() {" +
                        "  gl_Position = uMVPMatrix * vec4(aPosition, 1.0);" +
                        "  vNormal = aNormal;" +
                        "}";

        // Fragment Shader
        String fragmentShaderCode =
                "#version 320 es\n" +
                        "precision mediump float;" +
                        "in vec3 vNormal;" +
                        "out vec4 fragColor;" +
                        "void main() {" +
                        "    vec3 ambientLight = vec3(0.2, 0.2, 0.2);" +
                        "    vec3 lightDir1 = normalize(vec3(0.0, 0.0, 1.0));" +
                        "    vec3 lightDir2 = normalize(vec3(1.0, 1.0, 1.0));" +
                        "    float diff1 = max(dot(normalize(vNormal), lightDir1), 0.0);" +
                        "    float diff2 = max(dot(normalize(vNormal), lightDir2), 0.0);" +
                        "    vec3 diffuse = (diff1 + diff2) * vec3(0.4, 0.4, 0.4);" +
                        "    vec3 color = ambientLight + diffuse;" +
                        "    fragColor = vec4(color, 1.0);" +
                        "}";

        // Compile shaders
        int vertexShader = loadShader(GLES32.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES32.GL_FRAGMENT_SHADER, fragmentShaderCode);

        // Create and link shader program
        shaderProgram = GLES32.glCreateProgram();
        GLES32.glAttachShader(shaderProgram, vertexShader);
        GLES32.glAttachShader(shaderProgram, fragmentShader);
        GLES32.glLinkProgram(shaderProgram);

        // Check for linking errors
        int[] linkStatus = new int[1];
        GLES32.glGetProgramiv(shaderProgram, GLES32.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Shader program linking failed: " + GLES32.glGetProgramInfoLog(shaderProgram));
            GLES32.glDeleteProgram(shaderProgram);
            shaderProgram = 0;
        } else {
            // Get uniform and attribute locations
            mvpMatrixHandle = GLES32.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        }
    }

    /**
     * Initializes the framebuffer, render texture, and depth buffer for off-screen rendering.
     */
    private void initFrameBuffer() {
        // Generate frame buffer
        int[] fb = new int[1];
        GLES32.glGenFramebuffers(1, fb, 0);
        frameBuffer = fb[0];

        // Generate texture to render to
        int[] tex = new int[1];
        GLES32.glGenTextures(1, tex, 0);
        renderTexture = tex[0];
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, renderTexture);
        GLES32.glTexImage2D(GLES32.GL_TEXTURE_2D, 0, GLES32.GL_RGBA,
                width, height, 0, GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, null);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR);

        // Generate depth buffer
        int[] rb = new int[1];
        GLES32.glGenRenderbuffers(1, rb, 0);
        depthBuffer = rb[0];
        GLES32.glBindRenderbuffer(GLES32.GL_RENDERBUFFER, depthBuffer);
        GLES32.glRenderbufferStorage(GLES32.GL_RENDERBUFFER, GLES32.GL_DEPTH_COMPONENT16, width, height);

        // Attach texture and depth buffer to frame buffer
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, frameBuffer);
        GLES32.glFramebufferTexture2D(GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0,
                GLES32.GL_TEXTURE_2D, renderTexture, 0);
        GLES32.glFramebufferRenderbuffer(GLES32.GL_FRAMEBUFFER, GLES32.GL_DEPTH_ATTACHMENT,
                GLES32.GL_RENDERBUFFER, depthBuffer);

        // Check framebuffer completeness
        int status = GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER);
        if (status != GLES32.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Frame buffer is not complete: " + status);
        }

        // Unbind frame buffer
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0);
    }

    /**
     * Compiles a shader of the given type with the provided source code.
     *
     * @param type       The type of shader (vertex or fragment).
     * @param shaderCode The GLSL source code of the shader.
     * @return The handle to the compiled shader, or 0 if compilation failed.
     */
    private int loadShader(int type, String shaderCode) {
        int shader = GLES32.glCreateShader(type);
        if (shader == 0) {
            Log.e(TAG, "Error creating shader.");
            return 0;
        }

        // Attach shader source code and compile
        GLES32.glShaderSource(shader, shaderCode);
        GLES32.glCompileShader(shader);

        // Check for compilation errors
        int[] compiled = new int[1];
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String error = GLES32.glGetShaderInfoLog(shader);
            Log.e(TAG, "Shader compilation failed: " + error);
            GLES32.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    /**
     * Renders the model to an image using the provided view and projection matrices.
     *
     * @param viewMatrix       The view matrix.
     * @param projectionMatrix The projection matrix.
     * @return A {@link Mat} object containing the rendered image.
     */
    public Mat renderModelToImage(float[] viewMatrix, float[] projectionMatrix) {
        // Bind frame buffer
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, frameBuffer);

        // Set viewport and clear buffers
        GLES32.glViewport(0, 0, width, height);
        GLES32.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES32.glEnable(GLES32.GL_DEPTH_TEST);
        GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT | GLES32.GL_DEPTH_BUFFER_BIT);

        // Use shader program
        GLES32.glUseProgram(shaderProgram);

        // Compute MVP matrix
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);

        // Pass MVP matrix to shader
        GLES32.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        // Bind the VAO and draw the model
        GLES32.glBindVertexArray(vaoId);
        GLES32.glDrawElements(GLES32.GL_TRIANGLES, numIndices, GLES32.GL_UNSIGNED_INT, 0);

        // Unbind VAO
        GLES32.glBindVertexArray(0);

        // Read pixels from frame buffer
        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder());
        GLES32.glReadPixels(0, 0, width, height, GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, pixelBuffer);

        // Reset OpenGL state
        GLES32.glDisable(GLES32.GL_DEPTH_TEST);
        GLES32.glDisable(GLES32.GL_CULL_FACE);

        // Unbind frame buffer
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0);

        // Convert ByteBuffer to Mat
        Mat renderedImage = new Mat(height, width, CvType.CV_8UC4);
        byte[] pixelData = new byte[width * height * 4];
        pixelBuffer.position(0);
        pixelBuffer.get(pixelData);
        renderedImage.put(0, 0, pixelData);

        // Flip the image vertically to match OpenCV coordinate system
        Core.flip(renderedImage, renderedImage, 0);

        // Convert RGBA to RGB
        Imgproc.cvtColor(renderedImage, renderedImage, Imgproc.COLOR_RGBA2RGB);

        return renderedImage;
    }

    /**
     * Renders the CAD model from multiple viewpoints and returns the rendered images.
     *
     * @return A list of {@link Mat} objects containing the rendered images.
     */
    public List<Mat> renderFromViewpoints() {
        // Set up projection matrix
        float[] projectionMatrix = new float[16];
        float aspectRatio = 1.0f;
        float fovY = 45.0f;
        float near = 1.0f;
        float far = 10.0f;
        Matrix.perspectiveM(projectionMatrix, 0, fovY, aspectRatio, near, far);

        List<Mat> renderedImages = new ArrayList<>();

        // Render the model from angles 0 to 315 degrees in 45-degree increments
        for (float angle = 0; angle < 360; angle += 45) {
            // Set up view matrix
            float[] viewMatrix = new float[16];
            Matrix.setLookAtM(viewMatrix, 0,
                    0.0f, 0.0f, 5.0f,    // Camera position
                    0.0f, 0.0f, 0.0f,    // Look-at point
                    0.0f, 1.0f, 0.0f);    // Up vector

            // Set up model matrix for scaling and rotation
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.scaleM(modelMatrix, 0, 0.01f , 0.01f , 0.01f );
            Matrix.rotateM(modelMatrix, 0, angle, 0.0f, 1.0f, 0.0f);

            // Render the model to an image and add it to the list
            Mat image = renderModelToImage(viewMatrix, projectionMatrix);
            renderedImages.add(image);
        }

        return renderedImages;
    }

    /**
     * Releases OpenGL resources. Should be called when the renderer is no longer needed.
     */
    public void release() {
        GLES32.glDeleteProgram(shaderProgram);
        GLES32.glDeleteFramebuffers(1, new int[]{frameBuffer}, 0);
        GLES32.glDeleteTextures(1, new int[]{renderTexture}, 0);
        GLES32.glDeleteRenderbuffers(1, new int[]{depthBuffer}, 0);
        GLES32.glDeleteVertexArrays(1, new int[]{vaoId}, 0);
        GLES32.glDeleteBuffers(3, new int[]{vertexVboId, normalVboId, indexBufferId}, 0);
    }
}
