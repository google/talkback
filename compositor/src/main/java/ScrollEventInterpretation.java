/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.compositor;

import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.Role;

/** Contains derived data about scroll events. */
public class ScrollEventInterpretation extends ReadOnly {
  private int mElementRole = Role.ROLE_NONE;
  private boolean mFound = false;

  public void setElementRole(int role) {
    checkIsWritable();
    mElementRole = role;
  }

  public int getElementRole() {
    return mElementRole;
  }

  public void setFound(boolean found) {
    checkIsWritable();
    mFound = found;
  }

  public boolean getFound() {
    return mFound;
  }

  @Override
  public String toString() {
    return String.format("%s %sfound", Role.roleToString(mElementRole), (mFound ? "" : "not "));
  }
}
