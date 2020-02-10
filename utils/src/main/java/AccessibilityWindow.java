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

package com.google.android.accessibility.utils;

import android.annotation.TargetApi;
import android.os.Build;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;

/**
 * A wrapper around AccessibilityWindowInfo/Compat, to help with:
 *
 * <ul>
 *   <li>handling null windows
 *   <li>recycling
 *   <li>using compat vs bare methods
 *   <li>using correct methods for various android versions
 * </ul>
 *
 * <p>Currently, AccessibilityWindowInfo is not always recycled from
 * AccessibilityNodeInfo.getWindow(), and never recycled from AccessibilityService.getWindows()
 */
public class AccessibilityWindow {

  private static final String TAG = "AccessibilityWindow";

  ///////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Window types, including both bare and compat values. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TYPE_ACCESSIBILITY_OVERLAY,
    TYPE_APPLICATION,
    TYPE_INPUT_METHOD,
    TYPE_SPLIT_SCREEN_DIVIDER,
    TYPE_SYSTEM,
    TYPE_UNKNOWN
  })
  public @interface WindowType {}

  public static final int TYPE_ACCESSIBILITY_OVERLAY =
      AccessibilityWindowInfoCompat.TYPE_ACCESSIBILITY_OVERLAY;
  public static final int TYPE_APPLICATION = AccessibilityWindowInfoCompat.TYPE_APPLICATION;
  public static final int TYPE_INPUT_METHOD = AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD;
  public static final int TYPE_SPLIT_SCREEN_DIVIDER =
      AccessibilityWindowInfoCompat.TYPE_SPLIT_SCREEN_DIVIDER;
  public static final int TYPE_SYSTEM = AccessibilityWindowInfoCompat.TYPE_SYSTEM;
  public static final int TYPE_UNKNOWN = -1;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Member data

  /**
   * The wrapped window info. Both bare and compat objects are currently required, because
   * AccessibilityWindowInfoCompat has no un/wrap() methods. Do not expose this object.
   */
  private AccessibilityWindowInfo windowBare;

  private AccessibilityWindowInfoCompat windowCompat;

  /** Name of calling method that recycled this window. */
  private String recycledBy;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Construction

  /**
   * Takes ownership of window*Arg. Does not allow all-null arguments, because call chaining is
   * already impossible, because intermediate objects have to be recycled. Caller must recycle
   * returned AccessibilityWindow.
   */
  @Nullable
  public static AccessibilityWindow takeOwnership(
      @Nullable AccessibilityWindowInfo windowBareArg,
      @Nullable AccessibilityWindowInfoCompat windowCompatArg) {
    return construct(windowBareArg, windowCompatArg, FACTORY);
  }

  /**
   * Returns a node instance, or null. Should only be called by this class and sub-classes. Uses
   * factory argument to create sub-class instances, without creating unnecessary instances when
   * result should be null. Method is protected so that it can be called by sub-classes without
   * duplicating null-checking logic.
   *
   * @param windowBareArg The wrapped window info. Caller may retain responsibility to recycle.
   * @param windowCompatArg The wrapped window info. Caller may retain responsibility to recycle.
   * @param factory Creates instances of AccessibilityWindow or sub-classes.
   * @return AccessibilityWindow instance, that caller must recycle.
   */
  @Nullable
  protected static <T extends AccessibilityWindow> T construct(
      @Nullable AccessibilityWindowInfo windowBareArg,
      @Nullable AccessibilityWindowInfoCompat windowCompatArg,
      Factory<T> factory) {
    // Check inputs.
    if (windowBareArg == null && windowCompatArg == null) {
      return null;
    }

    // Construct window wrapper.
    T instance = factory.create();
    AccessibilityWindow windowBase = instance;
    windowBase.windowBare = windowBareArg;
    windowBase.windowCompat = windowCompatArg;
    return instance;
  }

  protected AccessibilityWindow() {}

  /** A factory that can create instances of AccessibilityWindow or sub-classes. */
  protected interface Factory<T extends AccessibilityWindow> {
    T create();
  }

  private static final Factory<AccessibilityWindow> FACTORY =
      new Factory<AccessibilityWindow>() {
        @Override
        public AccessibilityWindow create() {
          return new AccessibilityWindow();
        }
      };

  ///////////////////////////////////////////////////////////////////////////////////////
  // Recycling

  public final synchronized boolean isRecycled() {
    return (recycledBy != null);
  }

  /** Recycles non-null windows. */
  public static void recycle(String caller, @Nullable AccessibilityWindow... windows) {
    if (windows == null) {
      return;
    }

    for (AccessibilityWindow window : windows) {
      if (window != null) {
        window.recycle(caller);
      }
    }
  }

  /** Recycles non-null windows and empties collection. */
  public static void recycle(String caller, @Nullable Collection<AccessibilityWindow> windows) {
    if (windows == null) {
      return;
    }

    for (AccessibilityWindow window : windows) {
      if (window != null) {
        window.recycle(caller);
      }
    }

    windows.clear();
  }

  /**
   * Recycles window, or errors if already recycled. Cannot run at the same time as isRecycled(),
   * and caller should not try to run recycle() at the same time as any other member function.
   */
  public final synchronized void recycle(String caller) {

    // Check for double-recycling.
    if (recycledBy == null) {
      recycledBy = caller;
    } else {
      logOrThrow("AccessibilityWindow is already recycled by %s then by %s", recycledBy, caller);
    }

    // Recycle window infos.
    if (windowCompat != null) {
      recycle(windowCompat, caller);
    }
    if (windowBare != null) {
      recycle(windowBare, caller);
    }
  }

  private final void recycle(AccessibilityWindowInfo window, String caller) {
    try {
      window.recycle();
    } catch (IllegalStateException e) {
      logOrThrow(
          e,
          "Caught IllegalStateException from accessibility framework with %s trying to recycle"
              + " window %s",
          caller,
          window);
    }
  }

  private final void recycle(AccessibilityWindowInfoCompat window, String caller) {
    try {
      window.recycle();
    } catch (IllegalStateException e) {
      logOrThrow(
          e,
          "Caught IllegalStateException from accessibility framework with %s trying to recycle"
              + " window %s",
          caller,
          window);
    }
  }

  /** Overridable for testing. */
  protected boolean isDebug() {
    return BuildConfig.DEBUG;
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // AccessibilityWindowInfo/Compat pass-through methods. Prefers compat methods. Also see
  // https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo

  private AccessibilityWindowInfo getBare() {
    if (isRecycled()) {
      throwError("getBare() called on window already recycled by %s", recycledBy);
    }
    return windowBare;
  }

  private AccessibilityWindowInfoCompat getCompat() {
    if (isRecycled()) {
      throwError("getCompat() called on window already recycled by %s", recycledBy);
    }
    return windowCompat;
  }

  public final boolean isActive() {
    // TODO: If window already recycled, throw name of recycler.
    AccessibilityWindowInfoCompat compat = getCompat();
    return (compat == null) ? getBare().isActive() : compat.isActive();
  }

  public final boolean isFocused() {
    AccessibilityWindowInfoCompat compat = getCompat();
    return (compat == null) ? getBare().isFocused() : compat.isFocused();
  }

  /** Returns flag whether window is picture-in-picture, or null if flag not available. */
  @TargetApi(Build.VERSION_CODES.O)
  @Nullable
  public final Boolean isInPictureInPictureMode() {
    AccessibilityWindowInfo bare = getBare();
    if (bare == null) {
      return null;
    }
    if (BuildVersionUtils.isAtLeastO()) {
      return bare.isInPictureInPictureMode();
    } else {
      return false;
    }
  }

  @Nullable
  public final CharSequence getTitle() {
    AccessibilityWindowInfoCompat compat = getCompat();
    return (compat == null) ? null : compat.getTitle();
  }

  public final @AccessibilityWindow.WindowType int getType() {
    AccessibilityWindowInfoCompat compat = getCompat();
    return (compat == null) ? TYPE_UNKNOWN : compat.getType();
  }

  /** Returns root node info, which caller must recycle. */
  @Nullable
  public final AccessibilityNode getRoot() {
    AccessibilityWindowInfoCompat compat = getCompat();
    if (compat != null) {
      return AccessibilityNode.takeOwnership(compat.getRoot());
    }
    AccessibilityWindowInfo bare = getBare();
    if (bare != null) {
      return AccessibilityNode.takeOwnership(bare.getRoot());
    }
    return null;
  }

  // TODO: Add more pass-through methods on demand. Keep alphabetic order. Prefer compat
  // methods.

  ///////////////////////////////////////////////////////////////////////////////////////
  // AccessibilityWindowInfoUtils pass-through methods.

  @Nullable
  public final Boolean isWindowContentVisible() {
    AccessibilityWindowInfo bare = getBare();
    return (bare == null) ? null : AccessibilityWindowInfoUtils.isWindowContentVisible(bare);
  }

  // TODO: Add more pass-through methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // Error methods

  @FormatMethod
  private void logOrThrow(@FormatString String format, Object... parameters) {
    if (isDebug()) {
      throwError(format, parameters);
    } else {
      logError(format, parameters);
    }
  }

  private void logOrThrow(IllegalStateException exception, String format, Object... parameters) {
    if (isDebug()) {
      throw exception;
    } else {
      logError(format, parameters);
      logError("%s", exception);
    }
  }

  protected void logError(String format, Object... parameters) {
    LogUtils.e(TAG, format, parameters);
  }

  @FormatMethod
  protected void throwError(@FormatString String format, Object... parameters) {
    throw new IllegalStateException(String.format(format, parameters));
  }

  public static String typeToString(@WindowType int windowType) {
    switch (windowType) {
      case TYPE_ACCESSIBILITY_OVERLAY:
        return "TYPE_ACCESSIBILITY_OVERLAY";
      case TYPE_APPLICATION:
        return "TYPE_APPLICATION";
      case TYPE_INPUT_METHOD:
        return "TYPE_INPUT_METHOD";
      case TYPE_SPLIT_SCREEN_DIVIDER:
        return "TYPE_SPLIT_SCREEN_DIVIDER";
      case TYPE_SYSTEM:
        return "TYPE_SYSTEM";
      case TYPE_UNKNOWN:
        return "TYPE_UNKNOWN";
      default:
        return "(unhandled)";
    }
  }
}
