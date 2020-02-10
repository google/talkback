/*
 * Copyright (C) 2018 Google Inc.
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
package com.google.android.accessibility.switchaccess;

import com.google.common.annotations.VisibleForTesting;

/** Guards features that are under development, but not yet ready for launch. */
public class FeatureFlags {

  // LINT.IfChange
  private static boolean devBuildLogging = true;
  private static boolean crashOnError = true;
  private static boolean scrollArrows = true;
  private static boolean groupSelectionWithAutoScan = false;
  private static boolean primesDebugIntegration = false;
  private static boolean clearcutLogging = true;
  private static boolean screenSwitch = false;

  /**
   * Guards verbose logging, meant only for developer builds. When enabled, the log level should be
   * verbose.
   *
   * @return {@code true} if the log level should be set to verbose
   */
  public static boolean devBuildLogging() {
    return devBuildLogging;
  }

  /**
   * Guards the feature where certain errors will cause a crash for debug purposes but not in
   * production.
   *
   * @return {@code true} if we should crash on an error
   */
  public static boolean crashOnError() {
    return crashOnError;
  }

  /**
   * Guards the Scroll Arrows feature. When enabled, arrows will appear along the top, bottom,
   * right, and left edges of highlighted scrollable views to indicate to the user that the views
   * are scrollable.
   *
   * @return {@code true} if the Scroll Arrows feature is enabled, {@code false} otherwise
   */
  public static boolean scrollArrows() {
    return scrollArrows;
  }

  /**
   * Guards the feature that allows both group selection and auto-scan to be enabled simultaneously.
   *
   * @return {@code true} if group selection and auto-scan can be enabled simultaneously
   */
  public static boolean groupSelectionWithAutoScan() {
    return groupSelectionWithAutoScan;
  }

  /**
   * Guards the feature that allows a debug version of Primes to launch.
   *
   * @return {@code true} if launching a debug version of Primes is enabled
   */
  public static boolean primesDebugIntegration() {
    return primesDebugIntegration;
  }

  /**
   * Guards clearcut logging.
   *
   * @return {@code true} if clearcut logging is enabled
   */
  public static boolean clearcutLogging() {
    return clearcutLogging;
  }

  /**
   * Guards using the screen as a switch.
   *
   * @return {@code true} if using the screen as a switch is enabled
   */
  public static boolean screenSwitch() {
    return screenSwitch;
  }

  // LINT.ThenChange(//depot/google3/java/com/google/android/accessibility/switchaccess/proguard_release.pgcfg)

  @VisibleForTesting
  public static void enableAllFeatureFlags() {
    devBuildLogging = true;
    crashOnError = true;
    scrollArrows = true;
    groupSelectionWithAutoScan = true;
    primesDebugIntegration = true;
    clearcutLogging = true;
    screenSwitch = true;
  }

  @VisibleForTesting
  public static void disableAllFeatureFlags() {
    devBuildLogging = false;
    crashOnError = false;
    scrollArrows = false;
    groupSelectionWithAutoScan = false;
    primesDebugIntegration = false;
    clearcutLogging = false;
    screenSwitch = false;
  }
}
