package com.example.myapplication;

import android.content.Context;
import android.media.Image;
import android.opengl.GLES11Ext;
import android.opengl.GLES32;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.NotYetAvailableException;

import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The {@code CombinedRenderer} class is responsible for rendering the camera feed,
 * integrating ARCore and OpenCV functionalities, and performing feature matching
 * between the camera image and CAD model projections.
 */
public class CombinedRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "CombinedRenderer";

    private int textureId = 0;

    private ShaderProgram shaderProgram;

    private int cameraPositionHandle = -1;
    private int cameraTextureCoordHandle = -1;
    private int cameraTextureHandle = -1;

    private final Buffer vertexBuffer;
    private final Buffer textureBuffer;

    private final OpenCVRenderer openCVRenderer;
    private final CADModelLoader cadModelLoader;

    private Session session;

    private boolean computeRequested = false;

    // Fields to store features from CAD model projections
    private final List<MatOfKeyPoint> cadKeypointsList;
    private final List<Mat> cadDescriptorsList;
    private List<Mat> renderedImagesList;

    // Application context
    private final Context context;

    private MatchPercentageListener matchPercentageListener;

    /**
     * Constructs a new {@code CombinedRenderer} with the given CAD model loader and context.
     *
     * @param cadModelLoader The loader for the CAD model.
     * @param context        The application context.
     */
    public CombinedRenderer(CADModelLoader cadModelLoader, Context context) {
        this.context = context;

        // Define vertices for a full-screen quad
        float[] vertices = {
                -1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                -1.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 0.0f
        };

        // Define texture coordinates corresponding to the vertices
        float[] textureCoords = {
                1.0f, 1.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                0.0f, 0.0f
        };

        // Initialise buffers
        vertexBuffer = new Buffer(vertices);
        textureBuffer = new Buffer(textureCoords);

        // Initialise OpenCVRednerer and CADModelLoader classes
        openCVRenderer = new OpenCVRenderer();
        this.cadModelLoader = cadModelLoader;

        // Initialise lists to store CAD features
        cadKeypointsList = new ArrayList<>();
        cadDescriptorsList = new ArrayList<>();
    }

    /**
     * Requests computation for feature matching.
     * Sets a flag that is checked during rendering.
     */
    public void requestCompute() {
        computeRequested = true; // Set the flag to trigger computation
    }

    /**
     * Sets the ARCore session and associates the camera texture.
     *
     * @param session The ARCore session.
     */
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
        // Log the OpenGL ES version
        String version = GLES32.glGetString(GLES32.GL_VERSION);
        Log.i(TAG, "OpenGL ES version: " + version);

        // Generate a texture for the camera feed
        int[] textures = new int[1];
        GLES32.glGenTextures(1, textures, 0);
        textureId = textures[0];

        if (textureId == 0) {
            Log.e(TAG, "Failed to generate a valid texture ID.");
        } else {
            Log.i(TAG, "Generated texture ID: " + textureId);
        }

        // Initialize the camera feed with the generated texture
        initCameraFeed(textureId);

        // Initialize OpenCV renderer
        openCVRenderer.initOpenCV();

        // Set the camera texture name in the session if available
        if (session != null && textureId != 0) {
            session.setCameraTextureName(textureId);
            Log.i(TAG, "Camera texture name set: " + textureId);
        }

        // Initialise OpenGL resources for the CAD model
        cadModelLoader.initOpenGL();

        // Extract features from CAD model projections
        extractFeaturesFromCADProjections();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // Ensure valid surface dimensions
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
            // Enable blending for transparency
            GLES32.glEnable(GLES32.GL_BLEND);
            GLES32.glBlendFunc(GLES32.GL_SRC_ALPHA, GLES32.GL_ONE_MINUS_SRC_ALPHA);

            // Clear color and depth buffers
            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT | GLES32.GL_DEPTH_BUFFER_BIT);

            // Update the session to get the latest frame
            Frame frame = session.update();

            // Render the camera feed
            renderCameraFeed(frame);

            // Get the latest camera image
            Image cameraImage = null;
            int imageWidth = 0;
            int imageHeight = 0;

            try {
                cameraImage = frame.acquireCameraImage();
                imageWidth = cameraImage.getWidth();
                imageHeight = cameraImage.getHeight();
                Mat matImage = openCVRenderer.convertImageToMat(cameraImage);

                // Process the camera image with OpenCV
                openCVRenderer.processOpenCV(matImage);

                // Render OpenCV results
                openCVRenderer.renderOpenCV(imageWidth, imageHeight);

                // Perform feature matching if computation is requested
                if (computeRequested) {
                    computeRequested = false; // Reset the flag immediately

                    saveCameraImage(matImage);
                    drawAndSaveKeypoints(matImage);
                    double matchPercentage = performMatchingAndCalculateMatchPercentage();
                    Log.i(TAG, "Match Percentage: " + matchPercentage + "%");
                }

                matImage.release();

            } catch (NotYetAvailableException e) {
                Log.w(TAG, "Camera image not yet available.");
            } finally {
                if (cameraImage != null) {
                    cameraImage.close(); // Ensure the camera image is released
                }
            }

            Log.i(TAG, "Frame drawn successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in onDrawFrame: " + e.getMessage());
        }
    }

    /**
     * Initializes the camera feed texture and shader program.
     *
     * @param textureId The texture ID for the camera feed.
     */
    private void initCameraFeed(int textureId) {
        if (textureId == 0) {
            Log.e(TAG, "Invalid texture ID in initCameraFeed.");
            return;
        }

        // Bind the texture as an external texture for the camera feed
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE);
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE);

        Log.i(TAG, "Camera feed texture initialized with ID: " + textureId);

        // Vertex shader for rendering the camera feed
        String vertexShaderCode =
                "attribute vec4 vPosition;" +
                        "attribute vec2 vTexCoord;" +
                        "varying vec2 texCoord;" +
                        "void main() {" +
                        "  gl_Position = vPosition;" +
                        "  texCoord = vTexCoord;" +
                        "}";

        // Fragment shader for rendering the camera feed
        String fragmentShaderCode =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;" +
                        "uniform samplerExternalOES sTexture;" +
                        "varying vec2 texCoord;" +
                        "void main() {" +
                        "  gl_FragColor = texture2D(sTexture, texCoord);" +
                        "}";

        // Initialize the shader program with the vertex and fragment shaders
        shaderProgram = new ShaderProgram(vertexShaderCode, fragmentShaderCode);

        // Retrieve attribute and uniform locations from the shader program
        cameraPositionHandle = GLES32.glGetAttribLocation(shaderProgram.getProgramId(), "vPosition");
        cameraTextureCoordHandle = GLES32.glGetAttribLocation(shaderProgram.getProgramId(), "vTexCoord");
        cameraTextureHandle = GLES32.glGetUniformLocation(shaderProgram.getProgramId(), "sTexture");

        Log.i(TAG, "Camera feed rendering initialized successfully.");
    }

    /**
     * Renders the camera feed onto the screen.
     *
     * @param frame The current ARCore frame.
     */
    private void renderCameraFeed(Frame frame) {
        // Use the shader program for camera feed rendering
        GLES32.glUseProgram(shaderProgram.getProgramId());

        // Enable and set vertex attribute for position
        GLES32.glEnableVertexAttribArray(cameraPositionHandle);
        GLES32.glVertexAttribPointer(cameraPositionHandle, 3, GLES32.GL_FLOAT, false, 0, vertexBuffer.getFloatBuffer());

        // Enable and set vertex attribute for texture coordinates
        GLES32.glEnableVertexAttribArray(cameraTextureCoordHandle);
        GLES32.glVertexAttribPointer(cameraTextureCoordHandle, 2, GLES32.GL_FLOAT, false, 0, textureBuffer.getFloatBuffer());

        // Activate and bind the camera texture
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0);
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES32.glUniform1i(cameraTextureHandle, 0);

        // Draw the full-screen quad (two triangles)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4);

        // Disable vertex attributes to clean up state
        GLES32.glDisableVertexAttribArray(cameraPositionHandle);
        GLES32.glDisableVertexAttribArray(cameraTextureCoordHandle);
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    /**
     * Extracts features from the CAD model projections rendered from multiple viewpoints.
     */
    private void extractFeaturesFromCADProjections() {
        // Clear previous features
        cadKeypointsList.clear();
        cadDescriptorsList.clear();

        // Render the CAD model from multiple viewpoints
        renderedImagesList = cadModelLoader.renderCADModelFromViewpoints();

        int imageIndex = 0;
        for (Mat renderedImage : renderedImagesList) {
            // Save the rendered image to external storage
            String filename = "rendered_image_" + imageIndex + ".png";
            File directory = new File(context.getExternalFilesDir(null), "RenderedImages");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            File file = new File(directory, filename);
            boolean saved = Imgcodecs.imwrite(file.getAbsolutePath(), renderedImage);
            if (saved) {
                Log.d(TAG, "Saved rendered image to " + file.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to save rendered image.");
            }

            // Extract features using OpenCVRenderer's method
            Pair<MatOfKeyPoint, Mat> features = openCVRenderer.extractFeaturesFromImage(renderedImage);

            if (features == null || features.first.empty() || features.second.empty()) {
                Log.w(TAG, "No features detected in CAD rendered image at index " + imageIndex);
                imageIndex++;
                continue;
            }

            // Store the extracted keypoints and descriptors
            cadKeypointsList.add(features.first);
            cadDescriptorsList.add(features.second);

            Log.d(TAG, "Extracted " + features.first.size() + " keypoints from CAD rendered image at index " + imageIndex);

            // Draw keypoints on the rendered image for visualisation
            Mat outputImage = new Mat();
            Features2d.drawKeypoints(renderedImage, features.first, outputImage);

            // Save the image with keypoints to external storage
            String keypointsFilename = "rendered_image_with_keypoints_" + imageIndex + ".png";
            File keypointsFile = new File(directory, keypointsFilename);
            boolean keypointsSaved = Imgcodecs.imwrite(keypointsFile.getAbsolutePath(), outputImage);
            if (keypointsSaved) {
                Log.d(TAG, "Saved rendered image with keypoints to " + keypointsFile.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to save rendered image with keypoints.");
            }

            imageIndex++;
        }
    }

    /**
     * Saves the current camera image to external storage.
     *
     * @param matImage The camera image in OpenCV Mat format.
     */
    private void saveCameraImage(Mat matImage) {
        String filename = "camera_image.png";
        File directory = new File(context.getExternalFilesDir(null), "CameraImages");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File file = new File(directory, filename);
        boolean saved = Imgcodecs.imwrite(file.getAbsolutePath(), matImage);
        if (saved) {
            Log.d(TAG, "Saved camera image to " + file.getAbsolutePath());
        } else {
            Log.e(TAG, "Failed to save camera image.");
        }
    }

    /**
     * Draws keypoints on the camera image and saves it to external storage.
     *
     * @param matImage The camera image in OpenCV Mat format.
     */
    private void drawAndSaveKeypoints(Mat matImage) {
        // Draw keypoints on the camera image
        Mat outputImage = new Mat();
        Features2d.drawKeypoints(matImage, openCVRenderer.getDetectedKeyPoints(), outputImage);

        // Save the image with keypoints to external storage
        String keypointsFilename = "camera_image_with_keypoints.png";
        File directory = new File(context.getExternalFilesDir(null), "CameraImages");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File keypointsFile = new File(directory, keypointsFilename);
        boolean keypointsSaved = Imgcodecs.imwrite(keypointsFile.getAbsolutePath(), outputImage);
        if (keypointsSaved) {
            Log.d(TAG, "Saved camera image with keypoints to " + keypointsFile.getAbsolutePath());
        } else {
            Log.e(TAG, "Failed to save camera image with keypoints.");
        }
    }

    /**
     * Performs feature matching between the camera image and CAD model projections,
     * and calculates the match percentage.
     *
     * @return The match percentage as a double value.
     */
    private double performMatchingAndCalculateMatchPercentage() {
        int totalMatches = 0;
        int inlierMatches = 0;

        // Retrieve detected keypoints and descriptors from the camera image
        MatOfKeyPoint detectedKeypoints = openCVRenderer.getDetectedKeyPoints();
        Mat detectedDescriptors = openCVRenderer.getDetectedDescriptors();

        Log.d(TAG, "Detected keypoints: " + detectedKeypoints.size());
        Log.d(TAG, "Detected descriptors size: " + detectedDescriptors.size());

        if (detectedDescriptors == null || detectedDescriptors.empty()) {
            Log.w(TAG, "Detected descriptors are empty.");
            return 0.0;
        }

        for (int i = 0; i < cadDescriptorsList.size(); i++) {
            Mat cadDescriptors = cadDescriptorsList.get(i);
            MatOfKeyPoint cadKeypoints = cadKeypointsList.get(i);

            Log.d(TAG, "CAD keypoints at index " + i + ": " + cadKeypoints.size());
            Log.d(TAG, "CAD descriptors size at index " + i + ": " + cadDescriptors.size());

            if (cadDescriptors == null || cadDescriptors.empty()) {
                Log.w(TAG, "CAD descriptors are empty for index " + i);
                continue;
            }

            // Match features between CAD descriptors and detected descriptors
            List<DMatch> matches = openCVRenderer.matchFeatures(cadDescriptors, detectedDescriptors);

            Log.d(TAG, "Number of matches between CAD index " + i + " and detected features: " + matches.size());

            if (matches.size() < 4) {
                Log.w(TAG, "Not enough matches to compute homography for CAD index " + i + ". Matches found: " + matches.size());
                continue;
            }

            totalMatches += matches.size();

            // Filter matches with RANSAC
            List<DMatch> inliers = openCVRenderer.filterMatchesWithRANSAC(matches, cadKeypoints, detectedKeypoints);

            Log.d(TAG, "Number of inlier matches after RANSAC for CAD index " + i + ": " + inliers.size());

            inlierMatches += inliers.size();

            // Visualize matches
            if (!inliers.isEmpty()) {
                Mat imgMatches = new Mat();
                Mat renderedImage = renderedImagesList.get(i); // Get the rendered image

                // Create a MatOfDMatch from the inliers list
                MatOfDMatch matOfInliers = new MatOfDMatch();
                matOfInliers.fromList(inliers);

                Features2d.drawMatches(
                        renderedImage,
                        cadKeypoints,
                        openCVRenderer.getLastProcessedImage(),
                        detectedKeypoints,
                        matOfInliers,
                        imgMatches
                );

                // Save the matches visualization to external storage
                String matchesFilename = "matches_index_" + i + ".png";
                File directory = new File(context.getExternalFilesDir(null), "Matches");
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                File matchesFile = new File(directory, matchesFilename);
                boolean matchesSaved = Imgcodecs.imwrite(matchesFile.getAbsolutePath(), imgMatches);
                if (matchesSaved) {
                    Log.d(TAG, "Saved matches image to " + matchesFile.getAbsolutePath());
                } else {
                    Log.e(TAG, "Failed to save matches image.");
                }

                imgMatches.release();
            }
        }

        if (totalMatches == 0) {
            Log.w(TAG, "No matches found between CAD model and detected features.");
            return 0.0;
        }

        double matchPercentage = ((double) inlierMatches / totalMatches) * 100.0;
        Log.d(TAG, "Total Matches: " + totalMatches + ", Inlier Matches: " + inlierMatches);

        // Notify the listener
        if (matchPercentageListener != null) {
            // Ensure the listener is called on the main thread
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> matchPercentageListener.onMatchPercentageCalculated(matchPercentage));
        }

        return matchPercentage;
    }

    public void setMatchPercentageListener(MatchPercentageListener listener) {
        this.matchPercentageListener = listener;
    }
}
