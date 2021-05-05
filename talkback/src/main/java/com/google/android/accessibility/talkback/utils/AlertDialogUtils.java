/*
 * Copyright (C) 2020 Google Inc.
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
package com.google.android.accessibility.talkback.utils;

import android.app.AlertDialog;
import android.content.Context;
import com.google.android.accessibility.talkback.R;

/** Utility class for AlertDialog */
public class AlertDialogUtils {

  /**
   * Create {@link AlertDialog.Builder} and apply theme. Also customize the Builder so that, the
   * dialog buttons can adapt the color of foreground text, when the input focus changed, to comply
   * the contrast criteria.
   */
  public static AlertDialog.Builder createBuilder(Context context) {
    return new AlertDialogAdaptiveContrast(context, R.style.AlertDialogTheme);
  }
}
