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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.google.android.accessibility.switchaccess.utils.FeedbackUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import java.util.LinkedList;
import java.util.List;

/** Leaf node in the scanning tree which corresponds to a non-actionable, scannable item. */
public class NonActionableItemNode extends TreeScanSystemProvidedNode {
  protected SwitchAccessNodeCompat mNodeCompat;
  private final Context mContext;

  /**
   * Returns a new NonActionableItemNode if the provided {@link SwitchAccessNodeCompat} is
   * focusable, visible to the user, and has text.
   *
   * @param context Context used to determine whether autoselect is enabled
   * @param nodeCompat The {@link SwitchAccessNodeCompat} based on which this node will be created
   * @return A NonActionableItemNode if the provided {@link SwitchAccessNodeCompat} is focusable,
   *     visible to the user, and has text. Returns {@code null} otherwise.
   */
  public static NonActionableItemNode createNodeIfHasText(
      Context context, @NonNull SwitchAccessNodeCompat nodeCompat) {
    if (!nodeCompat.isVisibleToUser()) {
      return null;
    }

    String speakableText = FeedbackUtils.getNodeText(nodeCompat);
    if (speakableText == null || speakableText.isEmpty()) {
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

      if (speakableText.equals(FeedbackUtils.getNodeText(child))
          && child.getHasSameBoundsAsAncestor()) {
        child.recycle();
        return null;
      }
      child.recycle();
    }

    return new NonActionableItemNode(nodeCompat, context);
  }

  /**
   * @param nodeCompat The {@link SwitchAccessNodeCompat} that will be used to generate this node
   */
  protected NonActionableItemNode(SwitchAccessNodeCompat nodeCompat, Context context) {
    mNodeCompat = nodeCompat.obtainCopy();
    mContext = context;
  }

  @Override
  public SwitchAccessNodeCompat getNodeInfoCompat() {
    return mNodeCompat.obtainCopy();
  }

  private Rect getBoundsInScreen() {
    Rect bounds = new Rect();
    mNodeCompat.getBoundsInScreen(bounds);
    return bounds;
  }

  @Override
  protected void getVisibleBoundsInScreen(Rect bounds) {
    mNodeCompat.getVisibleBoundsInScreen(bounds);
  }

  @Override
  public void recycle() {
    mNodeCompat.recycle();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof NonActionableItemNode)) {
      return false;
    }

    NonActionableItemNode otherNode = (NonActionableItemNode) other;

    if (!otherNode.getBoundsInScreen().equals(getBoundsInScreen())) {
      return false;
    }

    return TextUtils.equals(
        FeedbackUtils.getNodeText(otherNode.mNodeCompat), FeedbackUtils.getNodeText(mNodeCompat));
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
    List<CharSequence> speakableText = new LinkedList<>();
    speakableText.add(FeedbackUtils.getSpeakableTextForNode(mContext, mNodeCompat));
    return speakableText;
  }
}
