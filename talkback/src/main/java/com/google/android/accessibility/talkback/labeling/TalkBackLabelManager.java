/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.accessibility.talkback.labeling;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.labeling.LabelManager;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Abstract controller for detecting unlabeled views and managing custom labels for them.
 *
 * <p>The former requires to keep track of the most recent accessibility focus event.
 */
public abstract class TalkBackLabelManager implements LabelManager, AccessibilityEventListener {
  private boolean hasFocusedEventText = false;

  @Override
  public int getEventTypes() {
    return AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    hasFocusedEventText =
        !TextUtils.isEmpty(AccessibilityEventUtils.getEventTextOrDescription(event));
  }

  /**
   * Returns whether the given node is missing a label.
   *
   * <p>To be called from the read-only state reader interface.
   */
  protected boolean needsLabel(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    // Spoken words can come from accessibility event text or content description, like the focus is
    // on ViewGroup that includes several non-focusable views.
    if (Role.getRole(node) == Role.ROLE_VIEW_GROUP && hasFocusedEventText) {
      return false;
    }

    // REFERTO. ImageView without content description, which isn't focusable and in a
    // actionable ViewGroup, can't be labelled because it is not in the accessibility node tree but
    // in the hierarchy viewer. The actionable ViewGroup should be labelled.
    return node.isEnabled()
        && (AccessibilityNodeInfoUtils.isClickable(node)
            || AccessibilityNodeInfoUtils.isLongClickable(node)
            || (AccessibilityNodeInfoUtils.isFocusable(node) && canAddLabel(node)))
        && node.getChildCount() == 0
        && TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node));
  }

  /**
   * Gets the text of a <code>node</code> by returning the content description (if available) or by
   * returning the text. Will use the specified <code>CustomLabelManager</code> as a fall back if
   * both are null.
   *
   * @param node The node.
   * @param labelManager The label manager.
   * @return The node text.
   */
  public static @Nullable CharSequence getNodeText(
      AccessibilityNodeInfoCompat node, TalkBackLabelManager labelManager) {
    CharSequence text = AccessibilityNodeInfoUtils.getNodeText(node);
    if (!TextUtils.isEmpty(text)) {
      return text;
    }

    if (labelManager != null && labelManager.isInitialized()) {
      Label label = labelManager.getLabelForViewIdFromCache(node.getViewIdResourceName());
      if (label != null) {
        return label.getText();
      }
    }
    return null;
  }

  /** Whether this manager is properly initialized and ready to store and retrieve labels. */
  public boolean isInitialized() {
    // When this non-static method is called, this object is already initialized by default.
    return true;
  }

  /** Gets called in TalkBack's {@code onResumeInfrastracture()}. */
  public void onResume(Context context) {
    // Do nothing by default.
  }

  /** Gets called in TalkBack's {@code suspendInfrastructure()}. */
  public void onSuspend(Context context) {
    // Do nothing by default.
  }

  /** Gets called in TalkBack's {@code onUnlockedBootCompleted()}. */
  public void onUnlockedBoot() {
    // Do nothing by default.
  }

  /** Shuts down this manager and releases resources. */
  public void shutdown() {
    // do nothing by default
  }

  /** Tries to overwrite the label for a node and returns whether it was successful. */
  public abstract boolean setLabel(
      @Nullable AccessibilityNodeInfoCompat node, @Nullable String userLabel);

  /** Fetches labels from database and calls the given {@code callback} when done. */
  public abstract void getLabelsFromDatabase(LabelsFetchRequest.OnLabelsFetchedListener callback);

  @Override
  public abstract @Nullable Label getLabelForViewIdFromCache(String resourceName);

  /** Returns whether the manager can store a label for the given node. */
  public abstract boolean canAddLabel(@Nullable AccessibilityNodeInfoCompat node);
}
