/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.switchaccess.treenodes;

import android.graphics.Rect;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.utils.FeedbackUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.libraries.accessibility.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;

/** Leaf node in the scanning tree which corresponds to a non-actionable, scannable item. */
public class NonActionableItemNode extends TreeScanSystemProvidedNode {
  private final SwitchAccessNodeCompat nodeCompat;

  /**
   * Returns a new NonActionableItemNode if the provided {@link SwitchAccessNodeCompat} is
   * focusable, visible to the user, and has text.
   *
   * @param nodeCompat The {@link SwitchAccessNodeCompat} based on which this node will be created
   * @return A NonActionableItemNode if the provided {@link SwitchAccessNodeCompat} is focusable,
   *     visible to the user, and has text. Returns {@code null} otherwise.
   */
  @Nullable
  public static NonActionableItemNode createNodeIfHasText(SwitchAccessNodeCompat nodeCompat) {
    if (!nodeCompat.isVisibleToUser()) {
      return null;
    }

    // If the node corresponds to an item on a WebView, which could have deep nesting layouts,
    // perform extra checks so that same content is not scanned twice.
    if (WebInterfaceUtils.supportsWebActions(nodeCompat)) {
      // Use logic shared with TalkBack to filter out nodes that should not receive focus from focus
      // traversal or touch exploration in TalkBack. These nodes usually correspond to a chunk of
      // text (e.g.,a few words) which belong to a larger block of text (e.g., a paragraph).
      // Because Switch Access scans the larger block first and speaks all of its content, there is
      // no need to scan small chunks contained by the larger block individually and read their
      // content again.
      if (!AccessibilityNodeInfoUtils.isAccessibilityFocusable(nodeCompat)) {
        return null;
      }

      // Check if the node is actionable for accessibility. That is, the node supports one of the
      // following actions: AccessibilityNodeInfoCompat#isClickable(),
      // AccessibilityNodeInfoCompat#isFocusable() and AccessibilityNodeInfoCompat#isLongClickable.
      // This check is performed so that the tabs on some WebViews (e.g., the trips overview page
      // after searching for a location in Chrome) are not scanned twice. These actionable nodes are
      // skipped when creating ShowActionsMenuNode because they have the same bounds as their
      // actionable parents.
      if (AccessibilityNodeInfoUtils.isActionableForAccessibility(nodeCompat)) {
        return null;
      }
    }

    // If the non-actionable node itself doesn't contain any text, then there is no need to scan
    // this node.
    CharSequence speakableText = nodeCompat.getNodeText();
    if (StringUtils.isEmpty(speakableText)) {
      return null;
    }

    // Use logic shared with TalkBack to filter out non-focusable nodes that have an actionable
    // parent. Keeping this logic shared is important for a consistent user experience.
    if (!AccessibilityNodeInfoUtils.shouldFocusNode(nodeCompat)) {
      return null;
    }

    // Ignore non-actionable nodes whose child(ren) has the same visible bounds and text. This logic
    // is necessary for apps that over-label things (e.g., Google App on search results).
    int childCount = nodeCompat.getChildCount();
    for (int i = 0; i < childCount; i++) {
      SwitchAccessNodeCompat child = nodeCompat.getChild(i);
      if (child != null) {
        if (speakableText.equals(child.getNodeText()) && child.getHasSameBoundsAsAncestor()) {
          child.recycle();
          return null;
        }
        child.recycle();
      }
    }

    return new NonActionableItemNode(nodeCompat);
  }

  /**
   * @param nodeCompat The {@link SwitchAccessNodeCompat} that will be used to generate this node
   */
  protected NonActionableItemNode(SwitchAccessNodeCompat nodeCompat) {
    this.nodeCompat = nodeCompat.obtainCopy();
  }

  @Override
  protected void getVisibleBoundsInScreen(Rect bounds) {
    nodeCompat.getVisibleBoundsInScreen(bounds);
  }

  @Override
  public boolean isProbablyTheSameAs(Object other) {
    if (!(other instanceof NonActionableItemNode)) {
      return false;
    }

    return super.isProbablyTheSameAs(other);
  }

  @Override
  public void recycle() {
    nodeCompat.recycle();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (!(other instanceof NonActionableItemNode)) {
      return false;
    }

    NonActionableItemNode otherNode = (NonActionableItemNode) other;

    return otherNode.getBoundsInScreen().equals(getBoundsInScreen())
        && TextUtils.equals(otherNode.nodeCompat.getNodeText(), nodeCompat.getNodeText());
  }

  @Override
  public int hashCode() {
    /*
     * Hashing function taken from an example in "Effective Java" page 38/39. The number 13 is
     * arbitrary, but choosing non-zero number to start decreases the number of collisions. 37
     * is used as it's an odd prime. If multiplication overflowed and the 37 was an even number,
     * it would be equivalent to bit shifting. The fact that 37 is prime is standard practice.
     */
    int hashCode = 13;
    hashCode = 37 * hashCode + getBoundsInScreen().hashCode();
    hashCode = 37 * hashCode + getClass().hashCode();
    return hashCode;
  }

  @Override
  public List<CharSequence> getSpeakableText() {
    List<CharSequence> speakableText = new ArrayList<>();
    speakableText.add(FeedbackUtils.getSpeakableTextForNonActionableNode(nodeCompat));
    return speakableText;
  }

  @Override
  protected boolean hasSimilarText(TreeScanSystemProvidedNode otherNode) {
    return getSpeakableText().toString().contentEquals(otherNode.getSpeakableText().toString());
  }

  @Override
  protected SwitchAccessNodeCompat getNodeInfoCompatDirectly() {
    return nodeCompat;
  }
}
