/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.android.accessibility.utils;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import android.widget.Button;

/** Utility class for Material component */
public class MaterialComponentUtils {
  /** Types of Button style. */
  public enum ButtonStyle {
    FILLED_BUTON,
    OUTLINED_BUTTON,
    DEFAULT_BUTTON,
  }

  /**
   * Decides if support Material component. Always returns false because it doesn't support.
   *
   * @return the value to decide if support Material component.
   */
  public static boolean supportMaterialComponent() {
    return false;
  }

  /**
   * Creates {@link AlertDialog.Builder} and apply theme. Also customize the Builder so that, the
   * dialog buttons can adapt the color of foreground text, when the input focus changed, to comply
   * the contrast criteria.
   *
   * @param context The current context
   * @return {@code AlertDialog.Builder} return AlertDialog.Builder
   */
  public static AlertDialog.Builder alertDialogBuilder(Context context) {
    return AlertDialogAdaptiveContrastUtil.v7AlertDialogAdaptiveContrastBuilder(
        context, R.style.A11yAlertDialogTheme);
  }

  /**
   * Creates {@link Button} since it doesn't support Material component.
   *
   * @param context The current context
   * @param buttonStyle The type of button style
   * @return {@code Button} return Button
   */
  public static Button createButton(Context context, ButtonStyle buttonStyle) {
    return new Button(context);
  }
}
