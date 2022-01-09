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
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Vibrator;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityWindowInfo;

/** Methods to check hardware and software support for operating system features. */
public final class FeatureSupport {
  /** TODO : The flag to enable sending motion events of gestures. */
  public static final int FLAG_SEND_MOTION_EVENTS = 0x0004000;
  // This flag is defined in QPR but not yet opened for access directly
  public static final int FLAG_REQUEST_2_FINGER_PASSTHROUGH = 0x0002000;

  public static boolean isWatch(Context context) {
    return context
        .getApplicationContext()
        .getPackageManager()
        .hasSystemFeature(PackageManager.FEATURE_WATCH);
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

  /** Returns {@code true} if the device supports accessibility shortcut. */
  public static boolean hasAccessibilityShortcut(Context context) {
    return isPhoneOrTablet(context) && BuildVersionUtils.isAtLeastO();
  }

  public static boolean useSpeakPasswordsServicePref() {
    return BuildVersionUtils.isAtLeastO();
  }

  /** Returns {@code true} for devices which have separate audio a11y stream. */
  public static boolean hasAccessibilityAudioStream(Context context) {
    return BuildVersionUtils.isAtLeastO() && !isTv(context);
  }

  /** Return whether fingerprint feature & fingerprint gesture is supported on this device. */
  public static boolean isFingerprintSupported(Context context) {
    // PackageManager.FEATURE_FINGERPRINT is supported since M.
    if (context == null) {
      return false;
    }

    return BuildVersionUtils.isAtLeastO()
        && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        && !isWatch(context);
  }

  /** Return whether vibrator is supported on this device. */
  public static boolean isVibratorSupported(Context context) {
    final Vibrator vibrator =
        (context == null) ? null : (Vibrator) context.getSystemService(VIBRATOR_SERVICE);

    return (vibrator != null) && vibrator.hasVibrator();
  }

  public static boolean supportsVolumeKeyShortcuts() {
    return !BuildVersionUtils.isAtLeastO();
  }

  public static boolean disableAnimation() {
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

  /** Returns {@code true} if the device supports system actions. */
  public static boolean supportSystemActions(Context context) {
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
   * blocked after S.
   */
  // TODO: framework support onMagnificationChanged() for Window magnification at next
  // Android.
  public static boolean supportAnnounceMagnificationChanged() {
    return BuildVersionUtils.isAtLeastN() && Build.VERSION.SDK_INT != BuildVersionUtils.API_S;
  }

  public static boolean isBoundsScaledUpByMagnifier() {
    return BuildVersionUtils.isAtLeastOMR1();
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

  /**
   * R-QPR supports the 2-finger pass-through. To identify the system is QPR or not depends on a new
   * AccessibilityServiceInfo flag FLAG_REQUEST_2_FINGER_PASSTHROUGH. Unfortunately, 1. It needs an
   * accessibility service to access the flag which is not suitable. 2. The flag is at hide so we
   * can not determine whether the underlying platform support it. We check indirectly via open API
   * flagToString which is a static method, so no accessibility service needed. TODO: When S is
   * ready, we can have a more solid way to check the 2-finger pass-through property.
   *
   * @return {@code true} if the device supports multi-finger gesture
   */
  public static boolean isMultiFingerGestureSupported() {
    return BuildVersionUtils.isAtLeastR()
        && AccessibilityServiceInfo.flagToString(FLAG_REQUEST_2_FINGER_PASSTHROUGH) != null;
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
    return BuildVersionUtils.isAtLeastR()
        && AccessibilityServiceInfo.flagToString(FLAG_SEND_MOTION_EVENTS) != null;
  }

  /** Returns {@code true} if the device supports long version code. */
  public static boolean supportLongVersionCode() {
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
}
