/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.talkback.monitor;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants;
import com.google.android.accessibility.utils.FormFactorUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Monitor for the state of the input method (onscreen keyboard). */
public class InputMethodMonitor {
  private final AccessibilityService service;
  private final FormFactorUtils formFactorUtils;
  private final Handler handler = new Handler(Looper.myLooper());
  private volatile String activeInputMethod;

  public InputMethodMonitor(@NonNull AccessibilityService service) {
    this.service = service;
    formFactorUtils = FormFactorUtils.getInstance();
  }

  /** Hook for when the {@link TalkBackService} is resumed. */
  public void onResumeInfrastructure() {
    service
        .getContentResolver()
        .registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
            false,
            contentObserver);
    updateActiveInputMethod();
  }

  /** Hook for when the {@link TalkBackService} is suspended. */
  public void onSuspendInfrastructure() {
    service.getContentResolver().unregisterContentObserver(contentObserver);
  }

  /**
   * Returns the currently active (open) input method window, or {@code null} if no input method is
   * currently active.
   */
  public @Nullable AccessibilityWindowInfo getActiveInputWindow() {
    return AccessibilityServiceCompatUtils.getOnscreenInputWindowInfo(service);
  }

  /**
   * Returns the root node for the currently active (open) input method window, or {@code null} if
   * no input method is currently active.
   */
  public @Nullable AccessibilityNodeInfo getRootInActiveInputWindow() {
    AccessibilityWindowInfo accessibilityWindowInfo =
        AccessibilityServiceCompatUtils.getOnscreenInputWindowInfo(service);
    if (accessibilityWindowInfo == null) {
      return null;
    }
    return accessibilityWindowInfo.getRoot();
  }

  /**
   * Returns the node with input focus in the currently active (open) input method window, or {@code
   * null} if no such node exists.
   */
  public @Nullable AccessibilityNodeInfoCompat findFocusedNodeInActiveInputWindow() {
    AccessibilityNodeInfo root = getRootInActiveInputWindow();
    if (root == null) {
      return null;
    }
    return AccessibilityNodeInfoUtils.toCompat(root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT));
  }

  /**
   * Returns whether the current input method should be treated by TalkBack as the active window
   * whenever it is on screen, even if it the {@link AccessibilityWindowInfo} disagrees.
   */
  public boolean useInputWindowAsActiveWindow() {
    if (!formFactorUtils.isAndroidTv()
        || activeInputMethod == null
        || !activeInputMethod.startsWith(Constants.GBOARD_PACKAGE_NAME)) {
      return false;
    }

    String packageName =
        activeInputMethod.startsWith(Constants.GBOARD_PACKAGE_NAME_DEV)
            ? Constants.GBOARD_PACKAGE_NAME_DEV
            : Constants.GBOARD_PACKAGE_NAME;
    int installedVersion = findInstalledVersionOfPackage(packageName);
    return installedVersion >= Constants.GBOARD_MIN_SUPPORTED_VERSION;
  }

  private final ContentObserver contentObserver =
      new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
          updateActiveInputMethod();
        }
      };

  private void updateActiveInputMethod() {
    activeInputMethod =
        Settings.Secure.getString(
            service.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
  }

  /** Returns the version code of the given package, assuming that it is installed. */
  private int findInstalledVersionOfPackage(@NonNull String packageName) {
    try {
      return service.getPackageManager().getPackageInfo(packageName, /* flags= */ 0).versionCode;
    } catch (NameNotFoundException e) {
      throw new AssertionError("Package not found despite being the default input method.", e);
    }
  }

  @VisibleForTesting
  void setActiveInputMethod(@NonNull String name) {
    activeInputMethod = name;
  }
}
