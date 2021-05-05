package com.google.android.libraries.accessibility.utils.screencapture;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.libraries.accessibility.utils.bitmap.BitmapUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.nio.ByteBuffer;

/**
 * Manages the capture of images from the device's frame buffer as {@link Bitmap}s via
 * {@link MediaProjection} APIs.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
public class ScreenCaptureController {

  private static final String TAG = "ScreenCaptureController";

  private static final String VIRTUAL_DISPLAY_NAME =
      "com.google.android.libraries.accessibility.utils.screencapture.VIRTUAL_DISPLAY_SCREEN_CAPTURE";

  /** Context used for authorizing screen capture */
  private final Context context;

  /** Handler used to post callbacks */
  private Handler handler;

  /** System service instance for obtaining a screen capture authorization token */
  private MediaProjectionManager projectionManager;

  /** Used to register for local broadcasts in changes to screen capture authorization state */
  private LocalBroadcastManager broadcastManager;

  /** Used to keep track of the virtual display so it can be released */
  private VirtualDisplay virtualDisplay;

  /** Used to track our ImageReader and ensure it isn't garbage collected */
  private ImageReader imageReader;

  /** Callback used to deauthorize capture if projection is stopped by the system */
  private final MediaProjection.Callback projectionCallback =
      new MediaProjection.Callback() {
        @Override
        public void onStop() {
          deauthorizeCapture();
        }
      };

  /**
   * Screen capture authorization token. When {@code null}, the user has not authorized us to
   * capture frames.
   */
  private MediaProjection activeProjection;

  public ScreenCaptureController(Context context) {
    this(context, new Handler(Looper.getMainLooper()));
  }

  public ScreenCaptureController(Context context, Handler handler) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
      this.context = null;
      return;
    }

    this.context = context;
    this.handler = handler;
    this.projectionManager =
        (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    this.broadcastManager = LocalBroadcastManager.getInstance(context);
  }

  /**
   * @return {@code True} if this instance has been authorized by the user to request screen capture
   *         data, {@code false} otherwise.
   */
  public boolean canRequestScreenCapture() {
    return activeProjection != null;
  }

  /**
   * Begins the asynchronous process by which the user authorizes this instance to request screen
   * capture data.
   *
   * @param listener An {@link AuthorizationListener} which can be used to determine when the user
   *        authorizes this instance to request screen capture data or when the user declines the
   *        authorization request.
   */
  public void authorizeCaptureAsync(final @Nullable AuthorizationListener listener) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
      return;
    }

    if (canRequestScreenCapture()) {
      LogUtils.w(TAG, "Authorization requested for previously authorized instance.");
      // Instance already authorized
      handler.post(
          () -> {
            if (listener != null) {
              listener.onAuthorizationFinished(true);
            }
          });
      return;
    }

    // Begin authorization
    handler.post(
        () -> {
          if (listener != null) {
            listener.onAuthorizationStarted();
          }
        });

    ScreenshotAuthorizationReceiver receiver = new ScreenshotAuthorizationReceiver(listener);
    broadcastManager.registerReceiver(receiver, receiver.getFilter());

    Intent intent = new Intent(context, ScreenshotAuthProxyActivity.class);
    intent.putExtra(ScreenshotAuthProxyActivity.INTENT_EXTRA_SCREEN_CAPTURE_INTENT,
        projectionManager.createScreenCaptureIntent());
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    context.startActivity(intent);
  }

  /**
   * Deauthorizes this instance from being able to request screen capture data. Once called,
   * {@link #authorizeCaptureAsync(AuthorizationListener)} may be invoked to request a new
   * authorization, and calls to {@link #requestScreenCaptureAsync(CaptureListener)} will implicitly
   * attempt to authorize this instance.
   */
  public void deauthorizeCapture() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
      return;
    }

    LogUtils.i(TAG, "Deauthorizing.");
    if (activeProjection != null) {
      activeProjection.unregisterCallback(projectionCallback);
      activeProjection.stop();
      activeProjection = null;
    }
    if (virtualDisplay != null) {
      virtualDisplay.release();
      virtualDisplay = null;
    }
    if (imageReader != null) {
      imageReader.close();
      imageReader = null;
    }
  }

  /**
   * Deauthorizes capture and shuts down all resources managed by this instance.
   */
  public void shutdown() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
      return;
    }

    deauthorizeCapture();
  }

  /**
   * Captures a single frame from the device's frame buffer. The frame is captured asynchronously
   * and passed to the supplied {@code listener}'s
   * {@link CaptureListener#onScreenCaptureFinished(Bitmap, boolean)}
   * <p>
   * NOTE: If this instance has not been authorized and {@link #canRequestScreenCapture()} returns
   * {@code false}, calling this method will attempt to automatically authorize and deauthorize this
   * instance. This process is asynchronous and may require the user to interact with a consent
   * dialog displayed by the Android OS prior to performing screen capture. As such, screen capture
   * may not occur at the exact point in time that it was requested. For more precise control over
   * when screen capture occurs, use {@link #authorizeCaptureAsync(AuthorizationListener)} and wait
   * for a successful callback before calling this method.
   *
   * @param listener A {@link CaptureListener} to be notified when screen capture has completed.
   */
  public void requestScreenCaptureAsync(final CaptureListener listener) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
      return;
    }

    if (!canRequestScreenCapture()) {
      requestManagedScreenCaptureAsync(listener);
      return;
    }

    DisplayMetrics metrics = new DisplayMetrics();
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    windowManager.getDefaultDisplay().getRealMetrics(metrics);
    imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels,
        PixelFormat.RGBA_8888, 1);
    imageReader.setOnImageAvailableListener(new ScreenCaptureImageProcessor(listener), handler);

    // Create a virtual display to hold captured frames and implicitly start projection
    if (virtualDisplay != null) {
      virtualDisplay.release();
    }

    try {
      virtualDisplay = activeProjection.createVirtualDisplay(VIRTUAL_DISPLAY_NAME,
          metrics.widthPixels,
          metrics.heightPixels,
          metrics.densityDpi,
          DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
          imageReader.getSurface(),
          null,
          null);
    } catch (SecurityException se) {
      LogUtils.e(TAG, "Unexpected invalid MediaProjection token");
      deauthorizeCapture();
      handler.post(
          () -> {
            if (listener != null) {
              listener.onScreenCaptureFinished(null, true);
            }
          });
    }
  }

  private void requestManagedScreenCaptureAsync(final CaptureListener clientListener) {
    // Define our own CaptureListener which will invoke the provided listener before automatically
    // deauthorizing this instance.
    CaptureListener managedCaptureListener =
        new CaptureListener() {

          @Override
          public void onScreenCaptureFinished(Bitmap screenCapture, boolean isFormatSupported) {
            clientListener.onScreenCaptureFinished(screenCapture, isFormatSupported);
            deauthorizeCapture();
          }
        };

    // Define our own AuthorizationListener that will request screen capture using our managed
    // CaptureListener following a successful authorization.
    AuthorizationListener managedAuthListener =
        new AuthorizationListener() {

          @Override
          public void onAuthorizationStarted() {
            /* no implementation needed */
          }

          @Override
          public void onAuthorizationFinished(boolean success) {
            if (success) {
              requestScreenCaptureAsync(managedCaptureListener);
            } else {
              // Authorization was not granted so pass the user a null Bitmap and truthy format
              // boolean.
              managedCaptureListener.onScreenCaptureFinished(null, true);
            }
          }
        };

    // Begin the authorization and capture process
    authorizeCaptureAsync(managedAuthListener);
  }

  /**
   * BroadcastReceiver used to handle broadcasts from ScreenshotAuthProxyActivity, which notifies
   * this class of changes in the user consent state for screen capture.
   */
  class ScreenshotAuthorizationReceiver extends BroadcastReceiver {

    public static final String ACTION_SCREEN_CAPTURE_AUTHORIZED =
        "com.google.android.libraries.accessibility.utils.screencapture.ACTION_SCREEN_CAPTURE_AUTHORIZED";

    public static final String ACTION_SCREEN_CAPTURE_NOT_AUTHORIZED =
        "com.google.android.libraries.accessibility.utils.screencapture.ACTION_SCREEN_CAPTURE_NOT_AUTHORIZED";

    public static final String INTENT_EXTRA_SCREEN_CAPTURE_AUTH_INTENT =
        "com.google.android.libraries.accessibility.utils.screencapture.EXTRA_SCREEN_CAPTURE_AUTH_INTENT";

    /** Callback to fire when the user authorizes or fails to authorize screen capture. */
    private final AuthorizationListener listener;

    public ScreenshotAuthorizationReceiver(AuthorizationListener listener) {
      this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      if (ACTION_SCREEN_CAPTURE_AUTHORIZED.equals(intent.getAction())) {
        LogUtils.i(TAG, "Screen capture was authorized.");
        Intent systemIntent =
            (Intent) intent.getParcelableExtra(INTENT_EXTRA_SCREEN_CAPTURE_AUTH_INTENT);
        if (systemIntent != null) {
          MediaProjection projection = null;
          try {
            projection = projectionManager.getMediaProjection(Activity.RESULT_OK, systemIntent);
          } catch (IllegalStateException ise) {
            LogUtils.e(TAG, "MediaProjectionManager indicated projection has already started.");
          }
          if (projection != null) {
            LogUtils.i(TAG, "Obtained MediaProjection from system.");
            activeProjection = projection;
            activeProjection.registerCallback(projectionCallback, null);
            deliverResult(true);
          } else {
            LogUtils.e(TAG, "Unable to obtain MediaProjection from system.");
            deliverResult(false);
          }
        } else {
          LogUtils.e(TAG, "Screen capture token was not valid.");
          deliverResult(false);
        }
      } else if (ACTION_SCREEN_CAPTURE_NOT_AUTHORIZED.equals(intent.getAction())) {
        LogUtils.w(TAG, "Screen capture was not authorized.");
        deliverResult(false);
      }

      broadcastManager.unregisterReceiver(this);
    }

    public IntentFilter getFilter() {
      IntentFilter filter = new IntentFilter();
      filter.addAction(ACTION_SCREEN_CAPTURE_AUTHORIZED);
      filter.addAction(ACTION_SCREEN_CAPTURE_NOT_AUTHORIZED);
      return filter;
    }

    private void deliverResult(final boolean success) {
      if (listener != null) {
        handler.post(() -> listener.onAuthorizationFinished(success));
      }
    }
  }

  /**
   * Manages the conversion of screen capture data from an {@link ImageReader} backed by the frame
   * buffer to a {@link Bitmap}.
   */
  private class ScreenCaptureImageProcessor implements ImageReader.OnImageAvailableListener {

    private final CaptureListener listener;

    public ScreenCaptureImageProcessor(CaptureListener listener) {
      this.listener = listener;
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
      // Unregister ourselves as soon as the first ImageReader frame is available.
      reader.setOnImageAvailableListener(null, null);

      // Copying the frame buffer from ImageReader to a Bitmap is expensive, so we push that work to
      // another thread, and post the callback on the main thread when this operation finishes.
      new Thread(
              () -> {
                boolean isFormatSupported = true;
                Bitmap result = null;
                try {
                  result = getBitmapFromImageReader(reader);
                } catch (UnsupportedOperationException e) {
                  isFormatSupported = false;
                }

                deliverResult(result, isFormatSupported);
              })
          .start();
    }

    /**
     * @param reader An {@link ImageReader} of format {@link PixelFormat#RGBA_8888}
     * @return a {@link Bitmap} of format {@link android.graphics.Bitmap.Config#ARGB_8888}
     *         containing screen capture data processed from the ImageReader's frame buffer.
     */
    private Bitmap getBitmapFromImageReader(ImageReader reader) {
      Image frame = reader.acquireLatestImage();
      if (frame == null) {
        return null;
      }

      Image.Plane[] planes = frame.getPlanes();
      if ((planes == null) || (planes.length < 1)) {
        return null;
      }

      // We only capture data from the first Plane of the Image, as the MediaProjection Surface uses
      // only a single plane.
      Plane imagePlane = planes[0];

      // Create a bitmap with a format matching that expected from the ImageReader and copy the
      // capture data.
      Bitmap bitmap = Bitmap.createBitmap(imagePlane.getRowStride() / imagePlane.getPixelStride(),
          frame.getHeight(), Bitmap.Config.ARGB_8888);
      ByteBuffer buffer = planes[0].getBuffer();
      bitmap.copyPixelsFromBuffer(buffer);
      Bitmap croppedBitmap = BitmapUtils.cropBitmap(bitmap, frame.getCropRect());
      bitmap.recycle();
      frame.close();

      return croppedBitmap;
    }

    private void deliverResult(final Bitmap screenCapture, final boolean isFormatSupported) {
      virtualDisplay.release();
      virtualDisplay = null;
      imageReader.close();
      imageReader = null;
      if (listener != null) {
        handler.post(() -> listener.onScreenCaptureFinished(screenCapture, isFormatSupported));
      }
    }
  }

  /**
   * Listener callback interface for authorizing an application to obtain a screen capture data.
   *
   * @see ScreenCaptureController#authorizeCaptureAsync(AuthorizationListener)
   */
  public interface AuthorizationListener {

    /** Invoked before the application requests authorization for screen capture from the user. */
    void onAuthorizationStarted();

    /**
     * Invoked when the user completes authorization for screen capture.
     *
     * @param success {@code True} if the user authorized the application to use screen capture
     *        functionality, {@code false} otherwise.
     */
    void onAuthorizationFinished(boolean success);
  }

  /**
   * Listener callback interface for obtain screen capture data.
   *
   * @see ScreenCaptureController#requestScreenCaptureAsync(CaptureListener)
   */
  public interface CaptureListener {

    /**
     * Invoked when screen capture completes.
     *
     * @param screenCapture A {@link Bitmap} containing screen capture data, or {@code null} if
     *     screen capture data could not be obtained.
     * @param isFormatSupported {@code true} if the screen capture's format is supported {@code
     *     false} otherwise
     */
    void onScreenCaptureFinished(Bitmap screenCapture, boolean isFormatSupported);
  }
}
