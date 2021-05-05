/*
 * Copyright 2020 Google Inc.
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

package com.google.android.accessibility.brailleime.dialog;

import android.app.Dialog;
import android.view.View;
import com.google.android.accessibility.brailleime.Dialogs;

/**
 * Abstract class that provides the basic functionality for a dialog which attached on specific
 * View.
 */
public abstract class ViewAttachedDialog {
  protected View viewToAttach;
  private Dialog dialog;

  /** Shows the dialog attach on the specific View. */
  public void show(View viewToAttach) {
    this.viewToAttach = viewToAttach;
    dialog = makeDialog();
    Dialogs.configureAndShowAttachedDialog(dialog, viewToAttach);
  }

  /** Gets the dialog is showing or not. */
  public boolean isShowing() {
    return dialog != null && dialog.isShowing();
  }

  /** Dismisses the dialog. */
  public void dismiss() {
    if (dialog != null) {
      dialog.dismiss();
    }
  }

  protected abstract Dialog makeDialog();
}
