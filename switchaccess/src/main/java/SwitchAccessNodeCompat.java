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

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SwitchAccessNodeCompat extends AccessibilityNodeInfoCompat {
  // The minimim amount of a view, either horizontally or vertically, that must be obscured by a
  // window above for us to crop the visible bounds of the view.
  private static final float MIN_INTERSECTION_WITH_WINDOW_ABOVE = 0.7f;

  protected final List<AccessibilityWindowInfo> mWindowsAbove;
  private boolean mVisibilityCalculated = false;
  private Rect mVisibleBoundsInScreen;
  private Boolean mBoundsDuplicateAncestor;

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
      // The rectToModify isn't within any of our cuts, so it's entirely occuled by otherRect.
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
  public SwitchAccessNodeCompat(Object info, List<AccessibilityWindowInfo> windowsAbove) {
    super(info);
    if (info == null) {
      throw new NullPointerException();
    }
    if (windowsAbove == null) {
      mWindowsAbove = Collections.emptyList();
    } else {
      mWindowsAbove = new ArrayList<>(windowsAbove);
    }
  }

  @Override
  public SwitchAccessNodeCompat getParent() {
    AccessibilityNodeInfo info = (AccessibilityNodeInfo) getInfo();
    AccessibilityNodeInfo parent = info.getParent();
    return (parent == null) ? null : new SwitchAccessNodeCompat(parent, mWindowsAbove);
  }

  @Override
  public SwitchAccessNodeCompat getChild(int index) {
    AccessibilityNodeInfo info = (AccessibilityNodeInfo) getInfo();
    AccessibilityNodeInfo child = info.getChild(index);
    return (child == null) ? null : new SwitchAccessNodeCompat(child, mWindowsAbove);
  }

  @Override
  public boolean isVisibleToUser() {
    if (!super.isVisibleToUser()) {
      return false;
    }
    Rect bounds = new Rect();
    getVisibleBoundsInScreen(bounds);
    // If something has no area, it is not visible. Check was added as a result of degenerate
    // bounds of rectangles underneath Switch Access menus sometimes having been marked visible.
    return (bounds.top != bounds.bottom) && (bounds.left != bounds.right);
  }

  /** @return An immutable copy of the current window list */
  public List<AccessibilityWindowInfo> getWindowsAbove() {
    return Collections.unmodifiableList(mWindowsAbove);
  }

  /**
   * Get the largest rectangle in the bounds of the View that is not covered by another window.
   *
   * @param visibleBoundsInScreen The rect to return the visible bounds in
   */
  public void getVisibleBoundsInScreen(Rect visibleBoundsInScreen) {
    updateVisibility();
    visibleBoundsInScreen.set(mVisibleBoundsInScreen);
  }

  /**
   * Check if this node has been found to have bounds matching an ancestor, which means it gets
   * special treatment during traversal.
   *
   * @return {@code true} if this node was found to have the same bounds as an ancestor.
   */
  public boolean getHasSameBoundsAsAncestor() {
    // Only need to check parent
    if (mBoundsDuplicateAncestor == null) {
      SwitchAccessNodeCompat parent = getParent();
      if (parent == null) {
        mBoundsDuplicateAncestor = false;
      } else {
        Rect parentBounds = new Rect();
        Rect myBounds = new Rect();
        parent.getVisibleBoundsInScreen(parentBounds);
        getVisibleBoundsInScreen(myBounds);
        mBoundsDuplicateAncestor = myBounds.equals(parentBounds);
        parent.recycle();
      }
    }
    return mBoundsDuplicateAncestor;
  }

  /**
   * Get a child with duplicate bounds in the screen, if one exists.
   *
   * @return A child with duplicate bounds or {@code null} if none exists.
   */
  public List<SwitchAccessNodeCompat> getDescendantsWithDuplicateBounds() {
    Rect myBounds = new Rect();
    getBoundsInScreen(myBounds);
    List<SwitchAccessNodeCompat> descendantsWithDuplicateBounds = new ArrayList<>();
    addDescendantsWithBoundsToList(descendantsWithDuplicateBounds, myBounds);
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
        child.mBoundsDuplicateAncestor = true;
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
    SwitchAccessNodeCompat obtainedInstance =
        new SwitchAccessNodeCompat(
            AccessibilityNodeInfo.obtain((AccessibilityNodeInfo) getInfo()), mWindowsAbove);

    /* Preserve lazily-initialized value if we have it */
    if (mVisibilityCalculated) {
      obtainedInstance.mVisibilityCalculated = true;
      obtainedInstance.mVisibleBoundsInScreen = new Rect(mVisibleBoundsInScreen);
    }

    obtainedInstance.mBoundsDuplicateAncestor = mBoundsDuplicateAncestor;
    return obtainedInstance;
  }

  private void updateVisibility() {
    if (mVisibilityCalculated) {
      return;
    }
    try {
      mVisibleBoundsInScreen = new Rect();
      if (!super.isVisibleToUser()) {
        mVisibleBoundsInScreen.setEmpty();
        return;
      }

      getBoundsInScreen(mVisibleBoundsInScreen);
      mVisibleBoundsInScreen.sort();

      // Deal with visibility implications from windows above
      reduceVisibleRectangleForWindowsAbove(mVisibleBoundsInScreen);

      if (!isScrollable()) {
        // Deal with visibility implications from sister views
        SwitchAccessNodeCompat parent = getParent();
        if (parent == null) {
          return;
        }

        parent.reduceVisibleRectangleForChildView(this, mVisibleBoundsInScreen);
        parent.recycle();
      }
    } finally {
      mVisibilityCalculated = true;
    }
  }

  /*
   * @param targetChild The child whose bounds should be used to reduce the size of the provided
   *    Rect
   * @param visibleRect The sorted bounds of the Rect whose bounds should be reduced to account for
   *    the provided child view
   */
  private void reduceVisibleRectangleForChildView(
      SwitchAccessNodeCompat targetChild, Rect currentVisibleRect) {
    Rect tempRect = new Rect();
    // Update our visibility, then truncate the child view based on our own visible bounds
    updateVisibility();
    Rect myBounds = tempRect;
    getBoundsInScreen(myBounds);
    myBounds.sort();
    if (!currentVisibleRect.intersect(myBounds)) {
      currentVisibleRect.setEmpty();
      return;
    }

    /* Reduce visibility for children drawn after the requested one */
    Rect childBounds = new Rect();
    for (int i = 0; i < getChildCount(); i++) {
      SwitchAccessNodeCompat child = getChild(i);
      if (child == null) {
        continue;
      }
      if ((child.getDrawingOrder() > targetChild.getDrawingOrder())) {
        child.getBoundsInScreen(childBounds);
        childBounds.sort();
        if (Rect.intersects(currentVisibleRect, childBounds)) {
          adjustRectToAvoidIntersection(currentVisibleRect, childBounds);
        }
      }
      child.recycle();
    }
  }

  /*
   * @param visibleRect The sorted bounds of the Rect whose bounds should be reduced to account for
   *    windows drawn above the window containing this Rect
   */
  private void reduceVisibleRectangleForWindowsAbove(Rect visibleRect) {
    Rect windowBoundsInScreen = new Rect();
    int visibleRectWidth = visibleRect.right - visibleRect.left;
    int visibleRectHeight = visibleRect.bottom - visibleRect.top;
    for (int i = 0; i < mWindowsAbove.size(); ++i) {
      mWindowsAbove.get(i).getBoundsInScreen(windowBoundsInScreen);
      windowBoundsInScreen.sort();
      Rect intersectingRectangle = new Rect(visibleRect);
      if (intersectingRectangle.intersect(windowBoundsInScreen)) {
        // If the rect above occupies less than a fraction of both sides of this rect, don't
        // adjust this rect's bounds. This prevents things like FABs changing the bounds
        // of scroll views under them.
        if (((intersectingRectangle.right - intersectingRectangle.left)
                < (visibleRectWidth * MIN_INTERSECTION_WITH_WINDOW_ABOVE))
            && ((intersectingRectangle.bottom - intersectingRectangle.top)
                < (visibleRectHeight * MIN_INTERSECTION_WITH_WINDOW_ABOVE))) {
          return;
        }
        adjustRectToAvoidIntersection(visibleRect, windowBoundsInScreen);
      }
    }
  }
}
