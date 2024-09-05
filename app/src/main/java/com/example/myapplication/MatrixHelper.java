package com.example.myapplication;

import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.Camera;

import java.nio.FloatBuffer;

public class MatrixHelper {
    public static void getModelViewProjectionMatrix(Frame frame, float[] modelViewProjectionMatrix) {
        float[] modelMatrix = new float[16];
        float[] viewMatrix = new float[16];
        float[] projectionMatrix = new float[16];
        float[] tempMatrix = new float[16];

        Camera camera = frame.getCamera();
        camera.getViewMatrix(viewMatrix, 0);
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, tempMatrix, 0);
    }
}
