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

package com.google.android.accessibility.switchaccess.treenodes;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.menuitems.GlobalMenuItem;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem.SelectMenuItemListener;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Leaf node of scanning tree that allows global menu to be shown. */
public class ShowGlobalMenuNode extends OverlayActionNode {

  public ShowGlobalMenuNode(OverlayController overlayController) {
    super(overlayController);
  }

  @Nullable
  @Override
  public Rect getRectForNodeHighlight() {
    return overlayController.getMenuButtonLocation();
  }

  @Override
  public boolean isProbablyTheSameAs(Object other) {
    // If the other node was also a ShowGlobalMenuNode, we can assume that it was probably the same,
    // as we should only have one ShowGlobalMenuNode per tree.
    return (other instanceof ShowGlobalMenuNode);
  }

  @Override
  public List<MenuItem> performActionOrGetMenuItems(
      @Nullable SelectMenuItemListener selectMenuItemListener) {
    return GlobalMenuItem.getGlobalMenuItemList(
        (AccessibilityService) overlayController.getContext(), selectMenuItemListener);
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof ShowGlobalMenuNode;
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
    hashCode = 37 * hashCode + getClass().hashCode();
    return hashCode;
  }

  @Override
  public List<CharSequence> getSpeakableText() {
    List<CharSequence> speakableText = new ArrayList<>();
    speakableText.add(
        overlayController
            .getContext()
            .getString(R.string.option_scanning_menu_button_content_description));
    return speakableText;
  }
}
