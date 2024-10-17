package com.google.android.accessibility.talkback.compositor;

import static android.view.View.ACCESSIBILITY_LIVE_REGION_NONE;

import android.os.SystemClock;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;

/**
 * This utility class implements a latest updated data which contain the source node of window
 * content change event, and the associated rate-limitation setting.
 */
public final class WindowContentChangeAnnouncementFilter {
  // The default time (in millisecond) when the update node does not tag with any value.
  private static final long CONTENT_CHANGE_MINIMUM_CYCLE = 30000;
  // The time (in millisecond) for the short duration of specific apps.
  private static final long SHORT_DURATION = 5000;
  // The time (in millisecond) for the long duration of specific apps.
  private static final long LONG_DURATION = 10000;

  // The recorded node which content is the last one ever changed.
  private static AccessibilityNodeInfoCompat lastUpdatedNode;
  // This records the time of TalkBack's announcement of window content change event.
  private static long timeStamp;

  private WindowContentChangeAnnouncementFilter() {}

  /**
   * Return {@code true} if the window content changed event with the source node should be
   * announced.
   */
  public static boolean shouldAnnounce(
      AccessibilityNodeInfoCompat node,
      boolean rateUnlimited,
      boolean enableShortAndLongDurationsForSpecificApps) {
    long time = AccessibilityNodeInfoUtils.getMinDurationBetweenContentChangesMillis(node);
    if (node == null) {
      return false;
    }

    if (enableShortAndLongDurationsForSpecificApps && time == 0) {
      boolean isShort = isShortDuration(node);
      boolean isLong = isLongDuration(node);

      if (isShort) {
        time = SHORT_DURATION;
      } else if (isLong) {
        time = LONG_DURATION;
      }
    }

    if (time == 0) {
      if (rateUnlimited) {
        return true;
      } else {
        // When the verbosity item "limit-text-change-rate" is on, uses default value.
        time = CONTENT_CHANGE_MINIMUM_CYCLE;
      }
    }

    long now = SystemClock.uptimeMillis();
    if (node.equals(lastUpdatedNode)) {
      if ((now - timeStamp) >= time) {
        timeStamp = now;
        return true;
      } else {
        return false;
      }
    } else {
      lastUpdatedNode = node;
      timeStamp = now;
      if (enableShortAndLongDurationsForSpecificApps) {
        if (node.getLiveRegion() == ACCESSIBILITY_LIVE_REGION_NONE) {
          // Skip the first announcement, to avoid two announcements in quick succession: one from
          // accessibility focus and the other from this window content change. Live regions should
          // not be skipped.
          return false;
        }
      }
      return true;
    }
  }

  /** Return {@code true} if source node is a timer of Clock. */
  private static boolean isShortDuration(AccessibilityNodeInfoCompat node) {
    CharSequence packageName = node.getPackageName();
    String resourceId = node.getViewIdResourceName();
    if (packageName == null || resourceId == null) {
      return false;
    }

    if (packageName.toString().equals("com.google.android.deskclock")
        && resourceId.contains("timer_text")) {
      return true;
    }
    return false;
  }

  /** Return {@code true} if source node is a stopwatch or video player of Clock or Photos. */
  private static boolean isLongDuration(AccessibilityNodeInfoCompat node) {
    CharSequence packageName = node.getPackageName();
    String resourceId = node.getViewIdResourceName();
    if (packageName == null || resourceId == null) {
      return false;
    }
    if (packageName.toString().equals("com.google.android.deskclock")
        && resourceId.contains("stopwatch_time_text")) {
      return true;
    }

    if (packageName.toString().equals("com.google.android.apps.photos")
        && resourceId.contains("video_player_progress")) {
      return true;
    }

    return false;
  }

  /**
   * To support the feedback of content change event which might triggered by user (touch)
   * operation; TalkBack will invalidate the cached node when touch interaction start event comes.
   */
  public static void invalidateRecordNode() {
    lastUpdatedNode = null;
  }
}
