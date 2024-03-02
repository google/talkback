/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.android.accessibility.utils.material;

import androidx.annotation.IntDef;

/** Defines the types of the icon which are used in button. */
public class ButtonIconType {

  /** The types of icon is used for button. */
  @IntDef({ICON_TYPE_BACK, ICON_TYPE_NEXT, ICON_TYPE_CANCEL, ICON_TYPE_CHECK})
  public @interface IconType {}

  public static final int ICON_TYPE_BACK = 0;
  public static final int ICON_TYPE_NEXT = 1;
  public static final int ICON_TYPE_CANCEL = 2;
  public static final int ICON_TYPE_CHECK = 3;

  private ButtonIconType() {}
}
