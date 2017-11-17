package com.google.android.gms.samples.vision.ocrreader;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;

import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.google.android.gms.samples.vision.ocrreader.ui.OcrReaderView;


/**
 * React Native ViewManager corresponding to OcrReaderView
 */

public class OcrReaderManager extends SimpleViewManager<OcrReaderView> {

    private OcrReaderView mOcrReaderView;
    public static int RC_HANDLE_CAMERA_PERM = 176; // must be < 256

    @Override
    public String getName() {
        return "RCTOcrReaderManager";
    }

    @Override
    protected OcrReaderView createViewInstance(ThemedReactContext reactContext) {
        mOcrReaderView = new OcrReaderView(reactContext);
        return mOcrReaderView;
    }

    public OcrReaderView getOcrReaderView() {
        return mOcrReaderView;
    }

    /*
     * -----------------------------------
     * ------------- Props ---------------
     * -----------------------------------
     */

    // Focus modes
    // Possible values: 0 = continuous focus (if supported), 1 = tap-to-focus (if supported), 2 = fixed focus
    @ReactProp(name = "focusMode", defaultInt = 0)
    public void setFocusMode(OcrReaderView view, int focusMode) {
        view.setFocusMode(focusMode);
    }

    // Fill modes
    // Possible values: 0 = cover the whole view, 1 = fit within view
    @ReactProp(name = "cameraFillMode", defaultInt = 0)
    public void setCameraFillMode(OcrReaderView view, int cameraFillMode) {
        view.setCameraFillMode(cameraFillMode);
    }

    /**
     * Handle results from requestPermissions.
     * Call this method from MainActivity.java in your React Native app or implement a version of your own that checks for the camera permission.
     */
    public void onRequestPermissionsResult(Activity activity, int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            // The permission result doesn't concern this app
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // we have permission, so create the camerasource
            mOcrReaderView.init();
            return;
        }

        // Permission was not granted. Show a message
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("No permission")
            .setMessage("No permission.")
            .setPositiveButton("Ok", null)
            .show();
    }
}
