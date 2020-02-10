/*
 * Copyright (C) 2018 Google Inc.
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
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A {@link SwitchAccessActionBase} that produces multiple {@link SwitchAccessAction}s. */
public class SwitchAccessActionGroup extends SwitchAccessActionBase {

  /**
   * @param id The id used to identify this action group. Should correspond to a valid {@link
   *     AccessibilityActionCompat} id.
   */
  public SwitchAccessActionGroup(int id) {
    this(id, null);
  }

  /**
   * @param id The id used to identify this action group. Should correspond to a valid {@link
   *     AccessibilityActionCompat} id.
   * @param label The human readable label used to identify this action group
   */
  public SwitchAccessActionGroup(int id, @Nullable CharSequence label) {
    super(id, label);
  }

  /**
   * @param actionCompat The {@link AccessibilityActionCompat} whose id and label identify this
   *     action group
   */
  public SwitchAccessActionGroup(AccessibilityActionCompat actionCompat) {
    this(actionCompat.getId(), actionCompat.getLabel());
  }

  @Override
  public ActionResult execute(AccessibilityService service) {
    // This does nothing because this class is used in GroupedMenuItem to create granular
    // actions and is never actually executed.
    return new ActionResult(false);
  }
}
