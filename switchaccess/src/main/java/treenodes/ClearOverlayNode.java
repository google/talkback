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

import android.graphics.Rect;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem.SelectMenuItemListener;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuItemEnum;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Leaf node of scanning tree that moves to the previous list of menu items if they exist or removes
 * all overlays otherwise.
 */
public class ClearOverlayNode extends OverlayActionNode {

  public ClearOverlayNode(OverlayController overlayController) {
    super(overlayController);
  }

  @Nullable
  @Override
  public Rect getRectForNodeHighlight() {
    return overlayController.getCancelButtonLocation();
  }

  @Override
  public boolean isProbablyTheSameAs(Object other) {
    // We don't want to compare a ClearOverlayNode and another type of TreeScanNode when determining
    // whether or not to clear focus, as the highlight should never fall on this node when scanning.
    return other instanceof ClearOverlayNode;
  }

  @Override
  public List<MenuItem> performActionOrGetMenuItems(
      @Nullable SelectMenuItemListener selectMenuItemListener) {
    overlayController.clearAllOverlays();

    if (selectMenuItemListener != null) {
      selectMenuItemListener.onMenuItemSelected(
          SwitchAccessMenuItemEnum.MenuItem.MENU_BUTTON_CANCEL);
    }
    return Collections.emptyList();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof ClearOverlayNode;
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
    List<CharSequence> speakableText = new LinkedList<>();
    speakableText.add(overlayController.getContext().getString(R.string.switch_access_close_menu));
    return speakableText;
  }
}
