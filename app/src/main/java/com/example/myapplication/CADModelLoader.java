package com.example.myapplication;

import android.content.Context;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.Point3;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The {@code CADModelLoader} class is responsible for loading CAD models from OBJ files,
 * parsing the model data, and initializing a renderer for OpenGL rendering.
 */
public class CADModelLoader {

    private static final String TAG = "CADModelLoader";

    private final Context context;
    private CADModelRenderer renderer;

    // Model data
    private float[] vertices;
    private float[] normals;
    private List<int[]> faces;

    /**
     * Constructs a new {@code CADModelLoader} with the given context.
     *
     * @param context The application context used for accessing resources.
     */
    public CADModelLoader(Context context) {
        this.context = context;
    }

    /**
     * Loads a CAD model from the provided input stream.
     * Parses the OBJ file and initializes the renderer with the model data.
     *
     * @param inputStream The input stream of the OBJ file.
     * @throws IOException If an I/O error occurs while reading the input stream.
     */
    public void loadModel(InputStream inputStream) throws IOException {
        // Parse the OBJ file using OBJParser
        OBJParser parser = new OBJParser();
        parser.parse(inputStream);

        // Retrieve parsed data
        List<Point3> vertexList = parser.getVertices();
        List<Point3> normalList = parser.getNormals();
        faces = parser.getFaces();

        // Convert List<Point3> to float arrays
        convertPoint3ListToFloatArray(vertexList, true);
        convertPoint3ListToFloatArray(normalList, false);

        // Initialize the renderer with the loaded model data
        renderer = new CADModelRenderer(context, vertices, normals, faces);
    }

    /**
     * Converts a list of {@link Point3} objects to a float array.
     *
     * @param pointList The list of {@code Point3} objects.
     * @param isVertex  {@code true} if the list represents vertices; {@code false} if normals.
     * @return A float array containing the point data.
     */
    private void convertPoint3ListToFloatArray(List<Point3> pointList, boolean isVertex) {
        if (pointList == null || pointList.isEmpty()) {
            Log.w(TAG, (isVertex ? "Vertex" : "Normal") + " list is empty or null.");
            return;
        }

        // Allocate array for x, y, z coordinates of each point
        float[] array = new float[pointList.size() * 3];
        int index = 0;
        for (Point3 point : pointList) {
            array[index++] = (float) point.x;
            array[index++] = (float) point.y;
            array[index++] = (float) point.z;
        }

        if (isVertex) {
            vertices = array;
        } else {
            normals = array;
        }
    }

    /**
     * Renders the CAD model from multiple viewpoints and returns the rendered images.
     *
     * @return A list of {@link Mat} objects containing the rendered images,
     *         or {@code null} if the renderer is not initialized.
     */
    public List<Mat> renderCADModelFromViewpoints() {
        if (renderer == null) {
            Log.e(TAG, "Renderer is not initialized. Call loadModel() first.");
            return null;
        }
        return renderer.renderFromViewpoints();
    }

    /**
     * Initializes OpenGL resources required for rendering.
     * Should be called after OpenGL context is created.
     */
    public void initOpenGL() {
        if (renderer != null) {
            renderer.initOpenGL();
        } else {
            Log.e(TAG, "Renderer is not initialized. Call loadModel() first.");
        }
    }

    /**
     * Releases resources held by the renderer.
     * Should be called when the renderer is no longer needed.
     */
    public void release() {
        if (renderer != null) {
            renderer.release();
        }
    }
}
