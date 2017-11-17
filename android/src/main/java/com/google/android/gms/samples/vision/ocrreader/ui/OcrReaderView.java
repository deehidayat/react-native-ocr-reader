package com.google.android.gms.samples.vision.ocrreader.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;

import com.facebook.react.bridge.WritableArray;
import com.google.android.gms.samples.vision.ocrreader.camera.CameraSource;
import com.google.android.gms.samples.vision.ocrreader.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.ocrreader.camera.GraphicOverlay;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;
import java.util.Locale;

public class OcrReaderView extends ViewGroup implements CameraSource.AutoFocusCallback, Detector.Processor<TextBlock> {

    private final static String TAG = "OCR_READER_VIEW";
    private final Context mContext;
    private boolean hasAllCapabilities = false; // barcode scanner library and newest play services

    private static final String TEXT_READ = "text_read";
    private static final String LOW_STORAGE_KEY = "low_storage";
    private static final String NOT_YET_OPERATIONAL = "not_yet_operational";
    private static final String NO_PLAY_SERVICES_KEY = "no_play_services";

    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // For focusing we prefer two continuous methods first, and then finally the "auto" mode which is fired on tap.
    // A device should support at least one of these for scanning to be possible at all.
    private static final String[] PREFERRED_FOCUS_MODES = {Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE, Camera.Parameters.FOCUS_MODE_AUTO, Camera.Parameters.FOCUS_MODE_FIXED};

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private TextRecognizer textRecognizer;
    private boolean mIsPaused = true;

    // Helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    // A TextToSpeech engine for speaking a String value.
    private TextToSpeech tts;

    public OcrReaderView(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public OcrReaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public OcrReaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init();
    }

    private boolean hasCameraPermission() {
        int rc = ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA);
        return rc == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNecessaryCapabilities() {
        return hasCameraPermission() && hasAllCapabilities;
    }

    public void init() {
        mPreview = new CameraSourcePreview(mContext, null);

        mGraphicOverlay = new GraphicOverlay<OcrGraphic>(mContext, null);

        mPreview.addView(mGraphicOverlay);

        addView(mPreview);

        gestureDetector = new GestureDetector(mContext.getApplicationContext(), new OcrReaderView.CaptureGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(mContext.getApplicationContext(), new OcrReaderView.ScaleListener());

        // Set up the Text To Speech engine.
        TextToSpeech.OnInitListener listener =
                new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(final int status) {
                        if (status == TextToSpeech.SUCCESS) {
                            Log.d("OnInitListener", "Text to speech engine started successfully.");
                            tts.setLanguage(Locale.US);
                        } else {
                            Log.d("OnInitListener", "Error starting the text to speech engine.");
                        }
                    }
                };
        tts = new TextToSpeech(mContext.getApplicationContext(), listener);

        start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!hasCameraPermission()) {
            // No camera permission. Alert user.
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle("No Camera permission")
                .setMessage("Enable camera permission in settings to use the scanner.")
                .setPositiveButton("Ok", null)
                .show();

            return;
        }

        /**
         * Check for a few other things that the device needs for the scanner to work.
         * And send a JS event if something goes wrongs.
         *
         * Checklist: (things are checked in this order)
         * 1. The device has the latest play services
         * 2. The device has sufficient storage
         * 3. The scanner dependencies are downloaded
         */

        // check that the device has (the latest) play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext.getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            sendNativeEvent(NO_PLAY_SERVICES_KEY, Arguments.createMap());
        } else if (textRecognizer != null && !textRecognizer.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = mContext.registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                // Detector dependencies can't be downloaded due to low storage
                sendNativeEvent(LOW_STORAGE_KEY, Arguments.createMap());
            } else {
                // Storage isn't low, but dependencies haven't been downloaded yet
                sendNativeEvent(NOT_YET_OPERATIONAL, Arguments.createMap());
            }
        } else {
            hasAllCapabilities = true;
            start();
        }
    }

    /**
     * Start the camera for the first time.
     */
    public void start() {
        if (!hasNecessaryCapabilities())
            return;

        createCameraSource();
        startCameraSource();
    }

    /**
     * Restarts the camera.
     */
    public void resume() {
        // start the camera only if it isn't already running
        if (mIsPaused && hasNecessaryCapabilities()) {
            startCameraSource();
        }
    }

    /**
     * Stops the camera.
     */
    public void pause() {
        if (mPreview != null && !mIsPaused && hasNecessaryCapabilities()) {
            mPreview.stop();
            mIsPaused = true;
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    public void release() {
        if (mPreview != null && hasNecessaryCapabilities()) {
            mPreview.release();
            mIsPaused = true;
        }
    }

    /**
     * Set focus mode.
     * Possible values: 0 = continuous focus (if supported), 1 = tap-to-focus (if supported), 2 = fixed focus
     * @param focusMode
     */
    public boolean setFocusMode(int focusMode) {
        if (focusMode < 0 || focusMode > 2) {
            focusMode = 0;
        }

        return mCameraSource != null && mCameraSource.setFocusMode(PREFERRED_FOCUS_MODES[focusMode]);
    }

    /**
     * Set camera fill mode.
     * Possible values:
     *   0 = camera stream will fill the entire view (possibly being cropped)
     *   1 = camera stream will fit snugly within the view (possibly showing fat borders around)
     */
    public void setCameraFillMode(int fillMode) {
//        if (mPreview != null) {
//            mPreview.setFillMode(fillMode);
//        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0, len = getChildCount(); i < len; i++) {
            // tell the child to fill the whole view when layouting
            getChildAt(i).layout(0, 0, r - l, b - t);
        }
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     *
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource() {
        // Set good defaults for capturing text.
        boolean autoFocus = true;
        boolean useFlash = false;

        Context context = mContext;

        // A text recognizer is created to find text.  An associated multi-processor instance
        // is set to receive the text recognition results, track the text, and maintain
        // graphics for each text block on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each text block.
        textRecognizer = new TextRecognizer.Builder(context).build();
        textRecognizer.setProcessor(this);

        if (!hasNecessaryCapabilities()) {
            return;
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the text recognizer to detect small pieces of text.
        mCameraSource =
                new CameraSource.Builder(mContext.getApplicationContext(), textRecognizer)
                        .setFacing(CameraSource.CAMERA_FACING_BACK)
                        .setRequestedPreviewSize(1280, 1024)
                        .setRequestedFps(2.0f)
                        .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                        .setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null)
                        .build();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
                mIsPaused = false;
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        } else {
            Log.d(TAG, "Camera source is null!");
        }
    }

    private void tryAutoFocus() {
        if (mCameraSource != null) {
            mCameraSource.autoFocus(this);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mCameraSource != null && mCameraSource.getFocusMode() != null && mCameraSource.getFocusMode().equals(Camera.Parameters.FOCUS_MODE_AUTO)) {
            tryAutoFocus();
            return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    public void onAutoFocus(boolean success) {
        // No actions needed for the focus callback.
        Log.d(TAG, "Did autofocus.");
    }

    @Override
    public void receiveDetections(Detector.Detections<TextBlock> detections) {
        mGraphicOverlay.clear();
        SparseArray<TextBlock> items = detections.getDetectedItems();
        WritableArray text = Arguments.createArray();
        for (int i = 0; i < items.size(); ++i) {
            TextBlock item = items.valueAt(i);
            if (item != null && item.getValue() != null) {
                Log.d("OcrDetectorProcessor", "Text detected! " + item.getValue());
                text.pushString(item.getValue());
            }
            OcrGraphic graphic = new OcrGraphic(mGraphicOverlay, item);
            mGraphicOverlay.add(graphic);
        }

        // Fungsi untuk mengirim data hasi Detection ke Javascript module
        WritableMap event = Arguments.createMap();
        event.putArray("data", text);
        sendNativeEvent(TEXT_READ, event);
    }

    private void sendNativeEvent(String key, WritableMap event) {
        if (getId() < 0) {
            Log.w(TAG, "Tried to send native event with negative id!");
            return;
        }

        event.putString("key", key);

        // Send the newly found data to the JS side
        ReactContext reactContext = (ReactContext) mContext;
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
            getId(),
            "topChange",
            event);
    }


    /**
     * onTap is called to speak the tapped TextBlock, if any, out loud.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the tap was on a TextBlock
     */
    private boolean onTap(float rawX, float rawY) {
        OcrGraphic graphic = (OcrGraphic) mGraphicOverlay.getGraphicAtLocation(rawX, rawY);
        TextBlock text = null;
        if (graphic != null) {
            text = graphic.getTextBlock();
            if (text != null && text.getValue() != null) {
                Log.d(TAG, "text data is being spoken! " + text.getValue());
                // Speak the string.
                tts.speak(text.getValue(), TextToSpeech.QUEUE_ADD, null, "DEFAULT");
            }
            else {
                Log.d(TAG, "text data is null");
            }
        }
        else {
            Log.d(TAG,"no text detected");
        }
        return text != null;
    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress.
         * Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should consider this event
         * as handled. If an event was not handled, the detector
         * will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example,
         * only wants to update scaling factors if the change is
         * greater than 0.01.
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return false;
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by
         * new pointers going down.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should continue recognizing
         * this gesture. For example, if a gesture is beginning
         * with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the
         * rest of the gesture.
         */
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        /**
         * Responds to the end of a scale gesture. Reported by existing
         * pointers going up.
         * <p/>
         * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()}
         * and {@link ScaleGestureDetector#getFocusY()} will return focal point
         * of the pointers remaining on the screen.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         */
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (mCameraSource != null) {
                mCameraSource.doZoom(detector.getScaleFactor());
            }
        }
    }
}
