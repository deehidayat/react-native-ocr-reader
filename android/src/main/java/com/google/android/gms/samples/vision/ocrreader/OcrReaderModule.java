package com.google.android.gms.samples.vision.ocrreader;

import android.support.annotation.Nullable;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.google.android.gms.samples.vision.ocrreader.ui.OcrReaderView;
import com.google.android.gms.samples.vision.ocrreader.camera.CameraSourcePreview;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OcrReaderModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

  private OcrReaderManager mOcrReaderManager;

  public OcrReaderModule(ReactApplicationContext reactContext, OcrReaderManager ocrReaderManager) {
    super(reactContext);

    reactContext.addLifecycleEventListener(this);
    mOcrReaderManager = ocrReaderManager;
  }

  /**
   * The name intended for React Native.
   */
  @Override
  public String getName() {
    return "RCTOcrReaderModule";
  }

  @Nullable
  @Override
  public Map<String, Object> getConstants() {
    return Collections.unmodifiableMap(new HashMap<String, Object>() {
      {
        put("FocusMode", getFocusModes());
        put("CameraFillMode", getCameraFillModes());
      }
    });
  }

  private static Map<String, Integer> getFocusModes() {
    return Collections.unmodifiableMap(new HashMap<String, Integer>() {
      {
        put("AUTO", 0);
        put("TAP", 1);
        put("FIXED", 2);
      }
    });
  }

  private static Map<String, Integer> getCameraFillModes() {
    return Collections.unmodifiableMap(new HashMap<String, Integer>() {
      {
        put("COVER", CameraSourcePreview.FILL_MODE_COVER);
        put("FIT", CameraSourcePreview.FILL_MODE_FIT);
      }
    });
  }

    /* ----------------------------------------------
     * ------------- Methods for JS -----------------
     * ---------------------------------------------- */

  @ReactMethod
  public void resume(Promise promise) {
    if (resume())
      promise.resolve(null);
    else
      promise.reject("2", "Attempted to RESUME barcode scanner before scanner view was instantiated.");
  }

  @ReactMethod
  public void pause(Promise promise) {
    if (pause())
      promise.resolve(null);
    else
      promise.reject("3", "Attempted to PAUSE barcode scanner before scanner view was instantiated.");
  }

    /* ----------------------------------------------
     * ------------- Lifecycle events ---------------
     * ---------------------------------------------- */

  @Override
  public void onHostResume() {
    resume();
  }

  @Override
  public void onHostPause() {
    pause();
  }

  @Override
  public void onHostDestroy() {
    release();
  }


    /* ----------------------------------------------
     * ------------- Utility methods ----------------
     * ---------------------------------------------- */

  private boolean start() {
    OcrReaderView view = mOcrReaderManager.getOcrReaderView();

    if (view != null) {
      view.start();
    }

    return view != null;
  }

  private boolean resume() {
    OcrReaderView view = mOcrReaderManager.getOcrReaderView();

    if (view != null) {
      view.resume();
    }

    return view != null;
  }

  private boolean pause() {
    OcrReaderView view = mOcrReaderManager.getOcrReaderView();

    if (view != null) {
      view.pause();
    }

    return view != null;
  }

  private boolean release() {
    OcrReaderView view = mOcrReaderManager.getOcrReaderView();

    if (view != null) {
      view.release();
    }

    return view != null;
  }
}