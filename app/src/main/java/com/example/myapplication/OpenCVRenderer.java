package com.example.myapplication;

import android.media.Image;
import android.opengl.GLES32;
import android.util.Log;
import android.util.Pair;

import com.google.ar.core.ImageFormat;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.features2d.AKAZE;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code OpenCVRenderer} class integrates OpenCV functionalities for image processing,
 * feature detection, matching, and rendering within an OpenGL context.
 */
public class OpenCVRenderer {
    private static final String TAG = "OpenCVRenderer";

    private ShaderProgram featurePointShaderProgram;

    private int cameraPositionHandle = -1;
    private static final float POINT_SIZE = 5.0f;
    private static final float[] FEATURE_COLOR = {0.0f, 0.0f, 1.0f, 1.0f};

    private final List<Point> opencvFeaturePoints;

    private MatOfKeyPoint detectedKeyPoints;
    private Mat detectedDescriptors;

    private Mat lastProcessedImage;

    /**
     * Constructs a new {@code OpenCVRenderer}.
     * Initializes the list for storing feature points.
     */
    public OpenCVRenderer() {
        this.opencvFeaturePoints = new ArrayList<>();
    }

    /**
     * Initializes OpenCV-related resources and compiles shaders for rendering feature points.
     */
    public void initOpenCV() {
        // Vertex shader code for rendering points
        String pointVertexShaderCode =
                "attribute vec4 vPosition;" +
                        "uniform float u_PointSize;" +
                        "void main() {" +
                        "  gl_Position = vPosition;" +
                        "  gl_PointSize = u_PointSize;" +
                        "}";

        // Fragment shader code for rendering points
        String pointFragmentShaderCode =
                "precision mediump float;" +
                        "uniform vec4 u_Color;" +
                        "void main() {" +
                        "  gl_FragColor = u_Color;" +
                        "}";

        // Initialize the shader program with the vertex and fragment shaders
        featurePointShaderProgram = new ShaderProgram(pointVertexShaderCode, pointFragmentShaderCode);
    }

    /**
     * Converts an Android {@link Image} in YUV_420_888 format to an OpenCV {@link Mat} in RGB format.
     *
     * @param image The Android {@code Image} to be converted.
     * @return An OpenCV {@code Mat} in RGB format.
     * @throws IllegalArgumentException If the image format is not YUV_420_888.
     */
    public Mat convertImageToMat(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Expected image in YUV_420_888 format");
        }

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21Bytes = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21Bytes, 0, ySize);
        vBuffer.get(nv21Bytes, ySize, vSize);
        uBuffer.get(nv21Bytes, ySize + vSize, uSize);

        Mat yuvMat = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
        yuvMat.put(0, 0, nv21Bytes);

        Mat rgbMat = new Mat();
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21);

        yuvMat.release();

        return rgbMat;
    }

    /**
     * Preprocesses the image by converting to grayscale, normalizing, blurring, and edge detection.
     *
     * @param image The input image {@code Mat}.
     * @return A preprocessed image {@code Mat}.
     */
    private Mat preprocessImage(Mat image) {
        Mat grayImage = new Mat();
        if (image.channels() > 1) {
            Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
        } else {
            grayImage = image.clone();
        }

        // Normalize the image to improve contrast
        Core.normalize(grayImage, grayImage, 0, 255, Core.NORM_MINMAX);

        // Reduce noise with Gaussian blur
        Imgproc.GaussianBlur(grayImage, grayImage, new Size(5, 5), 0);

        // Enhance edges using Canny edge detection
        Mat edges = new Mat();
        Imgproc.Canny(grayImage, edges, 50, 150);

        grayImage.release();

        return edges;
    }

    /**
     * Processes the image to detect features using AKAZE algorithm.
     *
     * @param matImage The input image {@code Mat}.
     * @return A list of detected feature points.
     */
    public List<Point> processOpenCV(Mat matImage) {
        // Preprocess the image
        Mat processedImage = preprocessImage(matImage);

        // Initialize AKAZE detector
        AKAZE akazeDetector = AKAZE.create();
        MatOfKeyPoint keyPoints = new MatOfKeyPoint();
        Mat descriptors = new Mat();

        // Detect keypoints and compute descriptors
        akazeDetector.detectAndCompute(processedImage, new Mat(), keyPoints, descriptors);

        // Store keypoints and descriptors
        this.detectedKeyPoints = keyPoints;
        this.detectedDescriptors = descriptors;

        // Convert keypoints to point objects
        opencvFeaturePoints.clear();
        for (KeyPoint kp : keyPoints.toArray()) {
            opencvFeaturePoints.add(new Point(kp.pt.x, kp.pt.y));
        }

        // Clone the original image for later use
        lastProcessedImage = matImage.clone();

        Log.i(TAG, "Number of detected OpenCV keypoints: " + opencvFeaturePoints.size());

        processedImage.release();

        return opencvFeaturePoints;
    }

    /**
     * Returns the last processed image.
     *
     * @return The last processed image as a {@code Mat}.
     */
    public Mat getLastProcessedImage() {
        return lastProcessedImage;
    }

    /**
     * Creates a Brute-Force matcher with Hamming distance metric.
     *
     * @return A {@link DescriptorMatcher} instance.
     */
    private DescriptorMatcher createBFMatcher() {
        return BFMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING, false);
    }

    /**
     * Matches features between two sets of descriptors using KNN matching and Lowe's ratio test.
     *
     * @param descriptors1 The first set of descriptors.
     * @param descriptors2 The second set of descriptors.
     * @return A list of good matches.
     */
    public List<DMatch> matchFeatures(Mat descriptors1, Mat descriptors2) {
        DescriptorMatcher matcher = createBFMatcher();

        // Perform KNN matching with k=2
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        matcher.knnMatch(descriptors1, descriptors2, knnMatches, 2);

        // Apply Lowe's ratio test to filter good matches
        List<DMatch> goodMatches = new ArrayList<>();
        for (MatOfDMatch matOfDMatch : knnMatches) {
            DMatch[] matches = matOfDMatch.toArray();
            if (matches.length >= 2) {
                if (matches[0].distance < 0.75 * matches[1].distance) {
                    goodMatches.add(matches[0]);
                }
            }
        }

        return goodMatches;
    }

    /**
     * Filters matches using RANSAC to find inlier matches based on homography estimation.
     *
     * @param matches     The list of matches to be filtered.
     * @param keypoints1  Keypoints from the first image.
     * @param keypoints2  Keypoints from the second image.
     * @return A list of inlier matches.
     */
    public List<DMatch> filterMatchesWithRANSAC(List<DMatch> matches, MatOfKeyPoint keypoints1, MatOfKeyPoint keypoints2) {
        List<DMatch> inlierMatches = new ArrayList<>();

        if (matches.size() < 4) {
            // Not enough matches to compute homography
            Log.w(TAG, "Not enough matches to compute homography. Matches found: " + matches.size());
            return inlierMatches; // Return empty list
        }

        // Convert keypoints to Point2f
        List<Point> points1 = new ArrayList<>();
        List<Point> points2 = new ArrayList<>();

        List<KeyPoint> kp1List = keypoints1.toList();
        List<KeyPoint> kp2List = keypoints2.toList();

        for (DMatch match : matches) {
            points1.add(kp1List.get(match.queryIdx).pt);
            points2.add(kp2List.get(match.trainIdx).pt);
        }

        if (points1.size() < 4 || points2.size() < 4) {
            Log.w(TAG, "Not enough point correspondences for homography. Points1: " + points1.size() + ", Points2: " + points2.size());
            return inlierMatches;
        }

        MatOfPoint2f pts1 = new MatOfPoint2f();
        pts1.fromList(points1);
        MatOfPoint2f pts2 = new MatOfPoint2f();
        pts2.fromList(points2);

        // Compute homography using RANSAC
        Mat mask = new Mat();
        try {
            Mat homography = Calib3d.findHomography(pts1, pts2, Calib3d.RANSAC, 3.0, mask);

            if (homography.empty()) {
                Log.w(TAG, "Homography matrix is empty.");
                return inlierMatches; // Return empty list
            }

            // Extract inliers based on the mask
            byte[] maskArray = new byte[(int) mask.total()];
            mask.get(0, 0, maskArray);
            for (int i = 0; i < maskArray.length; i++) {
                if (maskArray[i] != 0) {
                    inlierMatches.add(matches.get(i));
                }
            }

            homography.release();

        } catch (CvException e) {
            Log.e(TAG, "Exception during findHomography: " + e.getMessage());
            e.printStackTrace();
        } finally {
            mask.release();
        }

        return inlierMatches;
    }

    /**
     * Renders the detected OpenCV feature points onto the screen using OpenGL.
     *
     * @param imageWidth  The width of the image.
     * @param imageHeight The height of the image.
     */
    public void renderOpenCV(int imageWidth, int imageHeight) {
        // Use the shader program for rendering feature points
        GLES32.glUseProgram(featurePointShaderProgram.getProgramId());

        // Set the point size uniform
        GLES32.glUniform1f(GLES32.glGetUniformLocation(featurePointShaderProgram.getProgramId(), "u_PointSize"), 5.0f);

        int pointCount = opencvFeaturePoints.size();
        float[] glCoords = new float[pointCount * 2];

        // Fill the array with converted OpenGL coordinates
        for (int i = 0; i < pointCount; i++) {
            float[] coords = convertToOpenGLCoords(opencvFeaturePoints.get(i), imageWidth, imageHeight);
            glCoords[i * 2] = coords[0];
            glCoords[i * 2 + 1] = coords[1];
        }

        // Create a FloatBuffer for the OpenGL coordinates
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(glCoords.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(glCoords).position(0);

        // Retrieve the position attribute handle from the shader
        int positionHandle = GLES32.glGetAttribLocation(featurePointShaderProgram.getProgramId(), "vPosition");
        GLES32.glEnableVertexAttribArray(positionHandle);
        GLES32.glVertexAttribPointer(positionHandle, 2, GLES32.GL_FLOAT, false, 0, vertexBuffer);

        // Set the color uniform for the points
        GLES32.glUniform4f(GLES32.glGetUniformLocation(featurePointShaderProgram.getProgramId(), "u_Color"), 0.0f, 0.0f, 1.0f, 1.0f);

        // Draw the points
        GLES32.glDrawArrays(GLES32.GL_POINTS, 0, pointCount);

        // Disable the vertex attribute array
        GLES32.glDisableVertexAttribArray(positionHandle);

        // Unuse the shader program
        GLES32.glUseProgram(0);

        // Release the vertex buffer
        vertexBuffer.clear();
    }

    /**
     * Extracts features from the given image using AKAZE algorithm.
     *
     * @param image The input image {@code Mat}.
     * @return A {@code Pair} containing keypoints and descriptors.
     */
    public Pair<MatOfKeyPoint, Mat> extractFeaturesFromImage(Mat image) {
        // Preprocess the image
        Mat processedImage = preprocessImage(image);

        // Initialize AKAZE detector
        AKAZE akazeDetector = AKAZE.create();
        akazeDetector.setThreshold(0.001);
        MatOfKeyPoint keyPoints = new MatOfKeyPoint();
        Mat descriptors = new Mat();

        // Detect keypoints and compute descriptors
        akazeDetector.detectAndCompute(processedImage, new Mat(), keyPoints, descriptors);

        // Release the processed image as it's no longer needed
        processedImage.release();

        return new Pair<>(keyPoints, descriptors);
    }

    /**
     * Converts image coordinates to OpenGL coordinates.
     *
     * @param point       The point in image coordinates.
     * @param imageWidth  The width of the image.
     * @param imageHeight The height of the image.
     * @return A float array containing OpenGL coordinates.
     */
    public float[] convertToOpenGLCoords(Point point, int imageWidth, int imageHeight) {
        float glX = ((float) point.y / imageHeight) * 2.0f - 1.0f;
        float glY = 1.0f - ((float) point.x / imageWidth) * 2.0f;
        return new float[]{-glX, glY};
    }

    /**
     * Returns the detected keypoints from the last processed image.
     *
     * @return A {@code MatOfKeyPoint} containing the detected keypoints.
     */
    public MatOfKeyPoint getDetectedKeyPoints() {
        return detectedKeyPoints;
    }

    /**
     * Returns the detected descriptors from the last processed image.
     *
     * @return A {@code Mat} containing the detected descriptors.
     */
    public Mat getDetectedDescriptors() {
        return detectedDescriptors;
    }
}
