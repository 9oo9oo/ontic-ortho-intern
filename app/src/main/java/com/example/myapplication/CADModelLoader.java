package com.example.myapplication;

import android.util.Log;
import android.util.Pair;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


// CADModelLoader is responsible for loading and parsing a CAD model file (.obj format).
public class CADModelLoader {
    private static final String TAG = "CADModelLoader";
    private List<Point3> vertices; // List to store vertices
    private List<Point3> normals;  // List to store normals
    private List<int[]> faces;     // List to store faces (index of vertices)
    private MatOfPoint3f objectPoints; // Precomputed 3D points

    public CADModelLoader() {
        vertices = new ArrayList<>();
        normals = new ArrayList<>();
        faces = new ArrayList<>();
    }

    public void loadModel(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        // Read each line of the file
        while ((line = reader.readLine()) != null) {
            parseLine(line);
        }

        reader.close();
    }

    // Load and parse the CAD model and precompute all 3D points
    public void loadAndPrecomputeModel(InputStream inputStream) throws IOException {
        loadModel(inputStream); // Load the CAD model

        // Precompute all 3D points into a Mat object
        objectPoints = new MatOfPoint3f();
        objectPoints.fromList(vertices);

        Log.d(TAG, "Precomputed 3D points for CAD model: " + objectPoints.size());
    }

    // Parses a line from the .obj file to extract vertices, normals, and faces.
    private void parseLine(String line) {
        if (line.startsWith("v ")) { // Vertex line
            parseVertex(line);
        } else if (line.startsWith("vn ")) { // Normal line
            parseNormal(line);
        } else if (line.startsWith("f ")) { // Face line
            parseFace(line);
        }
        // Ignore other lines like texture coordinates, comments, etc.
    }

    // Parses a vertex line from the .obj file.
    private void parseVertex(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 4) {
            try {
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                vertices.add(new Point3(x, y, z)); // Add vertex to the list
            } catch (NumberFormatException e) {
                e.printStackTrace(); // Handle parsing error
            }
        }
    }

    // Parses a normal line from the .obj file.
    private void parseNormal(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 4) {
            try {
                double nx = Double.parseDouble(parts[1]);
                double ny = Double.parseDouble(parts[2]);
                double nz = Double.parseDouble(parts[3]);
                normals.add(new Point3(nx, ny, nz)); // Add normal to the list
            } catch (NumberFormatException e) {
                e.printStackTrace(); // Handle parsing error
            }
        }
    }

    // Parses a face line from the .obj file.
    private void parseFace(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 4) {
            try {
                int[] face = new int[3]; // Assuming triangular faces

                for (int i = 1; i <= 3; i++) {
                    String[] vertexData = parts[i].split("/");
                    face[i - 1] = Integer.parseInt(vertexData[0]) - 1; // Store vertex index, adjusting for 0-based indexing
                }
                faces.add(face);
            } catch (NumberFormatException e) {
                e.printStackTrace(); // Handle parsing error
            }
        }
    }

    // Method to compute the 2D projections of the precomputed 3D points
    public MatOfPoint2f compute2DProjections(Mat cameraMatrix, Mat rvec, Mat tvec) {
        MatOfPoint2f projectedPoints = new MatOfPoint2f();
        MatOfDouble distCoeffs = new MatOfDouble(0, 0, 0, 0); // Initialize distortion coefficients

        // Validate matrices before calling projectPoints
        if (objectPoints.empty()) {
            Log.e(TAG, "Error: objectPoints matrix is empty or not initialized.");
            return projectedPoints;
        }
        if (rvec.empty() || tvec.empty() || cameraMatrix.empty()) {
            Log.e(TAG, "Error: One or more matrices required for projection are not properly initialized.");
            return projectedPoints;
        }

        try {
            // Project the 3D points onto the 2D image plane
            Calib3d.projectPoints(objectPoints, rvec, tvec, cameraMatrix, distCoeffs, projectedPoints);
            Log.d(TAG, "Computed 2D projections of 3D points.");
        } catch (Exception e) {
            Log.e(TAG, "Exception during projectPoints: " + e.getMessage());
        }

        return projectedPoints;
    }

    // Method to match detected 2D feature points with projected 2D points
    public List<Pair<Point, Point>> match2DPoints(List<Point> detectedPoints, MatOfPoint2f projectedPoints, double threshold) {
        List<Pair<Point, Point>> matchedPoints = new ArrayList<>();
        Point[] projectedArray = projectedPoints.toArray();

        // Perform nearest-neighbor search
        for (Point detected : detectedPoints) {
            double minDistance = Double.MAX_VALUE;
            Point bestMatch = null;

            for (Point projected : projectedArray) {
                double distance = Math.sqrt(Math.pow(detected.x - projected.x, 2) + Math.pow(detected.y - projected.y, 2));
                if (distance < minDistance && distance < threshold) {
                    minDistance = distance;
                    bestMatch = projected;
                }
            }

            if (bestMatch != null) {
                matchedPoints.add(new Pair<>(detected, bestMatch));
            }
        }

        Log.d(TAG, "Matched " + matchedPoints.size() + " points.");
        return matchedPoints;
    }

    public double calculateMatchPercentage(List<Point> detectedPoints, List<Pair<Point, Point>> matchedPoints) {
        if (detectedPoints.isEmpty()) {
            return 0.0;
        }
        double matchPercentage = ((double) matchedPoints.size() / detectedPoints.size()) * 100.0;
        Log.d(TAG, "Match Percentage: " + matchPercentage + "%");
        return matchPercentage;
    }

    public List<Point3> getVertices() {
        return vertices;
    }

    public List<Point3> getNormals() {
        return normals;
    }

    public List<int[]> getFaces() {
        return faces;
    }

    public void clearModel() {
        vertices.clear();
        normals.clear();
        faces.clear();
    }
}