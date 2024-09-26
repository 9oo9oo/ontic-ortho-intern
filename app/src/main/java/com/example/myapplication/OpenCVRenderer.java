package com.example.myapplication;

import android.opengl.GLES32;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.ImageFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.media.Image;

import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.features2d.Feature2D;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

public class OpenCVRenderer {
    private static final String TAG = "OpenCVProcessor";
    private final List<Point> opencvFeaturePoints;
    private ShaderProgram featurePointShaderProgram;
    private int cameraPositionHandle;

    public OpenCVRenderer() {
        this.opencvFeaturePoints = new ArrayList<>();
    }

    public void initOpenCV() {
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


    public  Mat convertImageToMat(Image image) {
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
        vBuffer.get(nv21Bytes, ySize, vSize); // V before U
        uBuffer.get(nv21Bytes, ySize + vSize, uSize);

        Mat yuvMat = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
        yuvMat.put(0, 0, nv21Bytes);

        Mat rgbMat = new Mat();
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21);

        return rgbMat;
    }

    public List<Point> processOpenCV(Mat matImage) {
        Imgproc.GaussianBlur(matImage, matImage, new Size(5, 5), 0);

        MatOfKeyPoint keyPoints = new MatOfKeyPoint();
        ORB orbDetector = ORB.create(5000);
        orbDetector.detect(matImage, keyPoints);

        opencvFeaturePoints.clear();

        for (KeyPoint kp : keyPoints.toArray()) {
            opencvFeaturePoints.add(new Point(kp.pt.x, kp.pt.y));
        }

        Log.i(TAG, "Number of detected OpenCV keypoints: " + opencvFeaturePoints.size());
        return opencvFeaturePoints;
    }

//    public List<Point> processOpenCV(Mat matImage) {
//        // Step 1: Validate Input Image
//        if (matImage.empty()) {
//            Log.e(TAG, "Input image is empty.");
//            return Collections.emptyList();
//        }
//
//        // Step 2: Convert to Grayscale if Necessary
//        Mat grayImage = new Mat();
//        if (matImage.channels() > 1) {
//            Imgproc.cvtColor(matImage, grayImage, Imgproc.COLOR_BGR2GRAY);
//        } else {
//            grayImage = matImage.clone();
//        }
//
//        // Step 3: Apply CLAHE for Contrast Enhancement
//        Mat claheImage = new Mat();
//        CLAHE clahe = Imgproc.createCLAHE();
//        clahe.setClipLimit(2.0);
//        clahe.apply(grayImage, claheImage);
//
//        // Step 4: Apply Bilateral Filter for Edge-Preserving Noise Reduction
//        Mat bilateralImage = new Mat();
//        try {
//            Imgproc.bilateralFilter(claheImage, bilateralImage, 9, 75, 75);
//        } catch (CvException e) {
//            Log.e(TAG, "Bilateral Filter failed: " + e.getMessage());
//            return Collections.emptyList();
//        }
//
//        // Step 5: Apply Sharpening Filter
//        Mat sharpenedImage = new Mat();
//        Mat kernel = new Mat(3, 3, CvType.CV_32F) {
//            {
//                put(0, 0, 0, -1, 0);
//                put(1, 0, -1, 5, -1);
//                put(2, 0, 0, -1, 0);
//            }
//        };
//        Imgproc.filter2D(bilateralImage, sharpenedImage, bilateralImage.depth(), kernel);
//
//        // Step 6: Initialize Feature Detector (ORB in this Example)
//        ORB orbDetector = ORB.create(
//                10000,    // nFeatures
//                1.2f,     // scaleFactor
//                8,        // nLevels
//                31,       // edgeThreshold
//                0,        // firstLevel
//                2,        // WTA_K
//                ORB.HARRIS_SCORE, // scoreType
//                31        // patchSize
//        );
//        MatOfKeyPoint keyPoints = new MatOfKeyPoint();
//        orbDetector.detect(sharpenedImage, keyPoints);
//
//        // Step 7: Remove Duplicate or Nearby Keypoints
//        List<KeyPoint> filteredKeypoints = removeDuplicates(keyPoints.toList(), 10.0);
//        opencvFeaturePoints.clear();
//        for (KeyPoint kp : filteredKeypoints) {
//            opencvFeaturePoints.add(new Point(kp.pt.x, kp.pt.y));
//        }
//
//        Log.i(TAG, "Number of detected OpenCV keypoints: " + opencvFeaturePoints.size());
//        return opencvFeaturePoints;
//    }
//
//    // Helper method to remove nearby keypoints
//    private List<KeyPoint> removeDuplicates(List<KeyPoint> keypoints, double minDistance) {
//        List<KeyPoint> filtered = new ArrayList<>();
//        for (KeyPoint kp : keypoints) {
//            boolean tooClose = false;
//            for (KeyPoint existing : filtered) {
//                double distance = Math.hypot(kp.pt.x - existing.pt.x, kp.pt.y - existing.pt.y);
//                if (distance < minDistance) {
//                    tooClose = true;
//                    break;
//                }
//            }
//            if (!tooClose) {
//                filtered.add(kp);
//            }
//        }
//        return filtered;
//    }

    public void renderOpenCV(int imageWidth, int imageHeight) {
        GLES32.glUseProgram(featurePointShaderProgram.getProgramId());
        GLES32.glUniform1f(GLES32.glGetUniformLocation(featurePointShaderProgram.getProgramId(), "u_PointSize"), 5.0f);

        int pointCount = opencvFeaturePoints.size();
        float[] glCoords = new float[pointCount * 2];

        // Fill the array with converted OpenGL coordinates
        for (int i = 0; i < pointCount; i++) {
            float[] coords = convertToOpenGLCoords(opencvFeaturePoints.get(i), imageWidth, imageHeight);
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

    public float[] convertToOpenGLCoords(Point point, int imageWidth, int imageHeight) {
        float glX = ((float) point.y / imageHeight) * 2.0f - 1.0f;
        float glY = (1.0f - ((float) point.x / imageWidth) * 2.0f);
        return new float[]{-glX, glY};
    }
}