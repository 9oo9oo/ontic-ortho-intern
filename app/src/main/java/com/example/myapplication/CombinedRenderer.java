package com.example.myapplication;

import android.opengl.GLES11Ext;
import android.opengl.GLES32;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.NotYetAvailableException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import android.media.Image;

import org.opencv.core.Mat;
import org.opencv.core.Point;

public class CombinedRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "CombinedRenderer";
    private Session session;

    private final Buffer vertexBuffer;
    private final Buffer textureBuffer;

    private int textureId;
    private int cameraPositionHandle;
    private int cameraTextureHandle;
    private int cameraTextureCoordHandle;

    private ShaderProgram shaderProgram;
    private final PointCloudRenderer pointCloudRenderer;
    private final OpenCVProcessor openCVProcessor;

    private ShaderProgram cameraFeedShaderProgram;
    private ShaderProgram featurePointShaderProgram;

    private final List<Point> opencvFeaturePoints = new ArrayList<>();

    public CombinedRenderer() {
        float[] vertices = {
                -1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                -1.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 0.0f
        };

        float[] textureCoords = {
                1.0f, 1.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                0.0f, 0.0f
        };

        vertexBuffer = new Buffer(vertices);
        textureBuffer = new Buffer(textureCoords);
        pointCloudRenderer = new PointCloudRenderer();
        openCVProcessor = new OpenCVProcessor();
    }

    public void setSession(Session session) {
        this.session = session;
        if (session != null) {
            if (textureId != 0) {
                session.setCameraTextureName(textureId);
                Log.i(TAG, "Camera texture name set: " + textureId);
            } else {
                Log.e(TAG, "Invalid texture ID. Cannot set camera texture name.");
            }
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        int[] textures = new int[1];
        GLES32.glGenTextures(1, textures, 0);
        textureId = textures[0];

        if (textureId == 0) {
            Log.e(TAG, "Failed to generate a valid texture ID.");
        } else {
            Log.i(TAG, "Generated texture ID: " + textureId);
        }

        initCameraFeed(textureId);
        pointCloudRenderer.initPointCloud();
        initShaders();

        if (session != null && textureId != 0) {
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

    private void initCameraFeed(int textureId) {
        if (textureId == 0) {
            Log.e(TAG, "Invalid texture ID in initCameraFeed.");
            return;
        }

        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE);

        Log.i(TAG, "Camera feed texture initialized with ID: " + textureId);

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

        Log.i(TAG, "Camera feed rendering initialized successfully.");
    }

    private void initShaders() {
        // Initialize the feature points shader
        String pointVertexShaderCode =
                "attribute vec4 vPosition;" +
                        "uniform float u_PointSize;" +
                        "void main() {" +
                        "  gl_Position = vPosition;" +
                        "  gl_PointSize = u_PointSize;" +
                        "}";

        String pointFragmentShaderCode =
                "precision mediump float;" +
                        "uniform vec4 u_Color;" +
                        "void main() {" +
                        "  gl_FragColor = u_Color;" +
                        "}";

        featurePointShaderProgram = new ShaderProgram(pointVertexShaderCode, pointFragmentShaderCode);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (session == null) {
            Log.e(TAG, "Session is null in onDrawFrame");
            return;
        }
        try {
            GLES32.glEnable(GLES32.GL_BLEND);
            GLES32.glBlendFunc(GLES32.GL_SRC_ALPHA, GLES32.GL_ONE_MINUS_SRC_ALPHA);
            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT | GLES32.GL_DEPTH_BUFFER_BIT);

            Frame frame = session.update();
            Image cameraImage = null;
            int imageWidth = 0;
            int imageHeight = 0;

            try {
                cameraImage = frame.acquireCameraImage();
                imageWidth = cameraImage.getWidth();
                imageHeight = cameraImage.getHeight();
                Mat matImage = openCVProcessor.convertImageToMat(cameraImage);

                // Populate feature points
                List<Point> detectedPoints = openCVProcessor.processWithOpenCV(matImage);
                opencvFeaturePoints.clear();  // Clear any previous points
                opencvFeaturePoints.addAll(detectedPoints);  // Add new points

                cameraImage.close();
            } catch (NotYetAvailableException e) {
                Log.w(TAG, "Camera image not yet available.");
            }

            renderCameraFeed(frame);
            pointCloudRenderer.renderPointCloud(frame);
            renderOpenCVFeaturePoints(imageWidth, imageHeight);

            Log.i(TAG, "Frame drawn successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in onDrawFrame: " + e.getMessage());
        }
    }

    private void renderOpenCVFeaturePoints(int imageWidth, int imageHeight) {
        GLES32.glUseProgram(featurePointShaderProgram.getProgramId());
        GLES32.glUniform1f(GLES32.glGetUniformLocation(featurePointShaderProgram.getProgramId(), "u_PointSize"), 5.0f);

        int pointCount = opencvFeaturePoints.size();
        float[] glCoords = new float[pointCount * 2];

        // Fill the array with converted OpenGL coordinates
        for (int i = 0; i < pointCount; i++) {
            float[] coords = openCVProcessor.convertToOpenGLCoordinates(opencvFeaturePoints.get(i), imageWidth, imageHeight);
            glCoords[i * 2] = coords[0];
            glCoords[i * 2 + 1] = coords[1];
        }

        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(glCoords.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(glCoords).position(0);

        GLES32.glEnableVertexAttribArray(cameraPositionHandle);
        GLES32.glVertexAttribPointer(cameraPositionHandle, 2, GLES32.GL_FLOAT, false, 0, vertexBuffer);

        GLES32.glUniform4f(GLES32.glGetUniformLocation(featurePointShaderProgram.getProgramId(), "u_Color"), 0.0f, 0.0f, 1.0f, 1.0f);

        GLES32.glDrawArrays(GLES32.GL_POINTS, 0, pointCount);
        GLES32.glDisableVertexAttribArray(cameraPositionHandle);

        GLES32.glUseProgram(0);
    }

    private void renderCameraFeed(Frame frame) {
        // Use the shader program
        GLES32.glUseProgram(shaderProgram.getProgramId());

        // Enable vertex and texture coordinate arrays
        GLES32.glEnableVertexAttribArray(cameraPositionHandle);
        GLES32.glVertexAttribPointer(cameraPositionHandle, 3, GLES32.GL_FLOAT, false, 0, vertexBuffer.getFloatBuffer());
        GLES32.glEnableVertexAttribArray(cameraTextureCoordHandle);
        GLES32.glVertexAttribPointer(cameraTextureCoordHandle, 2, GLES32.GL_FLOAT, false, 0, textureBuffer.getFloatBuffer());

        // Set the active texture and bind it
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0);
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES32.glUniform1i(cameraTextureHandle, 0);

        // Draw the texture on the rectangle
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4);

        // Disable vertex and texture coordinate arrays
        GLES32.glDisableVertexAttribArray(cameraPositionHandle);
        GLES32.glDisableVertexAttribArray(cameraTextureCoordHandle);
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }
}

