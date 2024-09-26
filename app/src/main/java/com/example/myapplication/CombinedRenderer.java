package com.example.myapplication;

import android.opengl.GLES11Ext;
import android.opengl.GLES32;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.util.Pair;
import android.media.Image;

import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.NotYetAvailableException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.KeyPoint;
import org.opencv.core.MatOfPoint2f;
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
    private final OpenCVRenderer openCVRenderer;
    private final CADModelLoader cadModelLoader;

    private boolean computeRequested = false;

    private final List<Point> openCVFeaturePoints = new ArrayList<>();

    public CombinedRenderer(InputStream cadModelInputStream) {
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
        openCVRenderer = new OpenCVRenderer();
        cadModelLoader = new CADModelLoader();

        try {
            cadModelLoader.loadAndPrecomputeModel(cadModelInputStream); // Load CAD model from input stream
        } catch (IOException e) {
            Log.e(TAG, "Failed to load CAD model: " + e.getMessage());
        }
    }

    public void requestCompute() {
        computeRequested = true; // Set the flag to trigger computation
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

            com.google.ar.core.Camera arCamera = frame.getCamera();
            float[] intrinsics = new float[4];
            arCamera.getImageIntrinsics().getFocalLength(intrinsics, 0);
            float fx = intrinsics[0]; // Focal length in x direction
            float fy = intrinsics[1]; // Focal length in y direction

            float[] principalPoint = new float[2];
            arCamera.getImageIntrinsics().getPrincipalPoint(principalPoint, 0);
            float cx = principalPoint[0]; // Principal point x-coordinate
            float cy = principalPoint[1]; // Principal point y-coordinate

            Mat cameraMatrix = new Mat(3, 3, CvType.CV_64F);
            cameraMatrix.put(0, 0, fx);
            cameraMatrix.put(0, 1, 0); // Ensure skew is zero
            cameraMatrix.put(0, 2, cx);
            cameraMatrix.put(1, 0, 0); // Ensure skew is zero
            cameraMatrix.put(1, 1, fy);
            cameraMatrix.put(1, 2, cy);
            cameraMatrix.put(2, 0, 0);
            cameraMatrix.put(2, 1, 0);
            cameraMatrix.put(2, 2, 1);

            Mat rvec = Mat.zeros(3, 1, CvType.CV_64F);
            Mat tvec = Mat.zeros(3, 1, CvType.CV_64F);

            try {
                cameraImage = frame.acquireCameraImage();
                imageWidth = cameraImage.getWidth();
                imageHeight = cameraImage.getHeight();
                Mat matImage = openCVRenderer.convertImageToMat(cameraImage);

                List<Point> detectedPoints = openCVRenderer.processOpenCV(matImage);
                openCVFeaturePoints.clear();
                for (Point p : detectedPoints) {
                    float x = (float)((p.x - cx) / fx);
                    float y = (float)((p.y - cy) / fy);
                    openCVFeaturePoints.add(new Point(x, y));
                }

//                List<Point3> corresponding3DPoints = cadModelLoader.findCorresponding3DPoints(openCVFeaturePoints, cameraMatrix);
//                cadModelLoader.estimatePose(openCVFeaturePoints, corresponding3DPoints, cameraMatrix);

                if (computeRequested) {
                    MatOfPoint2f projectedPoints = cadModelLoader.compute2DProjections(cameraMatrix, rvec, tvec);
                    List<Pair<Point, Point>> matchedPoints = cadModelLoader.match2DPoints(openCVFeaturePoints, projectedPoints, 5.0); // Threshold = 5 pixels
                    double matchPercentage = cadModelLoader.calculateMatchPercentage(openCVFeaturePoints, matchedPoints);
                    Log.i(TAG, "Match Percentage: " + matchPercentage + "%");

                    computeRequested = false; // Reset the flag after computation
                }

            } catch (NotYetAvailableException e) {
                Log.w(TAG, "Camera image not yet available.");
            } finally {
                if (cameraImage != null) {
                    cameraImage.close(); // Ensure the camera image is released
                }
            }

            renderCameraFeed(frame);
            openCVRenderer.renderOpenCV(imageWidth, imageHeight);

            Log.i(TAG, "Frame drawn successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in onDrawFrame: " + e.getMessage());
        }
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

