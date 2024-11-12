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
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;

import org.opencv.android.OpenCVLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * MainActivity is the entry point of the application.
 * It handles the initialization of OpenCV, ARCore, OpenGL rendering,
 * and user interactions with the UI.
 */
public class MainActivity extends Activity implements MatchPercentageListener {

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final String TAG = "MainActivity";

    private Session arSession;
    private boolean userRequestedInstall = true;

    private GLSurfaceView glSurfaceView;
    private CombinedRenderer renderer;

    private CADModelLoader cadModelLoader;

    /**
     * Called when the activity is first created.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down, this Bundle contains the data it most
     *                           recently supplied in onSaveInstanceState().
     *                           <b>Note: Otherwise, it is null.</b>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Set the UI layout

        // Initialize OpenCV library
        if (!OpenCVLoader.initDebug()) {
            // Initialization failed
            Log.e(TAG, "OpenCV initialization failed.");
            Toast.makeText(this, "OpenCV initialization failed.", Toast.LENGTH_LONG).show();
        } else {
            // Initialization succeeded
            Log.i(TAG, "OpenCV initialization succeeded.");
        }

        // Setup GLSurfaceView for OpenGL rendering
        glSurfaceView = findViewById(R.id.gl_surface_view);
        glSurfaceView.setPreserveEGLContextOnPause(true); // Preserve the OpenGL context on pause
        glSurfaceView.setEGLContextClientVersion(3);
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        // Initialize CADModelLoader and load the CAD model
        cadModelLoader = new CADModelLoader(this);
        loadCADModel("fixed.obj"); // Load the CAD model from assets

        // Initialize CombinedRenderer with the CADModelLoader
        renderer = new CombinedRenderer(cadModelLoader, this);
        renderer.setMatchPercentageListener(this);
        glSurfaceView.setRenderer(renderer); // Set the renderer for the GLSurfaceView
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY); // Set render mode to continuous

        // Setup the compute button to trigger feature matching
        Button computeButton = findViewById(R.id.compute_button);
        computeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // Handle button click
                renderer.requestCompute(); // Request computation for feature matching
            }
        });

        // Check and request camera permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Request camera permission if not granted
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            // Initialize ARCore if permission is already granted
            initializeARCore();
        }
    }

    @Override
    public void onMatchPercentageCalculated(double matchPercentage) {
        // Format the match percentage to 3 decimal places
        String formattedPercentage = String.format(Locale.US, "%.3f%%", matchPercentage);

        // Display the match percentage in a Toast
        Toast.makeText(this, "Match Percentage: " + formattedPercentage, Toast.LENGTH_LONG).show();
    }

    /**
     * Loads the CAD model from the assets folder.
     *
     * @param filename The name of the CAD model file (e.g., "fixed.obj").
     */
    private void loadCADModel(String filename) {
        try (InputStream cadModelInputStream = getAssets().open(filename)) {
            // Open the OBJ file
            cadModelLoader.loadModel(cadModelInputStream); // Load the model data
            Log.i(TAG, "CAD model loaded successfully from " + filename);
        } catch (IOException e) {
            // Handle I/O exceptions
            Log.e(TAG, "Failed to load the CAD model.", e);
            Toast.makeText(this, "Error loading model file", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Initializes the ARCore session and configures its settings.
     */
    private void initializeARCore() {
        try {
            if (arSession == null) {
                // Check if ARCore is installed and up to date
                switch (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                    case INSTALLED:
                        // Create a new ARCore session
                        arSession = new Session(this);

                        // Configure ARCore settings
                        Config config = new Config(arSession);
                        config.setFocusMode(Config.FocusMode.AUTO);
                        config.setDepthMode(Config.DepthMode.AUTOMATIC);
                        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
                        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                        arSession.configure(config);

                        // Assign the ARCore session to the renderer
                        renderer.setSession(arSession);
                        Log.i(TAG, "ARCore session initialized successfully.");
                        break;

                    case INSTALL_REQUESTED:
                        // Installation of ARCore requested
                        userRequestedInstall = false;
                        Log.i(TAG, "ARCore installation requested.");
                        break;
                }
            }
        } catch (UnavailableException e) {
            // Handle exceptions related to ARCore availability
            Log.e(TAG, "ARCore is not available", e);
            Toast.makeText(this, "ARCore is not available", Toast.LENGTH_LONG).show();
            finish(); // Close the app
        }
    }

    /**
     * Called when the activity is resumed.
     * Resumes the ARCore session and the GLSurfaceView rendering.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (arSession != null) {
            try {
                arSession.resume(); // Resume the ARCore session
                glSurfaceView.onResume(); // Resume OpenGL rendering
            } catch (CameraNotAvailableException e) {
                // Handle the exception when the camera is not available
                Toast.makeText(this, "Camera not available. Try restarting the app.", Toast.LENGTH_LONG).show();
                arSession = null;
                Log.e(TAG, "Camera not available: " + e.getMessage());
            }
        }
    }

    /**
     * Called when the activity is paused.
     * Pauses the ARCore session and the GLSurfaceView rendering.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            glSurfaceView.onPause(); // Pause OpenGL rendering
        }
        if (arSession != null) {
            arSession.pause(); // Pause the ARCore session
        }
    }

    /**
     * Called when the activity is destroyed.
     * Closes the ARCore session and releases resources.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (arSession != null) {
            arSession.close(); // Close the ARCore session
            arSession = null;
        }
    }

    /**
     * Callback for the result from requesting permissions.
     *
     * @param requestCode  The request code passed in requestPermissions().
     * @param permissions  The requested permissions.
     * @param grantResults The grant results for the corresponding permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted; initialize ARCore
                initializeARCore();
            } else {
                // Permission denied; inform the user and close the app
                Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
