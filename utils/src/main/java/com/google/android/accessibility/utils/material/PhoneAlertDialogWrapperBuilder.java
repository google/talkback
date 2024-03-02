/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.accessibility.utils.material;

import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnKeyListener;
import android.graphics.drawable.Drawable;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** A builder for an androidx.appcompat.app.AlertDialog that uses the default alert dialog theme. */
class PhoneAlertDialogWrapperBuilder implements A11yAlertDialogWrapper.Builder {
  // Hold the Alertdialog builder, such as MaterialAlertDialog or support.v7.app.AlertDialog, for
  // the phone & TV platforms.
  private final AlertDialog.Builder v7Builder;

  PhoneAlertDialogWrapperBuilder(AlertDialog.Builder v7Builder) {
    this.v7Builder = v7Builder;
  }

  @Override
  @NonNull
  public Context getContext() {
    return v7Builder.getContext();
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setTitle(@StringRes int resId) {
    v7Builder.setTitle(resId);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setTitle(@NonNull CharSequence title) {
    v7Builder.setTitle(title);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setCustomTitle(@NonNull View customTitleView) {

    v7Builder.setCustomTitle(customTitleView);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setMessage(@StringRes int resId) {
    v7Builder.setMessage(resId);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setMessage(@NonNull CharSequence message) {
    v7Builder.setMessage(message);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setIcon(@DrawableRes int resId) {
    v7Builder.setIcon(resId);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setIcon(@NonNull Drawable drawable) {
    v7Builder.setIcon(drawable);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setPositiveButton(
      @StringRes int resId, OnClickListener listener) {
    v7Builder.setPositiveButton(resId, listener);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setPositiveButton(
      CharSequence text, OnClickListener listener) {
    v7Builder.setPositiveButton(text, listener);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setNegativeButton(
      @StringRes int resId, OnClickListener listener) {
    v7Builder.setNegativeButton(resId, listener);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setPositiveButtonIconId(int buttonIconId) {
    // Leaves this API to do nothing since only the wear version supports the icon button.
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setNegativeButton(
      CharSequence text, OnClickListener listener) {
    v7Builder.setNegativeButton(text, listener);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setNegativeButtonIconId(int buttonIconId) {
    // Leaves this API to do nothing since only the wear version supports the icon button.
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setNeutralButton(
      @StringRes int resId, OnClickListener listener) {
    v7Builder.setNeutralButton(resId, listener);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setNeutralButton(
      CharSequence text, OnClickListener listener) {
    v7Builder.setNeutralButton(text, listener);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setCancelable(boolean cancelable) {
    v7Builder.setCancelable(cancelable);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setOnCancelListener(OnCancelListener listener) {
    v7Builder.setOnCancelListener(listener);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setOnDismissListener(OnDismissListener listener) {
    v7Builder.setOnDismissListener(listener);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setOnKeyListener(OnKeyListener listener) {
    v7Builder.setOnKeyListener(listener);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public A11yAlertDialogWrapper.Builder setView(int layoutResId) {
    v7Builder.setView(layoutResId);
    return this;
  }

  @CanIgnoreReturnValue
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
}
