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

import android.graphics.Rect;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Leaf node of Option Scanning tree that moves to the previous list of menu items if they exist or
 * removes all overlays otherwise.
 */
public class ClearOverlayNode extends OverlayActionNode {

  public ClearOverlayNode(OverlayController overlayController) {
    super(overlayController);
  }

  @Override
  public Rect getRectForNodeHighlight() {
    return mOverlayController.getCancelButtonLocation();
  }

  @Override
  public List<MenuItem> performActionOrGetMenuItems() {
    mOverlayController.moveToPreviousMenuItemsOrClearOverlays();
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object other) {
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
    speakableText.add(mOverlayController.getContext().getString(android.R.string.cancel));
    return speakableText;
  }
}
