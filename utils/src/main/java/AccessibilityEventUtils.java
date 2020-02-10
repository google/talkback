/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.google.android.accessibility.utils;

import android.app.Notification;
import android.os.Parcelable;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.checkerframework.checker.nullness.qual.Nullable;

/** This class contains utility methods. */
public class AccessibilityEventUtils {

  private static final String DIALOG_CLASS_NAME = "android.app.Dialog";
  private static final String VOLUME_CONTROLS_CLASS_IN_ANDROID_P =
      "com.android.systemui.volume.VolumeDialogImpl$CustomDialog";

  /** Unknown window id. Must match private variable AccessibilityWindowInfo.UNDEFINED_WINDOW_ID */
  public static final int WINDOW_ID_NONE = -1;

  /** Undefined scroll delta. */
  public static final int DELTA_UNDEFINED = -1;

  private AccessibilityEventUtils() {
    // This class is not instantiable.
  }

  /** Returns window id from event, or WINDOW_ID_NONE. */
  public static int getWindowId(@Nullable AccessibilityEvent event) {
    if (event == null) {
      return WINDOW_ID_NONE;
    }
    // Try to get window id from event.
    int windowId = event.getWindowId();
    if (windowId != WINDOW_ID_NONE) {
      return windowId;
    }
    // Try to get window id from event source.
    AccessibilityNodeInfo source = event.getSource();
    try {
      return (source == null) ? WINDOW_ID_NONE : source.getWindowId();
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(source);
    }
  }

  /**
   * Determines if an accessibility event is of a type defined by a mask of qualifying event types.
   *
   * @param event The event to evaluate
   * @param typeMask A mask of event types that will cause this method to accept the event as
   *     matching
   * @return {@code true} if {@code event}'s type is one of types defined in {@code typeMask},
   *     {@code false} otherwise
   */
  public static boolean eventMatchesAnyType(AccessibilityEvent event, int typeMask) {
    return event != null && (event.getEventType() & typeMask) != 0;
  }

  /**
   * Gets the text of an <code>event</code> by returning the content description (if available) or
   * by concatenating the text members (regardless of their priority) using space as a delimiter.
   *
   * @param event The event.
   * @return The event text.
   */
  public static CharSequence getEventTextOrDescription(AccessibilityEvent event) {
    if (event == null) {
      return null;
    }

    final CharSequence contentDescription = event.getContentDescription();

    if (!TextUtils.isEmpty(contentDescription)) {
      return contentDescription;
    }

    return getEventAggregateText(event);
  }

  /**
   * Gets the text of an <code>event</code> by concatenating the text members (regardless of their
   * priority) using space as a delimiter.
   *
   * @param event The event.
   * @return The event text.
   */
  public static CharSequence getEventAggregateText(AccessibilityEvent event) {
    if (event == null) {
      return null;
    }

    final SpannableStringBuilder aggregator = new SpannableStringBuilder();
    for (CharSequence text : event.getText()) {
      StringBuilderUtils.appendWithSeparator(aggregator, text);
    }

    return aggregator;
  }

  public static boolean isCharacterTraversalEvent(AccessibilityEvent event) {
    return (event.getEventType()
            == AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY
        && event.getMovementGranularity()
            == AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER);
  }

  /**
   * Returns true if the event came from a non main window event, such as invisible, toast, or IME
   * window; otherwise returns false. Examples:
   *
   * <ul>
   *   <li>Returns false for main activity windows.
   *   <li>Returns true for the soft keyboard window in an IME.
   *   <li>Returns true for toasts.
   *   <li>Returns true for volume slider.
   * </ul>
   */
  // TODO: Add window-types similar to AccessibilityWindowInfo.getType(), but more
  // specific and more stable across android versions.
  public static boolean isNonMainWindowEvent(AccessibilityEvent event) {
    // If there's an actual window ID, we need to check the window type (if window available).
    boolean isNonMainWindow = false;
    AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
    AccessibilityNodeInfoCompat source = record.getSource();
    if (source != null) {
      AccessibilityWindowInfoCompat window = AccessibilityNodeInfoUtils.getWindow(source);
      if (window == null) {
        // If window is not visible, we cannot know whether the window type is input method
        // or not. Let's assume that it comes from an IME. If window is visible but window
        // info is not available, it can be non-focusable visible window.
        isNonMainWindow = true;
      } else {
        switch (window.getType()) {
          case AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD:
            // IME case.
            isNonMainWindow = true;
            break;
          case AccessibilityWindowInfoCompat.TYPE_SYSTEM:
            isNonMainWindow = isFromVolumeControlPanel(event);
            break;
          default: // fall out
        }
        window.recycle();
      }
      source.recycle();
    }
    return isNonMainWindow;
  }

  public static boolean isFromVolumeControlPanel(AccessibilityEvent event) {
    // Volume slider case.
    // TODO: Find better way to handle volume slider.
    CharSequence sourceClassName = event.getClassName();
    boolean isVolumeInAndroidP =
        BuildVersionUtils.isAtLeastP()
            && TextUtils.equals(sourceClassName, VOLUME_CONTROLS_CLASS_IN_ANDROID_P);
    boolean isVolumeInAndroidO =
        BuildVersionUtils.isAtLeastO()
            && (!BuildVersionUtils.isAtLeastP())
            && TextUtils.equals(sourceClassName, DIALOG_CLASS_NAME);
    return isVolumeInAndroidO || isVolumeInAndroidP;
  }

  /** Returns whether the {@link AccessibilityEvent} contains {@link Notification} data. */
  public static boolean isNotificationEvent(AccessibilityEvent event) {
    // Real notification events always have parcelable data.
    return event != null
        && event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        && event.getParcelableData() != null;
  }

  /**
   * Extracts a {@link Notification} from an {@link AccessibilityEvent}.
   *
   * @param event The event to extract from.
   * @return The extracted Notification, or {@code null} on error.
   */
  public static @Nullable Notification extractNotification(AccessibilityEvent event) {
    final Parcelable parcelable = event.getParcelableData();

    if (!(parcelable instanceof Notification)) {
      return null;
    }

    return (Notification) parcelable;
  }

  /**
   * Returns the progress percentage from the event. The value will be in the range [0, 100], where
   * 100 is the maximum scroll amount.
   *
   * @param event The event from which to obtain the progress percentage.
   * @return The progress percentage.
   */
  public static float getProgressPercent(AccessibilityEvent event) {
    if (event == null) {
      return 0.0f;
    }
    final int maxProgress = event.getItemCount();
    final int progress = event.getCurrentItemIndex();
    final float percent = (progress / (float) maxProgress);

    return (100.0f * Math.max(0.0f, Math.min(1.0f, percent)));
  }

  /**
   * Returns the percentage scrolled within a scrollable view. The value will be in the range [0,
   * 100], where 100 is the maximum scroll amount.
   *
   * @param event The event from which to obtain the scroll position.
   * @param defaultValue Value to return if there is no scroll position from the event. This value
   *     should be in the range [0, 100].
   * @return The percentage scrolled within a scrollable view.
   */
  public static float getScrollPercent(AccessibilityEvent event, float defaultValue) {
    if (defaultValue < 0 || defaultValue > 100) {
      throw new IllegalArgumentException(
          "Default value should be in the range [0, 100]. Got " + defaultValue + ".");
    }
    final float position = getScrollPosition(event, defaultValue / 100);

    return (100.0f * Math.max(0.0f, Math.min(1.0f, position)));
  }

  /**
   * Returns a floating point value representing the scroll position of an {@link
   * AccessibilityEvent}. This value may be outside the range {0..1}. If there's no valid way to
   * obtain a position, this method returns the default value.
   *
   * @param event The event from which to obtain the scroll position.
   * @param defaultValue Value to return if there is no valid scroll position from the event.
   * @return A floating point value representing the scroll position.
   */
  public static float getScrollPosition(AccessibilityEvent event, float defaultValue) {
    if (event == null) {
      return defaultValue;
    }

    final int itemCount = event.getItemCount();
    final int fromIndex = event.getFromIndex();

    // First, attempt to use (fromIndex / itemCount).
    if ((fromIndex >= 0) && (itemCount > 0)) {
      return (fromIndex / (float) itemCount);
    }

    final int scrollY = event.getScrollY();
    final int maxScrollY = event.getMaxScrollY();

    // Next, attempt to use (scrollY / maxScrollY). This will fail if the
    // getMaxScrollX() method is not available.
    if ((scrollY >= 0) && (maxScrollY > 0)) {
      return (scrollY / (float) maxScrollY);
    }

    // Finally, attempt to use (scrollY / itemCount).
    // TODO: Investigate if it is still needed.
    if ((scrollY >= 0) && (itemCount > 0) && (scrollY <= itemCount)) {
      return (scrollY / (float) itemCount);
    }

    return defaultValue;
  }

  public static boolean hasSourceNode(AccessibilityEvent event) {
    if (event == null) {
      return false;
    }
    AccessibilityNodeInfo source = event.getSource();
    try {
      return source != null;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(source);
    }
  }

  /**
   * Recycles an old event, and obtains a copy of a new event to replace the old event.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * AccessibilityEvent lastEvent = firstEvent.obtain();
   * // Use lastEvent...
   * lastEvent = replaceWithCopy(lastEvent, secondEvent);
   * // Use lastEvent...
   * lastEvent.recycle();
   * }</pre>
   *
   * @param old An old event, which will be recycled by this function.
   * @param newEvent A new event which will be copied by this function. Caller must recycle
   *     newEvent.
   * @return A copy of newEvent, that the caller must eventually recycle.
   */
  public static AccessibilityEvent replaceWithCopy(
      @Nullable AccessibilityEvent old, @Nullable AccessibilityEvent newEvent) {
    if (old != null) {
      old.recycle();
    }
    return (newEvent == null) ? null : AccessibilityEvent.obtain(newEvent);
  }

  public static void recycle(AccessibilityEvent event) {
    if (event != null) {
      event.recycle();
    }
  }

  public static int[] getAllEventTypes() {
    return new int[] {
      AccessibilityEvent.TYPE_ANNOUNCEMENT,
      AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT,
      AccessibilityEvent.TYPE_GESTURE_DETECTION_END,
      AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
      AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
      AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END,
      AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START,
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED,
      AccessibilityEvent.TYPE_VIEW_CLICKED,
      AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED,
      AccessibilityEvent.TYPE_VIEW_FOCUSED,
      AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
      AccessibilityEvent.TYPE_VIEW_HOVER_EXIT,
      AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
      AccessibilityEvent.TYPE_VIEW_SCROLLED,
      AccessibilityEvent.TYPE_VIEW_SELECTED,
      AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
      AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
      AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY,
      AccessibilityEvent.TYPE_WINDOWS_CHANGED,
      AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    };
  }

  public static String typeToString(int eventType) {
    switch (eventType) {
      case AccessibilityEvent.TYPE_ANNOUNCEMENT:
        return "TYPE_ANNOUNCEMENT";
      case AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT:
        return "TYPE_ASSIST_READING_CONTEXT";
      case AccessibilityEvent.TYPE_GESTURE_DETECTION_END:
        return "TYPE_GESTURE_DETECTION_END";
      case AccessibilityEvent.TYPE_GESTURE_DETECTION_START:
        return "TYPE_GESTURE_DETECTION_START";
      case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
        return "TYPE_NOTIFICATION_STATE_CHANGED";
      case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END:
        return "TYPE_TOUCH_EXPLORATION_GESTURE_END";
      case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START:
        return "TYPE_TOUCH_EXPLORATION_GESTURE_START";
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        return "TYPE_TOUCH_INTERACTION_END";
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        return "TYPE_TOUCH_INTERACTION_START";
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        return "TYPE_VIEW_ACCESSIBILITY_FOCUSED";
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED:
        return "TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED";
      case AccessibilityEvent.TYPE_VIEW_CLICKED:
        return "TYPE_VIEW_CLICKED";
      case AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED:
        return "TYPE_VIEW_CONTEXT_CLICKED";
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        return "TYPE_VIEW_FOCUSED";
      case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
        return "TYPE_VIEW_HOVER_ENTER";
      case AccessibilityEvent.TYPE_VIEW_HOVER_EXIT:
        return "TYPE_VIEW_HOVER_EXIT";
      case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
        return "TYPE_VIEW_LONG_CLICKED";
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        return "TYPE_VIEW_SCROLLED";
      case AccessibilityEvent.TYPE_VIEW_SELECTED:
        return "TYPE_VIEW_SELECTED";
      case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
        return "TYPE_VIEW_TEXT_CHANGED";
      case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
        return "TYPE_VIEW_TEXT_SELECTION_CHANGED";
      case AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY:
        return "TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY";
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
        return "TYPE_WINDOWS_CHANGED";
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        return "TYPE_WINDOW_CONTENT_CHANGED";
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        return "TYPE_WINDOW_STATE_CHANGED";
      default:
        return "(unhandled)";
    }
  }

  public static int getScrollDeltaX(AccessibilityEvent event) {
    return BuildVersionUtils.isAtLeastP() ? event.getScrollDeltaX() : DELTA_UNDEFINED;
  }

  public static int getScrollDeltaY(AccessibilityEvent event) {
    return BuildVersionUtils.isAtLeastP() ? event.getScrollDeltaY() : DELTA_UNDEFINED;
  }

  public static boolean hasValidScrollDelta(AccessibilityEvent event) {
    return BuildVersionUtils.isAtLeastP()
        && ((event.getScrollDeltaX() != DELTA_UNDEFINED)
            || (event.getScrollDeltaY() != DELTA_UNDEFINED));
  }
}
