/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.utils;

import static android.content.Context.SENSOR_SERVICE;
import static android.content.Context.VIBRATOR_SERVICE;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Vibrator;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Methods to check hardware and software support for operating system features. */
public final class FeatureSupport {
  @Nullable private static Boolean brailleDisplaySettingsActivityPresent = null;
  @Nullable private static Boolean brailleKeyboardSettingsActivityPresent = null;
  @Nullable private static Boolean isWatch = null;

  // Enforce noninstantiability with a private constructor.
  private FeatureSupport() {}

  /**
   * Returns {@code true} if the device is a watch.
   *
   * <p>The flag will be cached for further query.
   */
  public static boolean isWatch(Context context) {
    if (isWatch == null) {
      isWatch =
          context
              .getApplicationContext()
              .getPackageManager()
              .hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
    return isWatch;
  }

  public static boolean isArc() {
    return (Build.DEVICE != null) && Build.DEVICE.matches(".+_cheets|cheets_.+");
  }

  public static boolean isTv(Context context) {
    if (context == null) {
      return false;
    }

    UiModeManager modeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    return ((modeManager != null)
        && (modeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION));
  }

  public static boolean isPhoneOrTablet(Context context) {
    return (!isWatch(context) && !isArc() && !isTv(context));
  }

  public static boolean useSpeakPasswordsServicePref() {
    return BuildVersionUtils.isAtLeastO();
  }

  /** Returns {@code true} for devices which have separate audio a11y stream. */
  public static boolean hasAccessibilityAudioStream(Context context) {
    return BuildVersionUtils.isAtLeastO() && !isTv(context);
  }

  /** Return whether fingerprint gesture is supported on this device. */
  public static boolean isFingerprintGestureSupported(Context context) {
    // Fingerprint gesture is supported since O.
    boolean supportFingerprint = isFingerprintSupported(context);
    if (context == null || !BuildVersionUtils.isAtLeastO() || !supportFingerprint) {
      return false;
    }
    int fingerprintSupportsGesturesResID =
        context
            .getResources()
            .getIdentifier("config_fingerprintSupportsGestures", "bool", "android");
    return fingerprintSupportsGesturesResID != 0
        && context.getResources().getBoolean(fingerprintSupportsGesturesResID);
  }

  /** Return whether fingerprint feature is supported on this device. */
  private static boolean isFingerprintSupported(Context context) {
    if (context == null || isWatch(context)) {
      return false;
    }
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
  }

  /** Return whether vibrator is supported on this device. */
  public static boolean isVibratorSupported(Context context) {
    final Vibrator vibrator =
        (context == null) ? null : (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
    return (vibrator != null) && vibrator.hasVibrator();
  }

  /** Return whether enable/disable IME is supported on this device. */
  public static boolean supportEnableDisableIme() {
    return BuildVersionUtils.isAtLeastT();
  }

  public static boolean supportsUserDisablingOfGlobalAnimations() {
    return BuildVersionUtils.isAtLeastP();
  }

  public static boolean supportReadClipboard() {
    return !BuildVersionUtils.isAtLeastQ();
  }

  public static boolean screenshotRequiresForeground() {
    return Build.VERSION.SDK_INT == VERSION_CODES.Q;
  }

  /**
   * Returns {@code true} if the device supports takeScreenshot by AccessibilityService native API.
   */
  public static boolean canTakeScreenShotByAccessibilityService() {
    return BuildVersionUtils.isAtLeastR();
  }

  public static boolean supportNotificationChannel() {
    return BuildVersionUtils.isAtLeastO();
  }

  public static boolean isHeadingWorks() {
    return BuildVersionUtils.isAtLeastN();
  }

  /** Returns {@code true} if the device supports {@link AccessibilityWindowInfo#getTitle()}. */
  public static boolean supportGetTitleFromWindows() {
    return BuildVersionUtils.isAtLeastN();
  }

  public static boolean supportSwitchToInputMethod() {
    return BuildVersionUtils.isAtLeastR();
  }

  public static boolean supportContentDescriptionInReplacementSpan() {
    return BuildVersionUtils.isAtLeastR();
  }

  /** Returns {@code true} if the device supports to get system actions. */
  public static boolean supportGetSystemActions(Context context) {
    return BuildVersionUtils.isAtLeastR() && !isWatch(context);
  }

  public static boolean supportMediaControls() {
    return BuildVersionUtils.isAtLeastR();
  }

  /** Returns {@code true} if the device supports brightness float. */
  public static boolean supportBrightnessFloat() {
    return BuildVersionUtils.isAtLeastR();
  }

  /**
   * Returns {@code true} if the device supports {@link
   * AccessibilityService#MagnificationController}.
   */
  public static boolean supportMagnificationController() {
    return BuildVersionUtils.isAtLeastN();
  }

  /**
   * Returns {@code true} if the device should announce magnification state when
   * onMagnificationChanged() is called. In S, window magnification is available but the
   * onMagnificationChanged listener doesn't support this yet. To prevent user confusing, this is
   * blocked in S.
   */
  public static boolean supportAnnounceMagnificationChanged() {
    return Build.VERSION.SDK_INT != VERSION_CODES.S && Build.VERSION.SDK_INT != VERSION_CODES.S_V2;
  }

  /**
   * Returns {@code true} if AccessibilityService and the device supports window magnification
   * feature. AccessibilityService can control window magnification by new API since T.
   */
  public static boolean supportWindowMagnification(Context context) {
    return BuildVersionUtils.isAtLeastT()
        && context
            .getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_WINDOW_MAGNIFICATION);
  }

  public static boolean isBoundsScaledUpByMagnifier() {
    return BuildVersionUtils.isAtLeastOMR1();
  }

  /**
   * Returns {@code true} if TalkBack handles the window state change event that requires pane
   * title.
   */
  public static boolean windowStateChangeRequiresPane() {
    return BuildVersionUtils.isAtLeastT();
  }

  public static boolean supportMultiDisplay() {
    return BuildVersionUtils.isAtLeastR();
  }

  /**
   * Returns {@code true} if the device requires the phone permission granted to access the call
   * state. {@link Manifest.permission#READ_PHONE_STATE}
   */
  public static boolean callStateRequiresPermission() {
    return BuildVersionUtils.isAtLeastS();
  }

  /**
   * Returns {@code true} if the device requires the permission to post notifications. {@link
   * Manifest.permission#POST_NOTIFICATIONS}
   */
  public static boolean postNotificationsPermission() {
    return BuildVersionUtils.isAtLeastT();
  }

  /**
   * Returns {@code true} if all the insets will be reported to the window regarding the z-order.
   * {@link android.view.WindowManager.LayoutParams#receiveInsetsIgnoringZOrder}
   */
  public static boolean supportReportingInsetsByZOrder() {
    return BuildVersionUtils.isAtLeastS();
  }

  /** Returns {@code true} if the device supports customizing focus indicator. */
  public static boolean supportCustomizingFocusIndicator() {
    return BuildVersionUtils.isAtLeastS();
  }

  /**
   * Provides a function to check if support the dark theme. This feature is supported from Q (API
   * 29).
   *
   * @return {@code true} if the device supports dark theme.
   */
  public static boolean supportDarkTheme() {
    return BuildVersionUtils.isAtLeastQ();
  }

  /**
   * Returns {@code true} if the device supports closing shades when starting an activity. See
   * {@link Intent#ACTION_CLOSE_SYSTEM_DIALOGS}.
   */
  public static boolean startActivityClosesShades() {
    return BuildVersionUtils.isAtLeastS();
  }

  public static boolean supportPassthrough() {
    return BuildVersionUtils.isAtLeastR();
  }

  public static boolean supportSettingsTheme() {
    return BuildVersionUtils.isAtLeastS();
  }

  public static boolean supportBoldFont() {
    return BuildVersionUtils.isAtLeastS();
  }

  public static boolean supportSpeechState() {
    return BuildVersionUtils.isAtLeastT();
  }

  /**
   * Provides a Talkback menu item to manually enter or change a percentage value for seek controls.
   * This functionality is only available on Android N and later. REFERTO.
   *
   * @return {@code true} if the device supports change slider
   */
  public static boolean supportChangeSlider() {
    return BuildVersionUtils.isAtLeastN();
  }

  /**
   * Returns {@code true} if the device supports {@link
   * AccessibilityManager#getRecommendedTimeoutMillis(int, int)}}.
   */
  public static boolean supportRecommendedTimeout() {
    return BuildVersionUtils.isAtLeastQ();
  }

  /** Returns true if the runtime supports full multi-finger gesture support. */
  @SuppressLint("NewApi")
  public static boolean isMultiFingerGestureSupported() {
    return BuildVersionUtils.isAtLeastR()
        && AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH)
            != null;
  }

  /** Returns true if the platform supports FLAG_SERVICE_HANDLES_DOUBLE_TAP. */
  public static boolean doesServiceHandleDoubleTap() {
    return BuildVersionUtils.isAtLeastR();
  }

  /**
   * From Android S and forward, platform extends the multi-finger gestures with
   * GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD(43) GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD(44)
   * GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD(45)
   *
   * @return {@code true} if the device supports multi-finger gesture
   */
  public static boolean multiFingerTapAndHold() {
    return BuildVersionUtils.isAtLeastS();
  }

  /** Returns {@code true} if the device supports customizing bullet radius. */
  public static boolean customBulletRadius() {
    return BuildVersionUtils.isAtLeastP();
  }

  /** Returns {@code true} if the device supports sending motion events of gestures. */
  public static boolean supportGestureMotionEvents() {
    return BuildVersionUtils.isAtLeastS()
        && AccessibilityServiceInfo.flagToString(AccessibilityServiceInfo.FLAG_SEND_MOTION_EVENTS)
            != null;
  }

  /** Returns {@code true} if the device supports long version code. */
  public static boolean supportLongVersionCode() {
    return BuildVersionUtils.isAtLeastP();
  }

  /** Returns whether the android-version supports AccessibilityEvent.getScrollDeltaX/Y() */
  public static boolean scrollDelta() {
    return BuildVersionUtils.isAtLeastP();
  }

  /** Returns whether the android-version supports AccessibilityEvent.getWindowChanges() */
  public static boolean windowChanges() {
    return BuildVersionUtils.isAtLeastP();
  }

  /**
   * Supports accessibility button from Android O. *
   *
   * <p><strong>Note:</strong> Caller should use {@link AccessibilityButtonMonitor} to know whether
   * the button is available right now.
   *
   * @return {@code true} if the device supports accessibility button
   */
  public static boolean supportAccessibilityButton() {
    return BuildVersionUtils.isAtLeastO();
  }

  /** Returns {@code true} if the device supports accessibility multi-display. */
  public static boolean supportAccessibilityMultiDisplay() {
    return BuildVersionUtils.isAtLeastR();
  }

  /** Returns {@code true} if the device has proximity sensor built-in. */
  public static boolean supportProximitySensor(Context context) {
    return ((SensorManager) context.getSystemService(SENSOR_SERVICE))
            .getDefaultSensor(Sensor.TYPE_PROXIMITY)
        != null;
  }

  /** Returns {@code true} if the device has braille keyboard supported. */
  public static boolean supportBrailleKeyboard(Context context) {
    if (brailleKeyboardSettingsActivityPresent == null) {
      Intent activityIntent = new Intent().setComponent(Constants.BRAILLE_KEYBOARD_SETTINGS);
      brailleKeyboardSettingsActivityPresent =
          activityIntent.resolveActivityInfo(context.getPackageManager(), 0) != null;
    }
    return brailleKeyboardSettingsActivityPresent;
  }

  /** Returns {@code true} if the device has braille display supported. */
  public static boolean supportBrailleDisplay(Context context) {
    if (brailleDisplaySettingsActivityPresent == null) {
      Intent activityIntent = new Intent().setComponent(Constants.BRAILLE_DISPLAY_SETTINGS);
      brailleDisplaySettingsActivityPresent =
          activityIntent.resolveActivityInfo(context.getPackageManager(), 0) != null;
    }
    return brailleDisplaySettingsActivityPresent;
  }

  /**
   * Returns {@code true} if the order of receiving touch interaction event and hover event is NOT
   * guaranteed.
   *
   * <p>Related event type:
   *
   * <ul>
   *   <li>TYPE_TOUCH_INTERACTION_END,
   *   <li>TYPE_VIEW_HOVER_ENTER
   * </ul>
   */
  public static boolean hoverEventOutOfOrder() {
    return !BuildVersionUtils.isAtLeastR();
  }

  /**
   * Returns true if potentially sensitive information (such as tts text) is allowed to appear in
   * logcat.
   */
  public static boolean logcatIncludePsi() {
    return BuildConfig.DEBUG || (LogUtils.getLogLevel() < Log.ERROR);
  }

  /** Returns {@code true} if the device is running at least API 32 */
  public static boolean supportDragAndDrop() {
    return BuildVersionUtils.isAtLeastS2();
  }

  /** Returns {@code true} if the device supports animation off by Accessibility service. */
  public static boolean supportsServiceControlOfGlobalAnimations() {
    // TODO Disable this feature until there's reliable mechanism to revert the
    // animation scale for TalkBack on/off cycle.
    return false; // BuildVersionUtils.isAtLeastT();
  }

  /** Returns {@code true} if the device supports AccessibilityNodeInfo#isTextSelectable */
  public static boolean supportsIsTextSelectable() {
    return BuildVersionUtils.isAtLeastT();
  }

  /** Returns {@code true} if the device is the Automotive */
  public static boolean isAuto(Context context) {
    return context
        .getApplicationContext()
        .getPackageManager()
        .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
  }

  /** Returns {@code true} if the device supports gesture detection in the service side. */
  public static boolean supportGestureDetection() {
    return BuildVersionUtils.isAtLeastT();
  }

  /**
   * Returns {@code true} if the device supports the row/column title from {@link
   * AccessibilityNodeInfo.CollectionItemInfo}.
   */
  public static boolean supportGridTitle() {
    return BuildVersionUtils.isAtLeastT();
  }

  /**
   * Returns {@code true} if the device supports the App locale info from {@link
   * AccessibilityWindowInfo#getLocales()}.
   */
  @ChecksSdkIntAtLeast(api = 34)
  public static boolean supportAccessibilityAppLocale() {
    return BuildVersionUtils.isAtLeastU();
  }

  @ChecksSdkIntAtLeast(api = 34)
  public static boolean supportTakeScreenshotByWindow() {
    return BuildVersionUtils.isAtLeastU();
  }

  /**
   * Returns {@code true} if the device supports a requested initial focus from {@link
   * AccessibilityNodeInfo#hasRequestInitialAccessibilityFocus()}.
   *
   * <p><b>Note:</b> the limitation is only applies to the native AccessibilityNodeInfo class.
   * Calling via AndroidX is not affected.
   */
  @ChecksSdkIntAtLeast(api = 34)
  public static boolean supportRequestInitialAccessibilityFocusNative() {
    return BuildVersionUtils.isAtLeastU();
  }

  /**
   * Returns {@code true} if the device supports multiple gesture set. The new gesture to switch
   * between gesture set is available only for gesture detection on the service side.
   */
  public static boolean supportMultipleGestureSet() {
    return BuildVersionUtils.isAtLeastT();
  }

  /**
   * Returns {@code true} if the device supports AccessibilityService#getInputMethod which is added
   * since Android T.
   */
  public static boolean supportInputConnectionByA11yService() {
    return BuildVersionUtils.isAtLeastT();
  }
}
