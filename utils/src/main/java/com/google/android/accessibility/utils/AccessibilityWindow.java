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

import static com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WINDOW_ID_NONE;
import static com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WINDOW_TYPE_NONE;

import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WindowType;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Collection;

/**
 * A wrapper around AccessibilityWindowInfo/Compat, to help with:
 *
 * <ul>
 *   <li>handling null windows
 *   <li>using compat vs bare methods
 *   <li>using correct methods for various android versions
 * </ul>
 */
public class AccessibilityWindow {

  ///////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String TAG = "AccessibilityWindow";

  ///////////////////////////////////////////////////////////////////////////////////////
  // Member data

  /**
   * The wrapped window info. Both bare and compat objects are currently required, because
   * AccessibilityWindowInfoCompat has no un/wrap() methods. Do not expose this object.
   */
  private AccessibilityWindowInfo windowBare;

  private AccessibilityWindowInfoCompat windowCompat;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Construction

  /** Takes ownership of window*Arg. Does not allow all-null arguments. */
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
   * @param windowBareArg The wrapped window info.
   * @param windowCompatArg The wrapped window info.
   * @param factory Creates instances of AccessibilityWindow or sub-classes.
   * @return AccessibilityWindow instance.
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
    return false;
  }

  public static void recycle(String caller, @Nullable AccessibilityWindow... windows) {}

  public static void recycle(String caller, @Nullable Collection<AccessibilityWindow> windows) {}

  public final synchronized void recycle(String caller) {}

  /** Overridable for testing. */
  protected boolean isDebug() {
    return BuildConfig.DEBUG;
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // AccessibilityWindowInfo/Compat pass-through methods. Prefers compat methods. Also see
  // https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo

  private AccessibilityWindowInfo getBare() {
    return windowBare;
  }

  private AccessibilityWindowInfoCompat getCompat() {
    return windowCompat;
  }

  public final boolean isActive() {
    AccessibilityWindowInfoCompat compat = getCompat();
    return (compat == null) ? getBare().isActive() : compat.isActive();
  }

  public final boolean isFocused() {
    AccessibilityWindowInfoCompat compat = getCompat();
    return (compat == null) ? getBare().isFocused() : compat.isFocused();
  }

  /** Returns flag whether window is picture-in-picture, or null if flag not available. */
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

  /** Returns the window id if available, otherwise returns {@code WINDOW_ID_UNKNOWN}. */
  public final int getId() {
    AccessibilityWindowInfoCompat compat = getCompat();
    if (compat != null) {
      return compat.getId();
    }
    AccessibilityWindowInfo bare = getBare();
    if (bare != null) {
      return bare.getId();
    }
    return WINDOW_ID_NONE;
  }

  @Nullable
  public final CharSequence getTitle() {
    AccessibilityWindowInfoCompat compat = getCompat();
    return (compat == null) ? null : compat.getTitle();
  }

  @WindowType
  public final int getType() {
    AccessibilityWindowInfoCompat compat = getCompat();
    return (compat == null) ? WINDOW_TYPE_NONE : compat.getType();
  }

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

  protected void logError(String format, Object... parameters) {
    LogUtils.e(TAG, format, parameters);
  }

  @FormatMethod
  protected void throwError(@FormatString String format, Object... parameters) {
    throw new IllegalStateException(String.format(format, parameters));
  }

}
