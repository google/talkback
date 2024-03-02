package com.google.android.accessibility.talkback.compositor;

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
  // The recorded node which content is the last one ever changed.
  private static AccessibilityNodeInfoCompat lastUpdatedNode;
  // This records the time of TalkBack's announcement of window content change event.
  private static long timeStamp;

  private WindowContentChangeAnnouncementFilter() {}

  /**
   * Return {@code true} if the window content changed event with the source node should be
   * announced.
   */
  public static boolean shouldAnnounce(AccessibilityNodeInfoCompat node, boolean rateUnlimited) {
    long time = AccessibilityNodeInfoUtils.getMinDurationBetweenContentChangesMillis(node);
    if (node == null) {
      return false;
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
      return true;
    }
  }

  /**
   * To support the feedback of content change event which might triggered by user (touch)
   * operation; TalkBack will invalidate the cached node when touch interaction start event comes.
   */
  public static void invalidateRecordNode() {
    lastUpdatedNode = null;
  }
}
