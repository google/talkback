/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.braille.common;

import android.content.res.Resources;
import androidx.annotation.StringRes;

/** Dot pattern layout possibilities. */
public enum TouchDots {
  AUTO_DETECT(R.string.auto_detect, R.string.auto_detect),
  SCREEN_AWAY(R.string.screen_away, R.string.screen_away_detail),
  TABLETOP(R.string.tabletop, R.string.tabletop_detail);

  final int layoutNameStringId;
  final int layoutDescriptionStringId;

  TouchDots(@StringRes int layoutNameStringId, @StringRes int layoutDescriptionStringId) {
    this.layoutNameStringId = layoutNameStringId;
    this.layoutDescriptionStringId = layoutDescriptionStringId;
  }

  public String getLayoutName(Resources resources) {
    return resources.getString(layoutNameStringId);
  }

  public String getLayoutDescription(Resources resources) {
    return resources.getString(layoutDescriptionStringId);
  }
}
