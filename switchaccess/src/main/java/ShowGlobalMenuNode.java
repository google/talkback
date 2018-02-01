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

package com.google.android.accessibility.switchaccess;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import java.util.LinkedList;
import java.util.List;

/**
 * Leaf node of scanning tree that allows global menu to be shown. TODO: Consider removing
 * suppression and overriding hashCode()
 */
@SuppressWarnings("EqualsHashCode")
public class ShowGlobalMenuNode extends OverlayActionNode {

  public ShowGlobalMenuNode(OverlayController overlayController) {
    super(overlayController);
  }

  @Override
  public Rect getRectForNodeHighlight() {
    return mOverlayController.getMenuButtonLocation();
  }

  @Override
  public List<MenuItem> performActionOrGetMenuItems() {
    return GlobalMenuItem.getGlobalMenuItemList(
        (AccessibilityService) mOverlayController.getContext());
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ShowGlobalMenuNode;
  }

  @Override
  public List<CharSequence> getSpeakableText() {
    List<CharSequence> speakableText = new LinkedList<>();
    speakableText.add(
        mOverlayController.getContext().getString(R.string.option_scanning_menu_button));
    return speakableText;
  }
}
