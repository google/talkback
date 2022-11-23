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
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.ListAdapter;
import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Alertdialog wrapper to hold the wrapper of android.app.AlertDialog, MaterialAlertDialog or
 * support.v7.app.AlertDialog. This is for the different platforms, including the Wear OS.
 */
public class A11yAlertDialogWrapper implements DialogInterface {

  // Hold the one of dialogWrapper for Alertdialog, such as android.app.AlertDialog,
  // MaterialAlertDialog or support.v7.app.AlertDialog, for the platforms including the Wear OS.
  private final DialogWrapperInterface dialogWrapper;

  /**
   * Creates an A11yAlertDialogWrapper that holds a wrapper of MaterialAlertDialog or
   * support.v7.app.AlertDialog.
   *
   * @param v7AlertDialog the dialog is held by A11yAlertDialogWrapper.
   */
  private A11yAlertDialogWrapper(AlertDialog v7AlertDialog) {
    dialogWrapper = new V7AlertDialogWrapper(v7AlertDialog);
  }

  /**
   * Creates an A11yAlertDialogWrapper that holds a wrapper of android.app.AlertDialog.
   *
   * @param appAlertDialog the dialog is held by A11yAlertDialogWrapper.
   */
  private A11yAlertDialogWrapper(android.app.AlertDialog appAlertDialog) {
    dialogWrapper = new AppAlertDialogWrapper(appAlertDialog);
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

  /**
   * An interface of alert dialog wrapper to hold one type of AlertDialog that uses the default
   * alert dialog theme.
   */
  private interface DialogWrapperInterface {
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

    /** See {@link AlertDialog.Builder#setNegativeButton(int, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setNegativeButton(
        @StringRes int resId, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setNegativeButton(CharSequence, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setNegativeButton(CharSequence text, OnClickListener listener);

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

    /** See {@link AlertDialog.Builder#setItems(int, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setItems(@ArrayRes int itemsResId, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setItems(CharSequence[], OnClickListener)} */
    A11yAlertDialogWrapper.Builder setItems(CharSequence[] itemTitles, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setAdapter(ListAdapter, OnClickListener)} */
    A11yAlertDialogWrapper.Builder setAdapter(ListAdapter listAdapter, OnClickListener listener);

    /** See {@link AlertDialog.Builder#setOnItemSelectedListener(OnItemSelectedListener)} */
    A11yAlertDialogWrapper.Builder setOnItemSelectedListener(OnItemSelectedListener listener);

    /** See {@link AlertDialog.Builder#setView(int)} */
    A11yAlertDialogWrapper.Builder setView(int layoutResId);

    /** See {@link AlertDialog.Builder#setView(View)} */
    A11yAlertDialogWrapper.Builder setView(View view);

    /** See {@link AlertDialog.Builder#create()} */
    @NonNull
    A11yAlertDialogWrapper create();

    /** See {@link AlertDialog.Builder#show()} */
    A11yAlertDialogWrapper show();
  }

  /** A builder for an android.app.AlertDialog that uses the default alert dialog theme. */
  private static class AppBuilder implements Builder {
    // Hold the Alertdialog builder which is used in the Wear OS.
    private final android.app.AlertDialog.Builder appBuilder;

    AppBuilder(android.app.AlertDialog.Builder appBuilder) {
      this.appBuilder = appBuilder;
    }

    @Override
    @NonNull
    public Context getContext() {
      return appBuilder.getContext();
    }

    @Override
    public A11yAlertDialogWrapper.Builder setTitle(@StringRes int resId) {
      appBuilder.setTitle(resId);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setTitle(@NonNull CharSequence title) {
      appBuilder.setTitle(title);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setCustomTitle(@NonNull View customTitleView) {
      appBuilder.setCustomTitle(customTitleView);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setMessage(@StringRes int resId) {
      appBuilder.setMessage(resId);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setMessage(@NonNull CharSequence message) {
      appBuilder.setMessage(message);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setIcon(@DrawableRes int resId) {
      appBuilder.setIcon(resId);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setIcon(@NonNull Drawable drawable) {
      appBuilder.setIcon(drawable);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setPositiveButton(
        @StringRes int resId, OnClickListener listener) {
      appBuilder.setPositiveButton(resId, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setPositiveButton(
        CharSequence text, OnClickListener listener) {
      appBuilder.setPositiveButton(text, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setNegativeButton(
        @StringRes int resId, OnClickListener listener) {
      appBuilder.setNegativeButton(resId, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setNegativeButton(
        CharSequence text, OnClickListener listener) {
      appBuilder.setNegativeButton(text, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setNeutralButton(
        @StringRes int resId, OnClickListener listener) {
      appBuilder.setNeutralButton(resId, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setNeutralButton(
        CharSequence text, OnClickListener listener) {
      appBuilder.setNeutralButton(text, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setCancelable(boolean cancelable) {
      appBuilder.setCancelable(cancelable);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setOnCancelListener(OnCancelListener listener) {
      appBuilder.setOnCancelListener(listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setOnDismissListener(OnDismissListener listener) {
      appBuilder.setOnDismissListener(listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setOnKeyListener(OnKeyListener listener) {
      appBuilder.setOnKeyListener(listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setItems(
        @ArrayRes int itemsResId, OnClickListener listener) {
      appBuilder.setItems(itemsResId, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setItems(
        CharSequence[] itemTitles, OnClickListener listener) {
      appBuilder.setItems(itemTitles, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setAdapter(
        ListAdapter listAdapter, OnClickListener listener) {
      appBuilder.setAdapter(listAdapter, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setOnItemSelectedListener(
        OnItemSelectedListener listener) {
      appBuilder.setOnItemSelectedListener(listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setView(int layoutResId) {
      appBuilder.setView(layoutResId);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setView(View view) {
      appBuilder.setView(view);
      return this;
    }

    @Override
    @NonNull
    public A11yAlertDialogWrapper create() {
      android.app.AlertDialog alertdialog = appBuilder.create();
      return new A11yAlertDialogWrapper(alertdialog);
    }

    @Override
    public A11yAlertDialogWrapper show() {
      android.app.AlertDialog alertdialog = appBuilder.show();
      return new A11yAlertDialogWrapper(alertdialog);
    }
  }

  /**
   * A builder for an androidx.appcompat.app.AlertDialog that uses the default alert dialog theme.
   */
  private static class V7Builder implements Builder {
    // Hold the Alertdialog builder, such as MaterialAlertDialog or support.v7.app.AlertDialog, for
    // the most of the platforms excpet the Wear OS.
    private final AlertDialog.Builder v7Builder;

    V7Builder(AlertDialog.Builder v7Builder) {
      this.v7Builder = v7Builder;
    }

    @Override
    @NonNull
    public Context getContext() {
      return v7Builder.getContext();
    }

    @Override
    public A11yAlertDialogWrapper.Builder setTitle(@StringRes int resId) {
      v7Builder.setTitle(resId);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setTitle(@NonNull CharSequence title) {
      v7Builder.setTitle(title);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setCustomTitle(@NonNull View customTitleView) {

      v7Builder.setCustomTitle(customTitleView);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setMessage(@StringRes int resId) {
      v7Builder.setMessage(resId);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setMessage(@NonNull CharSequence message) {
      v7Builder.setMessage(message);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setIcon(@DrawableRes int resId) {
      v7Builder.setIcon(resId);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setIcon(@NonNull Drawable drawable) {
      v7Builder.setIcon(drawable);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setPositiveButton(
        @StringRes int resId, OnClickListener listener) {
      v7Builder.setPositiveButton(resId, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setPositiveButton(
        CharSequence text, OnClickListener listener) {
      v7Builder.setPositiveButton(text, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setNegativeButton(
        @StringRes int resId, OnClickListener listener) {
      v7Builder.setNegativeButton(resId, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setNegativeButton(
        CharSequence text, OnClickListener listener) {
      v7Builder.setNegativeButton(text, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setNeutralButton(
        @StringRes int resId, OnClickListener listener) {
      v7Builder.setNeutralButton(resId, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setNeutralButton(
        CharSequence text, OnClickListener listener) {
      v7Builder.setNeutralButton(text, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setCancelable(boolean cancelable) {
      v7Builder.setCancelable(cancelable);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setOnCancelListener(OnCancelListener listener) {
      v7Builder.setOnCancelListener(listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setOnDismissListener(OnDismissListener listener) {
      v7Builder.setOnDismissListener(listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setOnKeyListener(OnKeyListener listener) {
      v7Builder.setOnKeyListener(listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setItems(
        @ArrayRes int itemsResId, OnClickListener listener) {
      v7Builder.setItems(itemsResId, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setItems(
        CharSequence[] itemTitles, OnClickListener listener) {
      v7Builder.setItems(itemTitles, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setAdapter(
        ListAdapter listAdapter, OnClickListener listener) {
      v7Builder.setAdapter(listAdapter, listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setOnItemSelectedListener(
        OnItemSelectedListener listener) {
      v7Builder.setOnItemSelectedListener(listener);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setView(int layoutResId) {
      v7Builder.setView(layoutResId);
      return this;
    }

    @Override
    public A11yAlertDialogWrapper.Builder setView(View view) {
      v7Builder.setView(view);
      return this;
    }

    @Override
    @NonNull
    public A11yAlertDialogWrapper create() {
      AlertDialog alertdialog = v7Builder.create();
      return new A11yAlertDialogWrapper(alertdialog);
    }

    @Override
    public A11yAlertDialogWrapper show() {
      AlertDialog alertdialog = v7Builder.show();
      return new A11yAlertDialogWrapper(alertdialog);
    }
  }

  /**
   * A builder for an alert dialog that creates {@link AlertDialog.Builder} or {@link
   * com.google.android.material.dialog.MaterialAlertDialogBuilder} by the version of Android API
   * for the most of the platforms except the Wear OS. It creates {@link
   * android.app.AlertDialog.Builder} for the Wear OS.
   */
  private static class MaterialDialogBuilder {
    private final Context context;

    /**
     * Creates a builder,{@link AlertDialog} or {@link
     * com.google.android.material.dialog.MaterialAlertDialogBuilder}.
     *
     * @param context the parent context
     */
    MaterialDialogBuilder(@NonNull Context context) {
      this.context = context;
    }

    /**
     * Creates a builder,{@link AlertDialog} or {@link
     * com.google.android.material.dialog.MaterialAlertDialogBuilder}.
     *
     * @return A11yAlertDialogWrapper.Builder
     */
    A11yAlertDialogWrapper.Builder create() {
      // Creates android.app.AlertDialog.Builder if this is the Wear OS. Otherwise, creates
      // androidx.appcompat.app.AlertDialog.Builder before API 31 or
      // com.google.android.material.dialog.MaterialAlertDialogBuilder from API 31.
      if (FeatureSupport.isWatch(context)) {
        return new A11yAlertDialogWrapper.AppBuilder(AlertDialogUtils.builder(context));
      } else {
        return new A11yAlertDialogWrapper.V7Builder(
            MaterialComponentUtils.alertDialogBuilder(context));
      }
    }
  }

  /**
   * A builder for an alert dialog that creates {@link AlertDialog} for the most of the platforms
   * excpet the Wear OS or {@link android.app.AlertDialog} for the Wear OS. This builder is used for
   * these dialogs at the settings.
   */
  private static class AlertDialogBuilder {
    private final Context context;
    /**
     * Creates a builder of {@link AlertDialog} or {@link android.app.AlertDialog}.
     *
     * @param context the parent context
     */
    public AlertDialogBuilder(@NonNull Context context) {
      this.context = context;
    }

    /**
     * Creates a builder,{@link AlertDialog.Builder} or {@link android.app.AlertDialog}.
     *
     * @return A11yAlertDialogWrapper.Builder
     */
    A11yAlertDialogWrapper.Builder create() {
      // Creates android.app.AlertDialog.Builder if this is the Wear OS. Otherwise, creates
      // androidx.appcompat.app.AlertDialog.Builder for the dialogs at the settings.
      if (FeatureSupport.isWatch(context)) {
        return new A11yAlertDialogWrapper.AppBuilder(AlertDialogUtils.builder(context));
      } else {
        return new A11yAlertDialogWrapper.V7Builder(AlertDialogUtils.v7Builder(context));
      }
    }
  }

  /**
   * Creates {@link A11yAlertDialogWrapper.Builder} which holds {@link
   * com.google.android.material.dialog.MaterialAlertDialogBuilder} for the most of the platforms
   * except the Wear OS or {@link android.app.AlertDialog.Builder} for the Wear OS.
   *
   * @param context The current context
   * @return {@link A11yAlertDialogWrapper.Builder} returns A11yAlertDialogWrapper.Builder
   */
  public static A11yAlertDialogWrapper.Builder materialDialogBuilder(@NonNull Context context) {
    return new A11yAlertDialogWrapper.MaterialDialogBuilder(context).create();
  }

  /**
   * Creates {@link A11yAlertDialogWrapper.Builder} which holds {@link AlertDialog.Builder}.
   *
   * @param context The current context
   * @return {@link A11yAlertDialogWrapper.Builder} returns A11yAlertDialogWrapper.Builder
   */
  public static A11yAlertDialogWrapper.Builder alertDialogBuilder(@NonNull Context context) {
    return new A11yAlertDialogWrapper.AlertDialogBuilder(context).create();
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
