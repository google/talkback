/*
 * Copyright (C) 2013 Google Inc.
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

package com.google.android.accessibility.utils.labeling;

/** A class that stores summary information about the labels in a package. */
public class PackageLabelInfo {
  private String mPackageName;
  private int mLabelCount;

  /**
   * Constructs a new {@link PackageLabelInfo} instance.
   *
   * @param packageName The fully-qualified name of the package.
   * @param labelCount The number of custom labels for the package.
   */
  public PackageLabelInfo(String packageName, int labelCount) {
    mPackageName = packageName;
    mLabelCount = labelCount;
  }

  /** @return The fully-qualified name of the package. */
  public String getPackageName() {
    return mPackageName;
  }

  /** @return The number of custom labels for the package. */
  public int getLabelCount() {
    return mLabelCount;
  }
}
