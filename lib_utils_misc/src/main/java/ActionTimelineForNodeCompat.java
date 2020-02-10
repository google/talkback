package com.google.android.libraries.accessibility.utils.undo;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/** An {@link ActionTimeline}, with a reference to its {@link AccessibilityNodeInfoCompat}. */
public abstract class ActionTimelineForNodeCompat extends ActionTimeline {

  protected AccessibilityNodeInfoCompat node;

  public ActionTimelineForNodeCompat(AccessibilityNodeInfoCompat nodeToTrack) {
    node = nodeToTrack;
  }
}
