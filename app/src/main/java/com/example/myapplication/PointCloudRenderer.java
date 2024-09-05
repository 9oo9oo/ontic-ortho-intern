package com.example.myapplication;

import android.opengl.GLES32;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.media.Image;

public class PointCloudRenderer {
    private static final String TAG = "PointCloudRenderer";
    private ShaderProgram pointCloudShaderProgram;
    private int pointCloudVertexBufferId;
    private int uModelViewProjectionHandle;
    private int pointCloudPositionHandle;

    public PointCloudRenderer() {
    }

    public void initPointCloud() {
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

    public void renderPointCloud(Frame frame) {
        try {
            PointCloud pointCloud = frame.acquirePointCloud();
            FloatBuffer pointCloudBuffer = pointCloud.getPoints();

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

                // Upload point cloud data to the GPU
                GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, totalFloats * Float.BYTES, directFloatBuffer, GLES32.GL_DYNAMIC_DRAW);

                // Set the model view projection matrix
                float[] modelViewProjectionMatrix = getModelViewProjectionMatrix(frame);
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

    private float[] getModelViewProjectionMatrix(Frame frame) {
        float[] modelViewProjectionMatrix = new float[16];
        MatrixHelper.getModelViewProjectionMatrix(frame, modelViewProjectionMatrix);
        return modelViewProjectionMatrix;
    }
}
