package com.example.myapplication;

import android.util.Log;

import org.opencv.core.Point3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code OBJParser} class is responsible for parsing OBJ files
 * and extracting vertex positions, normals, and face indices.
 * It supports basic parsing of vertices (v), vertex normals (vn), and faces (f).
 */
public class OBJParser {

    private static final String TAG = "OBJParser";

    private List<Point3> vertices = new ArrayList<>();
    private List<Point3> normals = new ArrayList<>();
    private List<int[]> faces = new ArrayList<>();

    /**
     * Parses the OBJ file from the given input stream.
     *
     * @param inputStream The input stream of the OBJ file.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    public void parse(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        // Read the file line by line
        while ((line = reader.readLine()) != null) {
            // Remove leading and trailing whitespace
            line = line.trim();

            if (line.startsWith("v ")) {
                // Vertex position
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 4) {
                    double x = Double.parseDouble(tokens[1]);
                    double y = Double.parseDouble(tokens[2]);
                    double z = Double.parseDouble(tokens[3]);
                    vertices.add(new Point3(x, y, z));
                }
            } else if (line.startsWith("vn ")) {
                // Vertex normal
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 4) {
                    double x = Double.parseDouble(tokens[1]);
                    double y = Double.parseDouble(tokens[2]);
                    double z = Double.parseDouble(tokens[3]);
                    normals.add(new Point3(x, y, z));
                }
            } else if (line.startsWith("f ")) {
                // Face
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 4) {
                    // Assuming triangular faces
                    int[] faceVertexIndices = new int[3];
                    for (int i = 1; i <= 3; i++) {
                        String[] vertexData = tokens[i].split("/");
                        int vertexIndex = Integer.parseInt(vertexData[0]) - 1; // OBJ indices start at 1
                        faceVertexIndices[i - 1] = vertexIndex;
                    }
                    faces.add(faceVertexIndices);
                }
            }
            // Ignore other lines (vt, g, usemtl, etc.)
        }
        reader.close();
        Log.i(TAG, "OBJ parsing completed. Vertices: " + vertices.size() + ", Normals: " + normals.size() + ", Faces: " + faces.size());
    }

    /**
     * Returns the list of vertex positions.
     *
     * @return A list of {@link Point3} representing vertex positions.
     */
    public List<Point3> getVertices() {
        return vertices;
    }

    /**
     * Returns the list of vertex normals.
     *
     * @return A list of {@link Point3} representing vertex normals.
     */
    public List<Point3> getNormals() {
        return normals;
    }

    /**
     * Returns the list of face indices.
     *
     * @return A list of integer arrays, each containing vertex indices for a face.
     */
    public List<int[]> getFaces() {
        return faces;
    }
}
