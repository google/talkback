/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime;

import android.app.Dialog;
import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

/** Helps configure dialogs. */
public class Dialogs {

  private Dialogs() {}

  /** Returns {@link Context} which adopts our desired theme. */
  public static Context getDialogContext(Context context) {
    return new ContextThemeWrapper(context, R.style.Theme_AppCompat_DayNight_Dialog);
  }

  /** Returns {@link AlertDialog.Builder} which uses our desired theme. */
  public static AlertDialog.Builder getAlertDialogBuilder(Context context) {
    return new AlertDialog.Builder(getDialogContext(context));
  }

  /** Configures the {@link WindowManager.LayoutParams} for our dialogs. */
  public static void configureAndShowAttachedDialog(Dialog dialog, View windowTokenProvidingView) {
    LayoutParams layoutParams = dialog.getWindow().getAttributes();
    layoutParams.type = LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
    layoutParams.token = windowTokenProvidingView.getWindowToken();
    dialog.getWindow().setAttributes(layoutParams);
    dialog.show();
  }
}
