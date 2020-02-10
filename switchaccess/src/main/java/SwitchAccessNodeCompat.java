/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.switchaccess;

import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.MIN_VISIBLE_PIXELS;

import android.graphics.Rect;
import android.os.Trace;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.switchaccess.PerformanceMonitor.TreeBuildingEvent;
import com.google.android.accessibility.switchaccess.utils.ActionBuildingUtils;
import com.google.android.accessibility.switchaccess.utils.FeedbackUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This class works around shortcomings of AccessibilityNodeInfo/Compat. One major issue is that the
 * visibility of Views that are covered by other Views or Windows is not handled completely by the
 * framework, but other issues may crop up over time.
 *
 * <p>In order to support performing actions on the UI, we need to have access to the real Info.
 * This class can thus either wrap or extend AccessibilityNodeInfo or Compat. Because most of the
 * methods in Compat work fine, a wrapper will include huge amounts of boilerplate, so this is an
 * extension of the Compat class (Info is final).
 *
 * <p>The biggest issue with this class is that it can't override the static {@code obtain} methods
 * in compat. That means that it is not compatible with utils methods built for Compat classes.
 * Arguably it thus shouldn't extend Compat, but the boilerplate savings seems worth dealing with.
 * We may eventually drop the extending and completely hide the Compat implementation if such
 * obtaining becomes an issue.
 */
public class SwitchAccessNodeCompat extends AccessibilityNodeInfoCompat {
  // The minimum amount of a view, either horizontally or vertically, that must be obscured by a
  // window above or child view for us to crop the visible bounds of the view.
  @VisibleForTesting static final float MIN_INTERSECTION_TO_CROP = 0.7f;

  // The maximum depth to traverse when getting the visibility of a node. Some trees may have loops
  // which we can't detect, so this prevents StackOverflowError and also reduces latency for these
  // as well as very deep trees. Increase this value with caution as it will greatly affect the
  // speed at which we can build the tree on Chrome.
  private static final int MAX_DEPTH = 2;

  private final List<AccessibilityWindowInfo> windowsAbove;
  private boolean visibilityAndSpokenTextCalculated = false;
  private Rect visibleBoundsInScreen;
  private Boolean boundsDuplicateAncestor;

  // The text inside the current node. If the node does not have any text, this will be the text
  // from its children. If the node itself does not contain any text, the text from its
  // non-focusable children are spoken to give users more information about the highlighted node.
  // Text from non-focusable children is included, as these nodes would not be scanned separately.
  @Nullable private CharSequence nodeTextUsingTextFromChildrenIfEmpty = null;

  /**
   * Find the largest sub-rectangle that doesn't intersect a specified one.
   *
   * @param rectToModify The rect that may be modified to avoid intersections
   * @param otherRect The rect that should be avoided
   */
  private static void adjustRectToAvoidIntersection(Rect rectToModify, Rect otherRect) {
    /*
     * Some rectangles are flipped around (left > right). Make sure we have two Rects free of
     * such pathologies.
     */
    rectToModify.sort();
    otherRect.sort();
    /*
     * Intersect rectToModify with four rects that represent cuts of the entire space along
     * lines defined by the otherRect's edges
     */
    Rect[] cuts = {
      new Rect(Integer.MIN_VALUE, Integer.MIN_VALUE, otherRect.left, Integer.MAX_VALUE),
      new Rect(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, otherRect.top),
      new Rect(otherRect.right, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
      new Rect(Integer.MIN_VALUE, otherRect.bottom, Integer.MAX_VALUE, Integer.MAX_VALUE)
    };

    int maxIntersectingRectArea = 0;
    int indexOfLargestIntersection = -1;
    for (int i = 0; i < cuts.length; i++) {
      if (cuts[i].intersect(rectToModify)) {
        /* Reassign this cut to its intersection with rectToModify */
        int visibleRectArea = cuts[i].width() * cuts[i].height();
        if (visibleRectArea > maxIntersectingRectArea) {
          maxIntersectingRectArea = visibleRectArea;
          indexOfLargestIntersection = i;
        }
      }
    }
    if (maxIntersectingRectArea <= 0) {
      // The rectToModify isn't within any of our cuts, so it's entirely occluded by otherRect.
      rectToModify.setEmpty();
      return;
    }
    rectToModify.set(cuts[indexOfLargestIntersection]);
  }

  /** @param info The info to wrap */
  public SwitchAccessNodeCompat(Object info) {
    this(info, null);
  }

  /**
   * @param info The info to wrap
   * @param windowsAbove The windows sitting on top of the current one. This list is used to compute
   *     visibility.
   */
  public SwitchAccessNodeCompat(Object info, @Nullable List<AccessibilityWindowInfo> windowsAbove) {
    super(info);
    if (info == null) {
      throw new NullPointerException();
    }
    if (windowsAbove == null) {
      this.windowsAbove = Collections.emptyList();
    } else {
      this.windowsAbove = new ArrayList<>(windowsAbove);
    }
  }

  // We aren't allowed to override a non-null method with a nullable method, but we always handle
  // the null case, so making this method @Nullable should be fine.
  @SuppressWarnings("nullness:override.return.invalid")
  @Override
  @Nullable
  public SwitchAccessNodeCompat getParent() {
    AccessibilityNodeInfo parent = unwrap().getParent();
    return (parent == null) ? null : new SwitchAccessNodeCompat(parent, windowsAbove);
  }

  // We aren't allowed to override a non-null method with a nullable method, but we always handle
  // the null case, so making this method @Nullable should be fine.
  @SuppressWarnings("nullness:override.return.invalid")
  @Override
  @Nullable
  public SwitchAccessNodeCompat getChild(int index) {
    AccessibilityNodeInfo child = unwrap().getChild(index);
    return (child == null) ? null : new SwitchAccessNodeCompat(child, windowsAbove);
  }

  @Override
  public boolean isVisibleToUser() {
    Trace.beginSection("SwitchAccessNodeCompat#isVisibleToUser");
    if (!isOnScreenAndVisibleToUser()) {
      Trace.endSection();
      return false;
    }

    // Views are considered visible only if a minimum number of pixels is showing.
    Rect visibleBounds = new Rect();
    getVisibleBoundsInScreen(visibleBounds);
    int visibleHeight = visibleBounds.height();
    int visibleWidth = visibleBounds.width();
    boolean isVisible =
        (visibleHeight >= MIN_VISIBLE_PIXELS) && (visibleWidth >= MIN_VISIBLE_PIXELS);
    Trace.endSection();
    return isVisible;
  }

  /**
   * Gets the developer-provided text inside the current node.
   *
   * @return the developer-provided text inside the current node
   */
  public CharSequence getNodeText() {
    if (!isOnScreenAndVisibleToUser()) {
      return "";
    }

    if (nodeTextUsingTextFromChildrenIfEmpty == null) {
      AccessibilityNodeInfoCompat compat = AccessibilityNodeInfoCompat.wrap(unwrap());
      nodeTextUsingTextFromChildrenIfEmpty = FeedbackUtils.getNodeText(compat);
    }

    return nodeTextUsingTextFromChildrenIfEmpty;
  }

  /** @return An immutable copy of the current window list */
  public List<AccessibilityWindowInfo> getWindowsAbove() {
    return Collections.unmodifiableList(windowsAbove);
  }

  /**
   * Get the largest rectangle in the bounds of the View that is not covered by another window.
   *
   * @param visibleBoundsInScreen The rect to return the visible bounds in
   */
  public void getVisibleBoundsInScreen(Rect visibleBoundsInScreen) {
    Trace.beginSection("SwitchAccessNodeCompat#getVisibleBoundsInScreen");
    updateVisibility(0 /* currentDepth */);
    visibleBoundsInScreen.set(this.visibleBoundsInScreen);
    Trace.endSection();
  }

  /**
   * Check if this node has been found to have bounds matching an ancestor, which means it gets
   * special treatment during traversal.
   *
   * @return {@code true} if this node was found to have the same bounds as an ancestor.
   */
  public boolean getHasSameBoundsAsAncestor() {
    Trace.beginSection("SwitchAccessNodeCompat#getHasSameBoundsAsAncestor");
    // Only need to check parent
    if (boundsDuplicateAncestor == null) {
      SwitchAccessNodeCompat parent = getParent();
      if (parent == null) {
        boundsDuplicateAncestor = false;
      } else {
        Rect parentBounds = new Rect();
        Rect myBounds = new Rect();
        parent.getVisibleBoundsInScreen(parentBounds);
        getVisibleBoundsInScreen(myBounds);
        boundsDuplicateAncestor = myBounds.equals(parentBounds);
        parent.recycle();
      }
    }
    Trace.endSection();
    return boundsDuplicateAncestor;
  }

  /**
   * Get a child with duplicate bounds in the screen, if one exists.
   *
   * @return A child with duplicate bounds or {@code null} if none exists.
   */
  public List<SwitchAccessNodeCompat> getDescendantsWithDuplicateBounds() {
    Trace.beginSection("SwitchAccessNodeCompat#getDescendantsWithDuplicateBounds");
    Rect myBounds = new Rect();
    getBoundsInScreen(myBounds);
    List<SwitchAccessNodeCompat> descendantsWithDuplicateBounds = new ArrayList<>();
    addDescendantsWithBoundsToList(descendantsWithDuplicateBounds, myBounds);
    Trace.endSection();
    return descendantsWithDuplicateBounds;
  }

  private void addDescendantsWithBoundsToList(
      List<SwitchAccessNodeCompat> listOfNodes, Rect bounds) {
    Rect childBounds = new Rect();
    for (int i = 0; i < getChildCount(); i++) {
      SwitchAccessNodeCompat child = getChild(i);
      if (child == null) {
        continue;
      }
      child.getBoundsInScreen(childBounds);
      if (bounds.equals(childBounds) && !listOfNodes.contains(child)) {
        child.boundsDuplicateAncestor = true;
        listOfNodes.add(child);
        child.addDescendantsWithBoundsToList(listOfNodes, bounds);
      } else {
        // Children can't be bigger than parents, so once the bounds are different they
        // must be smaller, and further descendants won't duplicate the bounds
        child.recycle();
      }
    }
  }

  /**
   * Obtain a new copy of this object. The resulting node must be recycled for efficient use of
   * underlying resources.
   *
   * @return A new copy of the node
   */
  public SwitchAccessNodeCompat obtainCopy() {
    Trace.beginSection("SwitchAccessNodeCompat#obtainCopy");
    SwitchAccessNodeCompat obtainedInstance =
        new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain(unwrap()), windowsAbove);

    /* Preserve lazily-initialized value if we have it */
    if (visibilityAndSpokenTextCalculated) {
      obtainedInstance.visibilityAndSpokenTextCalculated = true;
      obtainedInstance.visibleBoundsInScreen = new Rect(visibleBoundsInScreen);
      // The obtained new copy of this SwitchAccessNodeCompat doesn't retain information of its
      // children. If a SwitchAccessNodeCompat doesn't contain any text itself, but has child nodes
      // that contain text, the text inside the node will be lost in the copy. Therefore, we store
      // text of the node in the nodeTextUsingTextFromChildrenIfEmpty variable.
      // TODO: Use the original AccessibilityNodeInfo directly, instead of getting a
      // copy.
      obtainedInstance.nodeTextUsingTextFromChildrenIfEmpty = nodeTextUsingTextFromChildrenIfEmpty;
    }

    obtainedInstance.boundsDuplicateAncestor = boundsDuplicateAncestor;
    Trace.endSection();
    return obtainedInstance;
  }

  /** Returns {@code true} if this object has actions that Switch Access can perform. */
  public boolean hasActions() {
    Trace.beginSection("SwitchAccessNodeCompat#hasActions");
    for (AccessibilityActionCompat action : this.getActionList()) {
      if (ActionBuildingUtils.isActionSupportedByNode(action, this)) {
        Trace.endSection();
        return true;
      }
    }
    Trace.endSection();
    return false;
  }

  private void updateVisibility(int currentDepth) {
    if (visibilityAndSpokenTextCalculated || (currentDepth > MAX_DEPTH)) {
      return;
    }
    PerformanceMonitor.getOrCreateInstance()
        .startNewTimerEvent(TreeBuildingEvent.SCREEN_VISIBILITY_UPDATE);
    visibleBoundsInScreen = new Rect();
    if (!isOnScreenAndVisibleToUser()) {
      visibleBoundsInScreen.setEmpty();
      PerformanceMonitor.getOrCreateInstance()
          .stopTimerEvent(TreeBuildingEvent.SCREEN_VISIBILITY_UPDATE, false);
      return;
    }

    Trace.beginSection("SwitchAccessNodeCompat#updateVisibility (when visible to user)");
    getBoundsInScreen(visibleBoundsInScreen);
    visibleBoundsInScreen.sort();

    // Deal with visibility implications from windows above. However, do not update visibility for
    // sibling views as we cannot do so robustly. Notably, while we have drawing order, that is not
    // enough as views can be transparent and let touches through.
    reduceVisibleRectangleForWindowsAbove(visibleBoundsInScreen);

    PerformanceMonitor.getOrCreateInstance()
        .stopTimerEvent(TreeBuildingEvent.SCREEN_VISIBILITY_UPDATE, false);
    visibilityAndSpokenTextCalculated = true;
    Trace.endSection();
  }

  /*
   * @param visibleRect The sorted bounds of the Rect whose bounds should be reduced to account for
   *    windows drawn above the window containing this Rect
   */
  private void reduceVisibleRectangleForWindowsAbove(Rect visibleRect) {
    Trace.beginSection("SwitchAccessNodeCompat#reduceVisibleRectangleForWindowsAbove");
    Rect windowBoundsInScreen = new Rect();
    int visibleRectWidth = visibleRect.right - visibleRect.left;
    int visibleRectHeight = visibleRect.bottom - visibleRect.top;
    for (int i = 0; i < windowsAbove.size(); ++i) {
      windowsAbove.get(i).getBoundsInScreen(windowBoundsInScreen);
      windowBoundsInScreen.sort();
      Rect intersectingRectangle = new Rect(visibleRect);
      if (intersectingRectangle.intersect(windowBoundsInScreen)) {
        // If the rect above occupies less than a fraction of both sides of this rect, don't
        // adjust this rect's bounds. This prevents things like FABs changing the bounds
        // of scroll views under them.
        if (((intersectingRectangle.right - intersectingRectangle.left)
                < (visibleRectWidth * MIN_INTERSECTION_TO_CROP))
            && ((intersectingRectangle.bottom - intersectingRectangle.top)
                < (visibleRectHeight * MIN_INTERSECTION_TO_CROP))) {
          Trace.endSection();
          return;
        }
        adjustRectToAvoidIntersection(visibleRect, windowBoundsInScreen);
      }
    }
    Trace.endSection();
  }

  private boolean isOnScreenAndVisibleToUser() {
    // In WebViews {@link AccessibilityNodeInfo#isVisibleToUser()} sometimes returns true when an
    // item is actually off the screen, so {@link
    // AccessibilityNodeInfoUtils#hasMinimumPixelsVisibleOnScreen} is used instead.
    return super.isVisibleToUser()
        && AccessibilityNodeInfoUtils.hasMinimumPixelsVisibleOnScreen(this);
  }
}
