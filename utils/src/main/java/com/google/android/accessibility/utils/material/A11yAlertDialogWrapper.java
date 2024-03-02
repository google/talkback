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
package com.google.android.accessibility.utils.material;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.utils.R;

/**
 * Alertdialog wrapper to hold the wrapper of android.app.AlertDialog, MaterialAlertDialog or
 * support.v7.app.AlertDialog. This is for the different platforms, including the Wear OS.
 */
public class A11yAlertDialogWrapper implements DialogInterface {
  /** Types of Alertdialog style. */
  public enum AlertDialogStyle {
    // Uses for androidx.appcompat.app.AlertDialog
    ALERT_DIALOG_CLASSIC,
    // Uses for material dialog
    ALERT_DIALOG_MATERIAL,
  }

  // Hold the one of dialogWrapper for Alertdialog, such as android.app.AlertDialog,
  // MaterialAlertDialog or support.v7.app.AlertDialog, for the platforms including the Wear OS.
  private final DialogWrapperInterface dialogWrapper;

  /**
   * Creates an A11yAlertDialogWrapper that holds a wrapper of MaterialAlertDialog or
   * support.v7.app.AlertDialog.
   *
   * @param v7AlertDialog the dialog is held by A11yAlertDialogWrapper.
   */
  A11yAlertDialogWrapper(AlertDialog v7AlertDialog) {
    dialogWrapper = new V7AlertDialogWrapper(v7AlertDialog);
  }

  /**
   * Creates an A11yAlertDialogWrapper that holds a wrapper of android.app.AlertDialog.
   *
   * @param appAlertDialog the dialog is held by A11yAlertDialogWrapper.
   */
  A11yAlertDialogWrapper(android.app.AlertDialog appAlertDialog) {
    dialogWrapper = new AppAlertDialogWrapper(appAlertDialog);
  }

  A11yAlertDialogWrapper(DialogWrapperInterface dialogWrapper) {
    this.dialogWrapper = dialogWrapper;
  }

  /**
   * Starts the dialog and display it on screen. The window is placed in the application layer and
   * opaque.
   */
  public void show() {
    dialogWrapper.show();
  }

  /** See {@link AlertDialog#getButton(int)} */
  public Button getButton(int whichButton) {
    return dialogWrapper.getButton(whichButton);
  }

  /** See {@link AlertDialog#isShowing()} */
  public boolean isShowing() {
    return dialogWrapper.isShowing();
  }

  /** See {@link AlertDialog#getWindow()} */
  @Nullable
  public Window getWindow() {
    return dialogWrapper.getWindow();
  }

  /** See {@link AlertDialog#cancel()} */
  @Override
  public void cancel() {
    dialogWrapper.cancel();
  }

  /** See {@link AlertDialog#dismiss()} */
  @Override
  public void dismiss() {
    dialogWrapper.dismiss();
  }

  /** See {@link AlertDialog#setOnDismissListener(OnDismissListener)} */
  public void setOnDismissListener(@Nullable OnDismissListener listener) {
    dialogWrapper.setOnDismissListener(listener);
  }

  /** See {@link AlertDialog#setCanceledOnTouchOutside(boolean)} */
  public void setCanceledOnTouchOutside(boolean cancel) {
    dialogWrapper.setCanceledOnTouchOutside(cancel);
  }

  /** Gets AlertDialog from dialog wrapper. */
  public Dialog getDialog() {
    return dialogWrapper.getDialog();
  }

  /**
   * An interface of alert dialog wrapper to hold one type of AlertDialog that uses the default
   * alert dialog theme.
   */
  public interface DialogWrapperInterface {
    /** See {@link AlertDialog#show()} */
    void show();

    /** See {@link AlertDialog#getButton(int)} */
    Button getButton(int whichButton);

    /** See {@link AlertDialog#isShowing()} */
    boolean isShowing();

    /** See {@link AlertDialog#getWindow()} */
    @Nullable
    Window getWindow();

    /** See {@link AlertDialog#cancel()} */
    void cancel();

    /** See {@link AlertDialog#dismiss()} */
    void dismiss();

    /** See {@link AlertDialog#setOnDismissListener(OnDismissListener)} */
    void setOnDismissListener(@Nullable OnDismissListener listener);

    /** See {@link AlertDialog#setCanceledOnTouchOutside(boolean)} */
    void setCanceledOnTouchOutside(boolean cancel);

    /** Gets AlertDialog */
    Dialog getDialog();
  }

  /** Alertdialog wrapper to hold android.app.AlertDialog. This is for the Wear OS. */
  private static class AppAlertDialogWrapper implements DialogWrapperInterface {
    // Hold the android.app.AlertDialog which is used in the Wear OS.
    private final android.app.AlertDialog appAlertDialog;

    AppAlertDialogWrapper(android.app.AlertDialog appAlertDialog) {
      this.appAlertDialog = appAlertDialog;
    }

    @Override
    public void show() {
      appAlertDialog.show();
    }

    @Override
    public Button getButton(int whichButton) {
      return appAlertDialog.getButton(whichButton);
    }

    @Override
    public boolean isShowing() {
      return appAlertDialog.isShowing();
    }

    @Override
    @Nullable
    public Window getWindow() {
      return appAlertDialog.getWindow();
    }

    @Override
    public void cancel() {
      appAlertDialog.cancel();
    }

    @Override
    public void dismiss() {
      appAlertDialog.dismiss();
    }

    @Override
    public void setOnDismissListener(@Nullable OnDismissListener listener) {
      appAlertDialog.setOnDismissListener(listener);
    }

    @Override
    public void setCanceledOnTouchOutside(boolean cancel) {
      appAlertDialog.setCanceledOnTouchOutside(cancel);
    }

    @Override
    public Dialog getDialog() {
      return appAlertDialog;
    }
  }

  /**
   * Alertdialog wrapper to hold MaterialAlertDialog or support.v7.app.AlertDialog. This is for the
   * different platforms except the Wear OS.
   */
  private static class V7AlertDialogWrapper implements DialogWrapperInterface {
    // Hold the Alertdialog, such as MaterialAlertDialog or support.v7.app.AlertDialog, for the most
    // of the platforms excpet the Wear OS.
    private final AlertDialog v7AlertDialog;

    V7AlertDialogWrapper(AlertDialog v7AlertDialog) {
      this.v7AlertDialog = v7AlertDialog;
    }

    @Override
    public void show() {
      v7AlertDialog.show();
    }

    @Override
    public Button getButton(int whichButton) {
      return v7AlertDialog.getButton(whichButton);
    }

    @Override
    public boolean isShowing() {
      return v7AlertDialog.isShowing();
    }

    @Override
    @Nullable
    public Window getWindow() {
      return v7AlertDialog.getWindow();
    }

    @Override
    public void cancel() {
      v7AlertDialog.cancel();
    }

    @Override
    public void dismiss() {
      v7AlertDialog.dismiss();
    }

    @Override
    public void setOnDismissListener(@Nullable OnDismissListener listener) {
      v7AlertDialog.setOnDismissListener(listener);
    }

    @Override
    public void setCanceledOnTouchOutside(boolean cancel) {
      v7AlertDialog.setCanceledOnTouchOutside(cancel);
    }

    @Override
    public Dialog getDialog() {
      return v7AlertDialog;
    }
  }

  /** A builder for an alert dialog that uses the default alert dialog theme. */
  public interface Builder {
    /**
     * Returns a {@link Context} with the appropriate theme for dialogs created by this Builder.
     * Applications should use this Context for obtaining LayoutInflaters for inflating views that
     * will be used in the resulting dialogs, as it will cause views to be inflated with the correct
     * theme.
     *
     * @return A Context for built Dialogs.
     */
    @NonNull
    Context getContext();

    /** See {@link AlertDialog.Builder#setTitle(int)} */
    A11yAlertDialogWrapper.Builder setTitle(@StringRes int resId);

    /** See {@link AlertDialog.Builder#setTitle(CharSequence)} */
    A11yAlertDialogWrapper.Builder setTitle(@NonNull CharSequence title);

    /** See {@link AlertDialog.Builder#setCustomTitle(View)} */
    A11yAlertDialogWrapper.Builder setCustomTitle(@NonNull View customTitleView);

    /** See {@link AlertDialog.Builder#setMessage(int)} */
    A11yAlertDialogWrapper.Builder setMessage(@StringRes int resId);

    /** See {@link AlertDialog.Builder#setMessage(CharSequence)} */
    A11yAlertDialogWrapper.Builder setMessage(@NonNull CharSequence message);

    /** See {@link AlertDialog.Builder#setIcon(int)} */
    A11yAlertDialogWrapper.Builder setIcon(@DrawableRes int resId);

    /** See {@link AlertDialog.Builder#setIcon(Drawable)} */
    A11yAlertDialogWrapper.Builder setIcon(@NonNull Drawable drawable);

    /** See {@link AlertDialog.Builder#setPositiveButton(int, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setPositiveButton(
        @StringRes int resId, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setPositiveButton(CharSequence, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setPositiveButton(CharSequence text, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setPositiveButtonIcon(Drawable)} */
    A11yAlertDialogWrapper.Builder setPositiveButtonIconId(@DrawableRes int buttonIconId);

    /** See {@link AlertDialog.Builder#setNegativeButton(int, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setNegativeButton(
        @StringRes int resId, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setNegativeButton(CharSequence, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setNegativeButton(CharSequence text, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setNegativeButtonIcon(Drawable)} */
    A11yAlertDialogWrapper.Builder setNegativeButtonIconId(@DrawableRes int buttonIconId);

    /** See {@link AlertDialog.Builder#setNeutralButton(int, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setNeutralButton(@StringRes int resId, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setNeutralButton(CharSequence, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setNeutralButton(CharSequence text, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setCancelable(boolean)} */
    A11yAlertDialogWrapper.Builder setCancelable(boolean cancelable);

    /** See {@link AlertDialog.Builder#setOnCancelListener(OnCancelListener)} */
    A11yAlertDialogWrapper.Builder setOnCancelListener(OnCancelListener listener);

    /** See {@link AlertDialog.Builder#setOnDismissListener(OnDismissListener)} */
    A11yAlertDialogWrapper.Builder setOnDismissListener(OnDismissListener listener);

    /** See {@link AlertDialog.Builder#setOnKeyListener(OnKeyListener)} */
    A11yAlertDialogWrapper.Builder setOnKeyListener(OnKeyListener listener);

    /** See {@link AlertDialog.Builder#setView(int)} */
    A11yAlertDialogWrapper.Builder setView(@LayoutRes int layoutResId);

    /** See {@link AlertDialog.Builder#setView(View)} */
    A11yAlertDialogWrapper.Builder setView(View view);

    /** See {@link AlertDialog.Builder#create()} */
    @NonNull
    A11yAlertDialogWrapper create();
  }

  /**
   * Creates {@link A11yAlertDialogWrapper.Builder} which holds {@link
   * com.google.android.material.dialog.MaterialAlertDialogBuilder} for the most of the platforms.
   *
   * <p>Exception is as follows:
   *
   * <ul>
   *   <li>Wear OS: Holds WearableLegacyAlertDialogBuilder with {@link R.style.A11yAlertDialogTheme}
   *       theme.
   *   <li>Automotive OS: Holds {@link com.android.car.ui.AlertDialogBuilder} with default theme.
   * </ul>
   *
   * @param context the current context
   * @return {@link A11yAlertDialogWrapper.Builder} for accessibility alert dialog
   */
  public static A11yAlertDialogWrapper.Builder materialDialogBuilder(@NonNull Context context) {
    return AccessibilitySuiteAlertDialogWrapperFactory.createA11yAlertDialogWrapperBuilder(
        context, AlertDialogStyle.ALERT_DIALOG_MATERIAL);
  }

  /**
   * Creates {@link A11yAlertDialogWrapper.Builder} which holds {@link
   * com.google.android.material.dialog.MaterialAlertDialogBuilder} for the most of the platforms on
   * the given {@code fm} fragment.
   *
   * <p>Exception is as follows:
   *
   * <ul>
   *   <li>Wear OS: Holds WearableAlertDialog.Builder wrapping {@link
   *       com.google.android.clockwork.common.wearable.wearmaterial.alertdialog.WearAlertDialog.Builder}
   *       with default theme if {@code fm} is provided; otherwise, WearableLegacyAlertDialogBuilder
   *       with {@link R.style.A11yAlertDialogTheme} theme.
   *   <li>Automotive OS: Holds {@link com.android.car.ui.AlertDialogBuilder} with default theme.
   * </ul>
   *
   * @param context the current context
   * @param fm the FragmentManager which this fragment will be added to
   * @return {@link A11yAlertDialogWrapper.Builder} for accessibility alert dialog
   */
  public static A11yAlertDialogWrapper.Builder materialDialogBuilder(
      @NonNull Context context, FragmentManager fm) {
    return AccessibilitySuiteAlertDialogWrapperFactory.createA11yAlertDialogWrapperBuilder(
        context, AlertDialogStyle.ALERT_DIALOG_MATERIAL, fm);
  }

  /**
   * Creates {@link A11yAlertDialogWrapper.Builder} which holds {@link AlertDialog.Builder} for the
   * most of the platforms.
   *
   * <p>Exception is as follows:
   *
   * <ul>
   *   <li>Wear OS: Holds WearLegacyBuilder with default theme.
   *   <li>Automotive OS: Holds {@link com.android.car.ui.AlertDialogBuilder} with default theme.
   * </ul>
   *
   * @param context the current context
   * @return {@link A11yAlertDialogWrapper.Builder} for accessibility alert dialog
   */
  public static A11yAlertDialogWrapper.Builder alertDialogBuilder(@NonNull Context context) {
    return AccessibilitySuiteAlertDialogWrapperFactory.createA11yAlertDialogWrapperBuilder(
        context, AlertDialogStyle.ALERT_DIALOG_CLASSIC);
  }

  /**
   * Creates {@link A11yAlertDialogWrapper.Builder} which holds {@link AlertDialog.Builder} for the
   * most of the platforms.
   *
   * <p>Exception is as follows:
   *
   * <ul>
   *   <li>Wear OS: Holds WearableAlertDialog.Builder wrapping {@link
   *       com.google.android.clockwork.common.wearable.wearmaterial.alertdialog.WearAlertDialog.Builder}
   *       with default theme if {@code fm} is provided; otherwise, WearableLegacyAlertDialogBuilder
   *       with {@link R.style.A11yAlertDialogTheme} theme.
   *   <li>Automotive OS: Holds {@link com.android.car.ui.AlertDialogBuilder} with default theme.
   * </ul>
   *
   * @param context the current context
   * @param fm the FragmentManager which this fragment will be added to
   * @return {@link A11yAlertDialogWrapper.Builder} for accessibility alert dialog
   */
  public static A11yAlertDialogWrapper.Builder alertDialogBuilder(
      @NonNull Context context, FragmentManager fm) {
    return AccessibilitySuiteAlertDialogWrapperFactory.createA11yAlertDialogWrapperBuilder(
        context, AlertDialogStyle.ALERT_DIALOG_CLASSIC, fm);
  }

  /**
   * Focuses the cancel button.
   *
   * @param alertDialog The {@link A11yAlertDialogWrapper} which focuses the cancel button.
   */
  public static void focusCancelButton(A11yAlertDialogWrapper alertDialog) {
    Button cancelButton = alertDialog.getButton(BUTTON_NEGATIVE);
    cancelButton.setFocusableInTouchMode(true);
    cancelButton.requestFocus();
  }
}
