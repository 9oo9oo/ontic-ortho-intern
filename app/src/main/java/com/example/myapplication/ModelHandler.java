package com.example.myapplication;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.io.InputStream;
import android.content.Context;
import android.opengl.GLES32;
import java.io.IOException;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjData;
import de.javagl.obj.ObjReader;

public class ModelHandler {
    private FloatBuffer vertexBuffer;
    private IntBuffer indexBuffer;
    private int vertexCount;
    private int indexCount;

    // Constructor to load and initialize the model
    public ModelHandler(Context context, int rawResourceId) {
        loadModel(context, rawResourceId);
    }

    // Method to load the OBJ model from raw resources
    private void loadModel(Context context, int rawResourceId) {
        try {
            InputStream inputStream = context.getResources().openRawResource(rawResourceId);
            Obj obj = ObjReader.read(inputStream);

            float[] vertices = ObjData.getVerticesArray(obj);
            int[] indices = ObjData.getFaceVertexIndicesArray(obj, 3);

            // Create buffers using the Buffer class
            vertexBuffer = new Buffer(vertices).getFloatBuffer();
            indexBuffer = new Buffer(indices).getIntBuffer();

            vertexCount = vertices.length / 3; // 3 coordinates per vertex
            indexCount = indices.length;

            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to bind vertex data to OpenGL
    public void bindData(int positionAttributeLocation) {
        // Pass in the position information
        GLES32.glVertexAttribPointer(positionAttributeLocation, 3, GLES32.GL_FLOAT, false, 0, vertexBuffer);
        GLES32.glEnableVertexAttribArray(positionAttributeLocation);
    }

    // Method to draw the model
    public void draw() {
        GLES32.glDrawElements(GLES32.GL_TRIANGLES, indexCount, GLES32.GL_UNSIGNED_INT, indexBuffer);
    }
}