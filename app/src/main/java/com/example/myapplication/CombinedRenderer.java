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
    private final OpenCVRenderer openCVRenderer;

    private final List<Point> openCVFeaturePoints = new ArrayList<>();

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
        openCVRenderer = new OpenCVRenderer();
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
        openCVRenderer.initOpenCV();

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
                Mat matImage = openCVRenderer.convertImageToMat(cameraImage);

                // Populate feature points
                List<Point> detectedPoints = openCVRenderer.processOpenCV(matImage);
                openCVFeaturePoints.clear();  // Clear any previous points
                openCVFeaturePoints.addAll(detectedPoints);  // Add new points

                cameraImage.close();
            } catch (NotYetAvailableException e) {
                Log.w(TAG, "Camera image not yet available.");
            }

            renderCameraFeed(frame);
            pointCloudRenderer.renderPointCloud(frame);
            openCVRenderer.renderOpenCV(imageWidth, imageHeight);

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

