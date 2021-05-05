/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils.compat.view;

public class InputDeviceCompatUtils {
  /**
   * The input source is a pointing device associated with a display. Examples: {@link
   * #SOURCE_TOUCHSCREEN}, {@link #SOURCE_MOUSE}. A {@link android.view.MotionEvent} should be
   * interpreted as absolute coordinates in display units according to the {@link android.view.View}
   * hierarchy. Pointer down/up indicated when the finger touches the display or when the selection
   * button is pressed/released.
   */
  private static final int SOURCE_CLASS_POINTER = 0x00000002;

  /**
   * The input source is a mouse pointing device. This code is also used for other mouse-like
   * pointing devices such as trackpads and trackpoints.
   *
   * @see #SOURCE_CLASS_POINTER
   */
  public static final int SOURCE_MOUSE = 0x00002000 | SOURCE_CLASS_POINTER;

  /**
   * The input source is a touch screen pointing device.
   *
   * @see #SOURCE_CLASS_POINTER
   */
  public static final int SOURCE_TOUCHSCREEN = 0x00001000 | SOURCE_CLASS_POINTER;

  /** The input source is unknown. */
  public static final int SOURCE_UNKNOWN = 0x00000000;

  private InputDeviceCompatUtils() {
    // This class is non-instantiable.
  }
}
