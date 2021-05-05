package com.google.android.accessibility.utils.accessibilitybutton;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Message;
import androidx.annotation.NonNull;
import android.view.Display;
import android.view.accessibility.AccessibilityManager;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Monitors whether accessibility button is supported on devices and notifies accessibility button
 * click event on the display.
 *
 * <p>{@link AccessibilityButtonController} provides API to listen to availability of the
 * accessibility button. The availability changes during runtime when the device goes into/out of
 * fullscreen mode. SelectToSpeak service needs an API to check whether the accessibility button is
 * supported on device, regardless of the "fullscreen mode" scenario. This class is a work around
 * for the problem, it wraps {@link AccessibilityButtonController.AccessibilityButtonCallback} and
 * exposes another callback to notify button click actions and the detect the supportability of a11y
 * button.
 *
 * <p>If the build supports a11y multi-display, {@link AccessibilityButtonController} should handle
 * the a11y button callback registration and callback unregistration for multi-display.
 */
public class AccessibilityButtonMonitor {

  private static final String TAG = "A11yMenuButtonMonitor";

  /** Callbacks for click action and confirmation of supportability for the a11y button. */
  public interface AccessibilityButtonMonitorCallback {

    /** Called when the a11y button is clicked. */
    void onAccessibilityButtonClicked();

    /**
     * Called when we can confirm the a11y button is supported or not supported on device.
     * <strong>Note:</strong> This callback method will only be called once.
     */
    void onConfirmSupportability(boolean isSupported);
  }

  // The state when we cannot confirm whether the button is supported or not.
  public static final int PENDING = 0;
  // The state when we can confirm that the button is not supported on device.
  public static final int NOT_SUPPORTED = 1;
  // The state when we can confirm that the button is supported on device.
  public static final int SUPPORTED = 2;

  /** Defines whether a11y button is supported on the device. */
  @Retention(RetentionPolicy.SOURCE)
  public @interface ButtonSupportability {}

  // Time out to post delayed confirmation of a11y button supportability.
  private static final long TIMEOUT = 1000;

  private final AccessibilityService mService;
  private final AccessibilityButtonCallBackHandler mHandler;

  // Callback used to notify AccessibilityService of button availability and click action.
  private AccessibilityButtonMonitorCallback mCallback;

  // Callback to be registered in AccessibilityButtonController.
  private AccessibilityButtonController.AccessibilityButtonCallback accessibilityButtonCallback;
  private final DisplayManager displayManager;
  // Listener that monitors the display change to support a11y button in multi-display.
  // AccessibilityButtonMonitor has to register or unregister the a11y button controller callback
  // for each display when the specified display is just added or removed.
  private final DisplayManager.DisplayListener displayListener =
      new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
          if (FeatureSupport.supportAccessibilityMultiDisplay()
              && accessibilityButtonCallback != null) {
            mService
                .getAccessibilityButtonController(displayId)
                .registerAccessibilityButtonCallback(accessibilityButtonCallback);
          }
        }

        @Override
        public void onDisplayChanged(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {
          if (FeatureSupport.supportAccessibilityMultiDisplay()
              && accessibilityButtonCallback != null) {
            mService
                .getAccessibilityButtonController(displayId)
                .unregisterAccessibilityButtonCallback(accessibilityButtonCallback);
          }
        }
      };

  @ButtonSupportability private int mButtonState = PENDING;

  public AccessibilityButtonMonitor(@NonNull AccessibilityService service) {
    mHandler = new AccessibilityButtonCallBackHandler(this);
    mService = service;
    displayManager = (DisplayManager) mService.getSystemService(Context.DISPLAY_SERVICE);
  }

  @TargetApi(Build.VERSION_CODES.O)
  public void initAccessibilityButton(@NonNull AccessibilityButtonMonitorCallback callback) {
    mCallback = callback;
    if (!FeatureSupport.supportAccessibilityButton()) {
      LogUtils.d(TAG, "Accessibility button is not supported for pre-O devices.");
      // A11y button is not supported on pre-O devices.
      mHandler.confirmAccessibilityButtonSupportability(false);
      return;
    }

    // Ensure the flag is added to AccessibilityServiceInfo.
    AccessibilityServiceInfo info = mService.getServiceInfo();
    if (info != null) {
      info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON;
      mService.setServiceInfo(info);
    }

    @NonNull
    AccessibilityButtonController accessibilityButtonController =
        mService.getAccessibilityButtonController();
    if (AccessibilityServiceCompatUtils.isAccessibilityButtonAvailableCompat(
        accessibilityButtonController)) {
      LogUtils.d(TAG, "Accessibility button is available on initialization.");
      // If a11y button is available at the very beginning when the monitor is initialized, we can
      // confirm that the a11y button is supported on the device.
      mHandler.confirmAccessibilityButtonSupportability(true);
    } else {
      LogUtils.d(TAG, "Accessibility button is not available on initialization.");
      // If a11y button is not available when monitor is initialized, there could be two reasons:
      // 1. The device has physical nav bar button and the virtual nav bar is not supported on the
      // device, which is permanent unavailability.
      // 2. Race condition during framework initialization, it returns false when we call
      // AccessibilityButtonController.isAccessibilityButtonAvailable(), but soon the
      // AccessibilityButtonCallback.onAvailabilityChanged will be called to update availability.
      //
      // In both cases, it's acceptable to post delay to notify unavailability. If we get notified
      // that the availability changes before time out, we can cancel this delayed message and
      // update the availability with another message.
      mHandler.postDelayedConfirmAccessibilityButtonSupportability(TIMEOUT);
    }

    accessibilityButtonCallback =
        new AccessibilityButtonController.AccessibilityButtonCallback() {
          @Override
          public void onClicked(AccessibilityButtonController controller) {
            LogUtils.d(TAG, "Accessibility button clicked.");
            handleControllerCallbackButtonClicked();
          }

          @Override
          public void onAvailabilityChanged(
              AccessibilityButtonController controller, boolean available) {
            LogUtils.d(TAG, "Accessibility button availability changed. isAvailable=%s", available);
            handleControllerCallbackAvailabilityChanged(available);
          }
        };

    // Register callback to AccessibilityButtonController.
    if (FeatureSupport.supportAccessibilityMultiDisplay()) {
      displayManager.registerDisplayListener(displayListener, null);
      for (Display display : displayManager.getDisplays()) {
        mService
            .getAccessibilityButtonController(display.getDisplayId())
            .registerAccessibilityButtonCallback(accessibilityButtonCallback);
      }
    } else {
      accessibilityButtonController.registerAccessibilityButtonCallback(
          accessibilityButtonCallback);
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  public void shutdown() {
    if (!FeatureSupport.supportAccessibilityButton()) {
      return;
    }
    // Unregister callback from AccessibilityButtonController.
    if (FeatureSupport.supportAccessibilityMultiDisplay()) {
      displayManager.unregisterDisplayListener(displayListener);

      for (Display display : displayManager.getDisplays()) {
        mService
            .getAccessibilityButtonController(display.getDisplayId())
            .unregisterAccessibilityButtonCallback(accessibilityButtonCallback);
      }
    } else {
      mService
          .getAccessibilityButtonController()
          .unregisterAccessibilityButtonCallback(accessibilityButtonCallback);
    }
  }

  /**
   * Returns {@code true} if accessibility button is detected and supported on the device.
   * <strong>Note:</strong> When it returns {@code false}, it could either because the device
   * doesn't support a11y nav bar button, or the a11y button is supported but not detected yet.
   */
  public boolean isAccessibilityButtonSupported() {
    return mButtonState == SUPPORTED;
  }

  /** Handles the callback AccessibilityButtonCallback.onClicked() */
  private void handleControllerCallbackButtonClicked() {
    // Override button state, and notify callback if necessary.
    if (mButtonState == PENDING) {
      mHandler.confirmAccessibilityButtonSupportability(true);
    } else if (mButtonState == NOT_SUPPORTED) {
      // If the previous state detection is a false negative, override the state without notifying
      // availability change.
      LogUtils.w(
          TAG,
          "A11y button is clicked after it's reported as NOT_SUPPORTED. "
              + "Update state from NOT_SUPPORTED to SUPPORTED.");
      mButtonState = SUPPORTED;
    }

    mHandler.notifyButtonClicked();
  }

  /** Handles the callback AccessibilityButtonCallback.onAvailabilityChanged(). */
  private void handleControllerCallbackAvailabilityChanged(boolean available) {
    switch (mButtonState) {
      case NOT_SUPPORTED:
        if (available) {
          // The previous detection indicates that the a11y button is not supported on device, but
          // the callback shows that the button is actually supported. we should update the state
          // quietly without duplicate notifying the confirmation of button availability.
          LogUtils.w(
              TAG,
              "A11y button availability is changed after it's reported as NOT_SUPPORTED. "
                  + "Update state from NOT_SUPPORTED to SUPPORTED.");
          mButtonState = SUPPORTED;
        }
        break;
      case PENDING:
        if (available) {
          // Available is a strong signal, we can confirm the availability immediately.
          mHandler.confirmAccessibilityButtonSupportability(true);
        } else {
          // Unavailable is a weak signal, we should post delay to confirm the unavailability in
          // case that something will be changed during the delay timeout.
          mHandler.postDelayedConfirmAccessibilityButtonSupportability(TIMEOUT);
        }
        break;
      case SUPPORTED:
      default:
        // Do nothing.
        break;
    }
  }

  /**
   * A {@link WeakReferenceHandler} to handle the callback for button click actions and button
   * support confirmation.
   */
  private static final class AccessibilityButtonCallBackHandler
      extends WeakReferenceHandler<AccessibilityButtonMonitor> {
    private static final int MSG_BUTTON_CLICKED = 0;
    private static final int MSG_CONFIRM_BUTTON_NOT_SUPPORTED = 1;
    private static final int MSG_CONFIRM_BUTTON_SUPPORTED = 2;
    private static final int MSG_CONFIRM_BUTTON_SUPPORTABILITY_DELAYED = 3;

    // Whether we have already notified the confirmation of button support.
    private boolean mHasNotifiedSupportability = false;

    public AccessibilityButtonCallBackHandler(AccessibilityButtonMonitor parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, AccessibilityButtonMonitor parent) {
      if (parent == null) {
        return;
      }
      switch (msg.what) {
        case MSG_BUTTON_CLICKED:
          parent.mCallback.onAccessibilityButtonClicked();
          break;
        case MSG_CONFIRM_BUTTON_NOT_SUPPORTED:
          parent.mButtonState = NOT_SUPPORTED;
          // Make sure that we only notify once.
          if (!mHasNotifiedSupportability) {
            LogUtils.d(TAG, "Notify that a11y button is not supported.");
            mHasNotifiedSupportability = true;
            parent.mCallback.onConfirmSupportability(false);
          }
          break;
        case MSG_CONFIRM_BUTTON_SUPPORTED:
          parent.mButtonState = SUPPORTED;
          // Make sure that we only notify once.
          if (!mHasNotifiedSupportability) {
            LogUtils.d(TAG, "Notify that a11y button is supported.");
            parent.mCallback.onConfirmSupportability(true);
            mHasNotifiedSupportability = true;
          }
          break;
        case MSG_CONFIRM_BUTTON_SUPPORTABILITY_DELAYED:
          boolean isAvailable;
          if (BuildVersionUtils.isAtLeastOMR1()) {
            isAvailable = AccessibilityManager.isAccessibilityButtonSupported();
          } else {
            isAvailable =
                AccessibilityServiceCompatUtils.isAccessibilityButtonAvailableCompat(
                    parent.mService.getAccessibilityButtonController());
          }
          parent.mButtonState = isAvailable ? SUPPORTED : NOT_SUPPORTED;
          if (!mHasNotifiedSupportability) {
            LogUtils.d(
                TAG,
                "Delayed. Notify that a11y button is %s.",
                (isAvailable ? "supported" : "not supported"));
            parent.mCallback.onConfirmSupportability(isAvailable);
            mHasNotifiedSupportability = true;
          }
          break;
        default:
          break;
      }
    }

    private void postDelayedConfirmAccessibilityButtonSupportability(long delay) {
      LogUtils.d(TAG, "Post delay to confirm supportability.");
      removeMessages(MSG_CONFIRM_BUTTON_SUPPORTED);
      removeMessages(MSG_CONFIRM_BUTTON_NOT_SUPPORTED);
      removeMessages(MSG_CONFIRM_BUTTON_SUPPORTABILITY_DELAYED);
      sendEmptyMessageDelayed(MSG_CONFIRM_BUTTON_SUPPORTABILITY_DELAYED, delay);
    }

    private void confirmAccessibilityButtonSupportability(boolean isSupported) {
      removeMessages(MSG_CONFIRM_BUTTON_SUPPORTED);
      removeMessages(MSG_CONFIRM_BUTTON_NOT_SUPPORTED);
      removeMessages(MSG_CONFIRM_BUTTON_SUPPORTABILITY_DELAYED);
      obtainMessage(isSupported ? MSG_CONFIRM_BUTTON_SUPPORTED : MSG_CONFIRM_BUTTON_NOT_SUPPORTED)
          .sendToTarget();
    }

    private void notifyButtonClicked() {
      obtainMessage(MSG_BUTTON_CLICKED).sendToTarget();
    }
  }
}
