package com.example.myapplication;

import android.opengl.GLES32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

public class Model {
    private FloatBuffer vertexBuffer;
    private FloatBuffer normalBuffer;
    private IntBuffer indexBuffer;

    public Model(List<Float> vertices, List<Float> normals, List<Float> textures, List<Integer> indices) {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size() * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(toFloatArray(vertices)).position(0);

        normalBuffer = ByteBuffer.allocateDirect(normals.size() * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        normalBuffer.put(toFloatArray(normals)).position(0);

        indexBuffer = ByteBuffer.allocateDirect(indices.size() * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        indexBuffer.put(toIntArray(indices)).position(0);
    }

    public void draw() {  // Remove the GL10 parameter
        GLES32.glEnableVertexAttribArray(0);
        GLES32.glVertexAttribPointer(0, 3, GLES32.GL_FLOAT, false, 0, vertexBuffer);

        GLES32.glEnableVertexAttribArray(1);
        GLES32.glVertexAttribPointer(1, 3, GLES32.GL_FLOAT, false, 0, normalBuffer);

        GLES32.glDrawElements(GLES32.GL_TRIANGLES, indexBuffer.remaining(), GLES32.GL_UNSIGNED_SHORT, indexBuffer);

        GLES32.glDisableVertexAttribArray(0);
        GLES32.glDisableVertexAttribArray(1);
    }
    private float[] toFloatArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private int[] toIntArray(List<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}