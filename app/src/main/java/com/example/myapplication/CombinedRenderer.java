package com.example.myapplication;

import android.opengl.GLES11Ext;
import android.opengl.GLES32;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.ImageFormat;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Camera;
import com.google.ar.core.exceptions.NotYetAvailableException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import android.media.Image;

import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;

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

    private List<Point> opencvFeaturePoints = new ArrayList<>();

    private static final float MIN_DEPTH = 0.2f; // Minimum depth in meters
    private static final float MAX_DEPTH = 5.0f; // Maximum depth in meters

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
        initPointCloud();
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
                Mat matImage = convertImageToMat(cameraImage);
                processWithOpenCV(matImage);
                cameraImage.close();
            } catch (NotYetAvailableException e) {
                Log.w(TAG, "Camera image not yet available.");
            }

            // Render the ARCore point cloud
            renderCameraFeed(frame);
            renderPointCloud(frame, null);

            // Render OpenCV feature points
            renderOpenCVFeaturePoints(imageWidth, imageHeight);

            Log.i(TAG, "Frame drawn successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in onDrawFrame: " + e.getMessage());
        }
    }

    private void renderOpenCVFeaturePoints(int imageWidth, int imageHeight) {
        GLES32.glUseProgram(shaderProgram.getProgramId());
        GLES32.glUniform1f(GLES32.glGetUniformLocation(shaderProgram.getProgramId(), "u_PointSize"), 5.0f); // Adjust point size as needed

        for (Point point : opencvFeaturePoints) {
            float[] glCoords = convertToOpenGLCoordinates(point, imageWidth, imageHeight);

            FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(glCoords.length * Float.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertexBuffer.put(glCoords).position(0); // Add the coordinates and reset the buffer position

            GLES32.glEnableVertexAttribArray(cameraPositionHandle);
            GLES32.glVertexAttribPointer(cameraPositionHandle, 2, GLES32.GL_FLOAT, false, 0, vertexBuffer);

            GLES32.glUniform4f(GLES32.glGetUniformLocation(shaderProgram.getProgramId(), "u_Color"), 0.0f, 0.0f, 1.0f, 1.0f); // Blue

            GLES32.glDrawArrays(GLES32.GL_POINTS, 0, 1);
            GLES32.glDisableVertexAttribArray(cameraPositionHandle);
        }

        GLES32.glUseProgram(0);
    }

    private Mat convertImageToMat(Image image) {
        // Ensure the Image format is YUV_420_888
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Expected image in YUV_420_888 format");
        }

        // Extract Y, U, V planes
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        // Prepare byte array
        byte[] nv21Bytes = new byte[ySize + uSize + vSize];

        // Fill byte array with data from Y, U, and V planes
        yBuffer.get(nv21Bytes, 0, ySize);
        vBuffer.get(nv21Bytes, ySize, vSize); // V before U
        uBuffer.get(nv21Bytes, ySize + vSize, uSize);

        // Convert NV21 to Mat
        Mat yuvMat = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
        yuvMat.put(0, 0, nv21Bytes);

        // Convert YUV to RGB
        Mat rgbMat = new Mat();
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21);

        return rgbMat;
    }

    private void processWithOpenCV(Mat matImage) {
        // Apply Gaussian Blur to reduce noise
        Imgproc.GaussianBlur(matImage, matImage, new Size(5, 5), 0);

        // Detect ORB features
        MatOfKeyPoint keyPoints = new MatOfKeyPoint();
        ORB orbDetector = ORB.create(1000);
        orbDetector.detect(matImage, keyPoints);

        // Clear previous points
        opencvFeaturePoints.clear();

        // Convert keypoints to a list of OpenCV Points
        for (KeyPoint kp : keyPoints.toArray()) {
            opencvFeaturePoints.add(new Point(kp.pt.x, kp.pt.y));
        }
        KeyPoint[] keypointArray = keyPoints.toArray();
        Log.i(TAG, "Number of detected OpenCV keypoints: " + keypointArray.length);
    }

    private float[] convertToOpenGLCoordinates(Point point, int imageWidth, int imageHeight) {
        float x = (float) ((point.x / imageWidth) * 2.0 - 1.0); // Normalize x to [-1, 1]
        float y = (float) (1.0 - (point.y / imageHeight) * 2.0); // Normalize y to [-1, 1] and invert y

        return new float[]{x, y};
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

    private void renderPointCloud(Frame frame, Image depthImage) {
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
                GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, pointCloudVertexBufferId);

                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(totalFloats * Float.BYTES);
                byteBuffer.order(ByteOrder.nativeOrder());
                FloatBuffer directFloatBuffer = byteBuffer.asFloatBuffer();
                directFloatBuffer.put(pointCloudBuffer);
                directFloatBuffer.position(0);

                 // Filter points by depth
//                if (depthImage != null) {
//                    filterFeaturePointsByDepth(directFloatBuffer, depthImage, numPoints, floatsPerPoint);
//                }

                // Upload point cloud data to the GPU
                GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, totalFloats * Float.BYTES, directFloatBuffer, GLES32.GL_DYNAMIC_DRAW);

                // Set the model view projection matrix
                float[] modelViewProjectionMatrix = new float[16];
                getModelViewProjectionMatrix(frame, modelViewProjectionMatrix);
                GLES32.glUniformMatrix4fv(uModelViewProjectionHandle, 1, false, modelViewProjectionMatrix, 0);

                // Set the point size and color for rendering
                GLES32.glUniform1f(GLES32.glGetUniformLocation(pointCloudShaderProgram.getProgramId(), "u_PointSize"), 5.0f); // Example size
                GLES32.glUniform4f(GLES32.glGetUniformLocation(pointCloudShaderProgram.getProgramId(), "u_Color"), 1.0f, 0.0f, 0.0f, 1.0f); // Example color: red

                // Specify how the point cloud data is interpreted
                GLES32.glVertexAttribPointer(pointCloudPositionHandle, 3, GLES32.GL_FLOAT, false, floatsPerPoint * Float.BYTES, 0);
                GLES32.glEnableVertexAttribArray(pointCloudPositionHandle);

                // Draw the point cloud
                GLES32.glDrawArrays(GLES32.GL_POINTS, 0, numPoints);

                // Disable the vertex attribute array
                GLES32.glDisableVertexAttribArray(pointCloudPositionHandle);
                GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0);
                GLES32.glUseProgram(0);  // Unbind the shader program
            } else {
                Log.w(TAG, "Point cloud is empty, skipping rendering.");
            }

            // Release the point cloud to free up resources
            pointCloud.release();
        } catch (Exception e) {
            Log.e(TAG, "Exception in renderPointCloud: " + e.getMessage());
        }
    }

    private void filterFeaturePointsByDepth(FloatBuffer pointCloudBuffer, Image depthImage, int numPoints, int floatsPerPoint) {
        ShortBuffer depthBuffer = depthImage.getPlanes()[0].getBuffer().asShortBuffer();
        int depthWidth = depthImage.getWidth();
        int depthHeight = depthImage.getHeight();

        int validPoints = 0;

        for (int i = 0; i < numPoints; i++) {
            float x = pointCloudBuffer.get(i * floatsPerPoint);
            float y = pointCloudBuffer.get(i * floatsPerPoint + 1);
            float z = pointCloudBuffer.get(i * floatsPerPoint + 2);

            // Map the 3D point to 2D depth image coordinates
            int depthX = (int) ((x / z) * depthWidth / 2.0f + depthWidth / 2.0f);
            int depthY = (int) ((y / z) * depthHeight / 2.0f + depthHeight / 2.0f);

            if (depthX < 0 || depthX >= depthWidth || depthY < 0 || depthY >= depthHeight) {
                continue; // Skip points that fall outside the depth image bounds
            }

            int depthIndex = depthY * depthWidth + depthX;
            float depthValue = (depthBuffer.get(depthIndex) & 0xFFFF) / 1000.0f; // Depth value in meters

            if (depthValue >= MIN_DEPTH && depthValue <= MAX_DEPTH) {
                validPoints++;
            } else {
                // Zero out the coordinates to effectively remove the point
                pointCloudBuffer.put(i * floatsPerPoint, 0);
                pointCloudBuffer.put(i * floatsPerPoint + 1, 0);
                pointCloudBuffer.put(i * floatsPerPoint + 2, 0);
            }
        }

        Log.i(TAG, "Number of valid feature points after filtering: " + validPoints);
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

