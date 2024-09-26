package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.Config;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point3;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;


public class MainActivity extends Activity {

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final String TAG = "MainActivity";
    private Session arSession;
    private boolean userRequestedInstall = true;
    private GLSurfaceView glSurfaceView;
    private CombinedRenderer renderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed.");
        } else {
            Log.i(TAG, "OpenCV initialization succeeded.");
        }

        glSurfaceView = findViewById(R.id.gl_surface_view);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        InputStream cadModelInputStream = getResources().openRawResource(R.raw.model);
        renderer = new CombinedRenderer(cadModelInputStream);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        Button computeButton = findViewById(R.id.compute_button);
        computeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renderer.requestCompute();
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            initializeARCore();
        }

        loadCADModel();
    }

    private void initializeARCore() {
        try {
            if (arSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                    case INSTALLED:
                        arSession = new Session(this);

                        Config config = new Config(arSession);
                        config.setFocusMode(Config.FocusMode.AUTO );  // Enable auto-focus mode
                        config.setDepthMode(Config.DepthMode.AUTOMATIC);  // Enable depth mode
                        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
                        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);

                        arSession.configure(config);
                        renderer.setSession(arSession);
                        Log.i(TAG, "ARCore session initialized successfully.");
                        break;

                    case INSTALL_REQUESTED:
                        userRequestedInstall = false;
                        Log.i(TAG, "ARCore installation requested.");
                }
            }
        } catch (UnavailableException e) {
            Log.e(TAG, "ARCore is not available", e);
            Toast.makeText(this, "ARCore is not available", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadCADModel() {
        try {
            // Load the CAD model file from the assets folder
            InputStream inputStream = getAssets().open("radiusCollesFracture_hollow_right.obj"); // Path to your model file
            CADModelLoader loader = new CADModelLoader();
            loader.loadModel(inputStream);
            inputStream.close();

            // Get the loaded model data
            List<Point3> modelVertices = loader.getVertices(); // Use these for further processing
            List<Point3> modelNormals = loader.getNormals();   // Use normals for feature extraction
            List<int[]> modelFaces = loader.getFaces();        // Use faces for constructing mesh

            // Example log to show successful loading
            Log.i(TAG, "Model loaded with " + modelVertices.size() + " vertices, " +
                    modelNormals.size() + " normals, and " + modelFaces.size() + " faces.");

        } catch (IOException e) {
            Log.e(TAG, "Failed to load the CAD model.", e);
            Toast.makeText(this, "Error loading model file", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (arSession != null) {
            try {
                arSession.resume();
                glSurfaceView.onResume();
            } catch (CameraNotAvailableException e) {
                Toast.makeText(this, "Camera not available. Try restarting the app.", Toast.LENGTH_LONG).show();
                arSession = null;
                Log.e(TAG, "Camera not available: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
        if (arSession != null) {
            arSession.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (arSession != null) {
            arSession.close();
            arSession = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeARCore();
            } else {
                Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}