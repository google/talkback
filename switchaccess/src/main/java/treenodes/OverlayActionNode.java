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

import com.google.android.accessibility.switchaccess.ui.OverlayController;

/** Leaf node of scanning tree that manipulates the overlay directly. */
public abstract class OverlayActionNode extends TreeScanLeafNode {

  final OverlayController overlayController;

  public OverlayActionNode(OverlayController overlayController) {
    super();
    this.overlayController = overlayController;
  }
}
