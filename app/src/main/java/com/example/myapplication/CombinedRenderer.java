package com.example.myapplication;

import android.opengl.GLES11Ext;
import android.opengl.GLES32;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Camera;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

public class CombinedRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "CombinedRenderer";
    private Session session;
    private final Buffer vertexBuffer;
    private final Buffer textureBuffer;

    private int textureId;
    private int cameraPositionHandle;
    private int cameraTextureHandle;
    private int cameraTextureCoordHandle;

    private FloatBuffer pointCloudBuffer;
    private ShaderProgram shaderProgram;
    private ShaderProgram pointCloudShaderProgram;
    private int pointCloudVertexBufferId;
    private int uModelViewProjectionHandle;
    private int pointCloudPositionHandle;
    private int pointCloudColorHandle;
    private float[] modelViewProjectionMatrix = new float[16];
    private long lastPointCloudTimestamp = 0;


    private final float[] vertices = {
            -1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 0.0f
    };

    private final float[] textureCoords = {
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            0.0f, 0.0f
    };


    public CombinedRenderer() {
        vertexBuffer = new Buffer(vertices);
        textureBuffer = new Buffer(textureCoords);
    }

    public void setSession(Session session) {
        this.session = session;
        if (session != null) {
            session.setCameraTextureName(getTextureId());
            Log.i(TAG, "Camera texture name set: " + getTextureId());
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        initCameraFeed();
        initPointCloud();

        if (session != null) {
            session.setCameraTextureName(textureId);
            Log.i(TAG, "Camera texture name set: " + textureId);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (width == 0 || height == 0) {
            Log.e(TAG, "Invalid surface dimensions: width or height is 0");
            return;
        }
        GLES32.glViewport(0, 0, width, height);
    }

    private void initCameraFeed() {
        String vertexShaderCode =
                "attribute vec4 vPosition;" +
                        "attribute vec2 vTexCoord;" +
                        "varying vec2 texCoord;" +
                        "void main() {" +
                        "  gl_Position = vPosition;" +
                        "  texCoord = vTexCoord;" +
                        "}";

        String fragmentShaderCode =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;" +
                        "uniform samplerExternalOES sTexture;" +
                        "varying vec2 texCoord;" +
                        "void main() {" +
                        "  gl_FragColor = texture2D(sTexture, texCoord);" +
                        "}";

        shaderProgram = new ShaderProgram(vertexShaderCode, fragmentShaderCode);

        cameraPositionHandle = GLES32.glGetAttribLocation(shaderProgram.getProgramId(), "vPosition");
        cameraTextureCoordHandle = GLES32.glGetAttribLocation(shaderProgram.getProgramId(), "vTexCoord");
        cameraTextureHandle = GLES32.glGetUniformLocation(shaderProgram.getProgramId(), "sTexture");

        int[] textures = new int[1];
        GLES32.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR);

        Log.i(TAG, "Camera feed rendering initialized successfully.");
    }

    private void initPointCloud() {
        String pointCloudVertexShaderCode =
                "uniform mat4 u_ModelViewProjection;" +
                        "uniform float u_PointSize;" +
                        "attribute vec4 a_Position;" +
                        "void main() {" +
                        "  gl_Position = u_ModelViewProjection * vec4(a_Position.xyz, 1.0);" +
                        "  gl_PointSize = u_PointSize;" +
                        "}";

        String pointCloudFragmentShaderCode =
                "precision mediump float;" +
                        "uniform vec4 u_Color;" +
                        "void main() {" +
                        "  gl_FragColor = u_Color;" +
                        "}";

        pointCloudShaderProgram = new ShaderProgram(pointCloudVertexShaderCode, pointCloudFragmentShaderCode);

        // Verify shader program compilation and linking
        int[] linkStatus = new int[1];
        GLES32.glGetProgramiv(pointCloudShaderProgram.getProgramId(), GLES32.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == GLES32.GL_FALSE) {
            Log.e(TAG, "Error linking shader program: " + GLES32.glGetProgramInfoLog(pointCloudShaderProgram.getProgramId()));
            return; // Exit the method if linking failed
        }

        // Check if the shader program ID is valid
        if (pointCloudShaderProgram.getProgramId() == 0) {
            Log.e(TAG, "Invalid shader program ID.");
            return; // Exit the method if the program ID is invalid
        }

        uModelViewProjectionHandle = GLES32.glGetUniformLocation(pointCloudShaderProgram.getProgramId(), "u_ModelViewProjection");
        int uPointSizeHandle = GLES32.glGetUniformLocation(pointCloudShaderProgram.getProgramId(), "u_PointSize");
        int uColorHandle = GLES32.glGetUniformLocation(pointCloudShaderProgram.getProgramId(), "u_Color");
        pointCloudPositionHandle = GLES32.glGetAttribLocation(pointCloudShaderProgram.getProgramId(), "a_Position");

        int[] buffers = new int[1];
        GLES32.glGenBuffers(1, buffers, 0);
        pointCloudVertexBufferId = buffers[0];

        GLES32.glUseProgram(pointCloudShaderProgram.getProgramId());
        GLES32.glUniform4f(uColorHandle, 31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f);
        GLES32.glUniform1f(uPointSizeHandle, 5.0f);
        GLES32.glUseProgram(0);

        Log.i(TAG, "Point cloud rendering initialized successfully.");
    }


    @Override
    public void onDrawFrame(GL10 gl) {
        if (session == null) {
            Log.e(TAG, "Session is null in onDrawFrame");
            return;
        }
        try {
            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT | GLES32.GL_DEPTH_BUFFER_BIT);

            Frame frame = session.update();
            renderCameraFeed(frame);
            renderPointCloud(frame);

            Log.i(TAG, "Frame drawn successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in onDrawFrame: " + e.getMessage());
        }
    }

    private void renderCameraFeed(Frame frame) {
        // Use the shader program
        GLES32.glUseProgram(shaderProgram.getProgramId());

        // Enable vertex and texture coordinate arrays
        GLES32.glEnableVertexAttribArray(cameraPositionHandle);
        GLES32.glVertexAttribPointer(cameraPositionHandle, 3, GLES32.GL_FLOAT, false, 0, vertexBuffer.getBuffer());

        GLES32.glEnableVertexAttribArray(cameraTextureCoordHandle);
        GLES32.glVertexAttribPointer(cameraTextureCoordHandle, 2, GLES32.GL_FLOAT, false, 0, textureBuffer.getBuffer());

        // Set the active texture and bind it
        GLES32.glUniform1i(cameraTextureHandle, 0);
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0);
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

        // Draw the texture on the rectangle
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4);

        // Disable vertex and texture coordinate arrays
        GLES32.glDisableVertexAttribArray(cameraPositionHandle);
        GLES32.glDisableVertexAttribArray(cameraTextureCoordHandle);
    }

    private void renderPointCloud(Frame frame) {
        try {
            PointCloud pointCloud = frame.acquirePointCloud();
            pointCloudBuffer = pointCloud.getPoints();

            int totalFloats = pointCloudBuffer.capacity();
            int floatsPerPoint = 4; // Assuming x, y, z, and confidence
            int numPoints = totalFloats / floatsPerPoint;

            Log.i(TAG, "Point Cloud Buffer Capacity: " + totalFloats);
            Log.i(TAG, "Number of Feature Points: " + numPoints);

            if (numPoints > 0) {
                GLES32.glUseProgram(pointCloudShaderProgram.getProgramId());
                int programError = GLES32.glGetError();
                Log.i(TAG, "Program Use Error: " + programError);

                GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, pointCloudVertexBufferId);
                int bufferError = GLES32.glGetError();
                Log.i(TAG, "Buffer Bind Error: " + bufferError);

                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(totalFloats * Float.BYTES);
                byteBuffer.order(ByteOrder.nativeOrder());
                FloatBuffer directFloatBuffer = byteBuffer.asFloatBuffer();
                directFloatBuffer.put(pointCloudBuffer);
                directFloatBuffer.position(0);

                GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, totalFloats * Float.BYTES, directFloatBuffer, GLES32.GL_DYNAMIC_DRAW);
                int bufferDataError = GLES32.glGetError();
                Log.i(TAG, "Buffer Data Error: " + bufferDataError);

                // Set the u_ModelViewProjection uniform
                float[] modelViewProjectionMatrix = new float[16];
                getModelViewProjectionMatrix(frame, modelViewProjectionMatrix);
                GLES32.glUniformMatrix4fv(uModelViewProjectionHandle, 1, false, modelViewProjectionMatrix, 0);

                // Set the u_PointSize and u_Color uniforms
                GLES32.glUniform1f(GLES32.glGetUniformLocation(pointCloudShaderProgram.getProgramId(), "u_PointSize"), 5.0f); // Example size
                GLES32.glUniform4f(GLES32.glGetUniformLocation(pointCloudShaderProgram.getProgramId(), "u_Color"), 1.0f, 0.0f, 0.0f, 1.0f); // Example color: red

                GLES32.glVertexAttribPointer(pointCloudPositionHandle, 3, GLES32.GL_FLOAT, false, floatsPerPoint * Float.BYTES, 0);
                GLES32.glEnableVertexAttribArray(pointCloudPositionHandle);

                GLES32.glDrawArrays(GLES32.GL_POINTS, 0, numPoints);
                int drawError = GLES32.glGetError();
                Log.i(TAG, "Draw Error: " + drawError);

                GLES32.glDisableVertexAttribArray(pointCloudPositionHandle);

            } else {
                Log.w(TAG, "Point cloud is empty, skipping rendering.");
            }

            pointCloud.release();
        } catch (Exception e) {
            Log.e(TAG, "Exception in renderPointCloud: " + e.getMessage());
        }
    }

    private void getModelViewProjectionMatrix(Frame frame, float[] modelViewProjectionMatrix) {
        // Initialize matrices
        float[] modelMatrix = new float[16];
        float[] viewMatrix = new float[16];
        float[] projectionMatrix = new float[16];
        float[] tempMatrix = new float[16];

        // Get the current AR frame camera
        Camera camera = frame.getCamera();

        // Retrieve the view and projection matrices from the camera
        camera.getViewMatrix(viewMatrix, 0);
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

        // For this example, we assume an identity model matrix
        Matrix.setIdentityM(modelMatrix, 0);

        // Multiply view and model matrices to get the Model-View matrix
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Multiply projection and Model-View matrices to get the Model-View-Projection matrix
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, tempMatrix, 0);
    }

    private void logPointCloudData(FloatBuffer buffer, int numPoints, int floatsPerPoint) {
        buffer.position(0);
        for (int i = 0; i < numPoints; i++) {
            float x = buffer.get();
            float y = buffer.get();
            float z = buffer.get();
            float confidence = buffer.get();
            Log.d(TAG, "Point " + i + ": (" + x + ", " + y + ", " + z + "), Confidence: " + confidence);
        }
    }

    public int getTextureId() {
        return textureId;
    }
}