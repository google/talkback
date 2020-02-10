/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.libraries.accessibility.utils.input;

import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Build;
import android.view.ViewConfiguration;
import com.google.android.libraries.accessibility.utils.device.ScreenUtils;

/** Utility class for creating common {@link GestureDescription}s. */
@TargetApi(Build.VERSION_CODES.N)
public class GestureUtils {

  // The default duration (in milliseconds) for a gesture
  private static final int DEFAULT_DURATION_MS = 400;

  /**
   * Create a description for a tap gesture. Gestures outside the screen will be offset to fit in
   * the screen.
   *
   * @param context The context in which the gesture is being created
   * @param x The x coordinate of the tap
   * @param y The y coordinate of the tap
   * @return A description of a tap at ({@code x}, {@code y})
   */
  public static GestureDescription createTap(Context context, int x, int y) {
    Path path = new Path();
    path.moveTo(x, y);
    int durationMs = ViewConfiguration.getTapTimeout();
    return createGestureDescription(new StrokeDescription(path, 0, durationMs));
  }

  /**
   * Create a description for a long press gesture. Gestures outside the screen will be offset to
   * fit in the screen.
   *
   * @param context The context in which the gesture is being created
   * @param x The x coordinate of the long press
   * @param y The y coordinate of the long press
   * @return A description of a long press at ({@code x}, {@code y})
   */
  public static GestureDescription createLongPress(Context context, int x, int y) {
    Path path = new Path();
    path.moveTo(x, y);
    int durationMs = ViewConfiguration.getLongPressTimeout() * 2;
    return createGestureDescription(new StrokeDescription(path, 0, durationMs));
  }

  /**
   * Create a description for a swipe gesture. Gestures outside the screen will be offset to fit in
   * the screen.
   *
   * @param context The context in which the gesture is being created
   * @param startX The x coordinate of the starting point
   * @param startY The y coordinate of the starting point
   * @param endX The x coordinate of the ending point
   * @param endY The y coordinate of the ending point
   * @return A description of a swipe from {@code startX}, {@code startY}) to ({@code endX}, {@code
   *     endY})
   */
  public static GestureDescription createSwipe(
      Context context, int startX, int startY, int endX, int endY) {
    // Crop path so that it fits on the screen.
    Point screenSize = ScreenUtils.getScreenSize(context);
    if (startX < 0) {
      startX = 0;
    } else if (startX > screenSize.x) {
      startX = screenSize.x;
    }
    if (startY < 0) {
      startY = 0;
    } else if (startY > screenSize.y) {
      startY = screenSize.y;
    }

    if (endX < 0) {
      endX = 0;
    } else if (endX > screenSize.x) {
      endX = screenSize.x;
    }
    if (endY < 0) {
      endY = 0;
    } else if (endY > screenSize.y) {
      endY = screenSize.y;
    }

    Path path = new Path();
    path.moveTo(startX, startY);
    path.lineTo(endX, endY);
    return createGestureDescription(new StrokeDescription(path, 0, DEFAULT_DURATION_MS));
  }

  /**
   * Create a description for a pinch (or zoom) gesture. Gestures outside the screen will be offset
   * to fit in the screen.
   *
   * @param context The context in which the gesture is being created
   * @param centerX The x coordinate of the center of the pinch
   * @param centerY The y coordinate of the center of the pinch
   * @param startSpacing The spacing of the touch points at the start of the gesture
   * @param endSpacing The spacing of the touch points at the end of the gesture
   * @return A description of a pinch centered at {@code xCenter}, {@code yCenter}) that starts with
   *     the touch points spaced by {@code startSpacing} and ends with them spaced by {@code
   *     endSpacing}
   */
  public static GestureDescription createPinch(
      Context context, int centerX, int centerY, int startSpacing, int endSpacing) {
    float[] startPoint1 = new float[2];
    float[] endPoint1 = new float[2];
    float[] startPoint2 = new float[2];
    float[] endPoint2 = new float[2];

    // Build points for a horizontal gesture centered at the origin.
    startPoint1[0] = startSpacing / 2;
    startPoint1[1] = 0;
    endPoint1[0] = endSpacing / 2;
    endPoint1[1] = 0;
    startPoint2[0] = -startSpacing / 2;
    startPoint2[1] = 0;
    endPoint2[0] = -endSpacing / 2;
    endPoint2[1] = 0;

    // Rotate and translate the points.
    Matrix matrix = new Matrix();
    matrix.postTranslate(centerX, centerY);
    matrix.mapPoints(startPoint1);
    matrix.mapPoints(endPoint1);
    matrix.mapPoints(startPoint2);
    matrix.mapPoints(endPoint2);

    // Make sure the points are inside the bounds of the screen.
    Point screenSize = ScreenUtils.getScreenSize(context);
    if (startPoint1[0] < 0) {
      startPoint1[0] = 0;
    } else if (startPoint1[0] >= screenSize.y) {
      startPoint1[0] = screenSize.y;
    }
    if (startPoint1[1] < 0) {
      startPoint1[1] = 0;
    } else if (startPoint1[1] >= screenSize.x) {
      startPoint1[1] = screenSize.x;
    }

    if (endPoint1[0] < 0) {
      endPoint1[0] = 0;
    } else if (endPoint1[0] >= screenSize.y) {
      endPoint1[0] = screenSize.y;
    }
    if (endPoint1[0] < 0) {
      endPoint1[0] = 0;
    } else if (endPoint1[0] >= screenSize.y) {
      endPoint1[0] = screenSize.y;
    }

    if (startPoint2[0] < 0) {
      startPoint2[0] = 0;
    } else if (startPoint2[0] >= screenSize.y) {
      startPoint2[0] = screenSize.y;
    }
    if (startPoint2[1] < 0) {
      startPoint2[1] = 0;
    } else if (startPoint2[1] >= screenSize.x) {
      startPoint2[1] = screenSize.x;
    }

    if (endPoint2[0] < 0) {
      endPoint2[0] = 0;
    } else if (endPoint2[0] >= screenSize.y) {
      endPoint2[0] = screenSize.y;
    }
    if (endPoint2[0] < 0) {
      endPoint2[0] = 0;
    } else if (endPoint2[0] >= screenSize.y) {
      endPoint2[0] = screenSize.y;
    }

    Path path1 = new Path();
    path1.moveTo(startPoint1[0], startPoint1[1]);
    path1.lineTo(endPoint1[0], endPoint1[1]);
    Path path2 = new Path();
    path2.moveTo(startPoint2[0], startPoint2[1]);
    path2.lineTo(endPoint2[0], endPoint2[1]);

    return createGestureDescription(
        new StrokeDescription(path1, 0, DEFAULT_DURATION_MS),
        new StrokeDescription(path2, 0, DEFAULT_DURATION_MS));
  }

  /** Create a gesture description. */
  private static GestureDescription createGestureDescription(StrokeDescription... strokes) {
    GestureDescription.Builder builder = new GestureDescription.Builder();
    for (StrokeDescription stroke : strokes) {
      builder.addStroke(stroke);
    }
    return builder.build();
  }
}
