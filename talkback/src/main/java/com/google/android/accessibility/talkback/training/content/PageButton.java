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

package com.google.android.accessibility.talkback.training.content;

import static android.widget.Toast.LENGTH_LONG;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.training.TrainingIpcClient.ServiceData;
import java.util.function.Consumer;

/** A button with a text on training pages. */
public class PageButton extends PageContentConfig {

  /** Defines click listeners for buttons on training pages. */
  public enum PageButtonOnClickListener {
    LINK_TO_SETTINGS(PageButton::linkToSettings);

    private final Consumer<Context> consumer;

    PageButtonOnClickListener(Consumer<Context> consumer) {
      this.consumer = consumer;
    }

    public void onClick(Context context) {
      consumer.accept(context);
    }
  }

  @VisibleForTesting public static final String PACKAGE_NAME = "com.google.android.marvin.talkback";

  @VisibleForTesting
  public static final String SETTINGS_CLASS_NAME = TalkBackPreferencesActivity.class.getName();

  @StringRes private final int textResId;
  @Nullable private final PageButtonOnClickListener pageButtonOnClickListener;

  public PageButton(@StringRes int textResId) {
    this(textResId, /* pageButtonOnClickListener= */ null);
  }

  public PageButton(
      @StringRes int textResId, @Nullable PageButtonOnClickListener pageButtonOnClickListener) {
    this.textResId = textResId;
    this.pageButtonOnClickListener = pageButtonOnClickListener;
  }

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    final View view = inflater.inflate(R.layout.training_button, container, false);
    final Button button = view.findViewById(R.id.training_button);
    button.setText(textResId);
    if (pageButtonOnClickListener == null) {
      button.setOnClickListener(
          v ->
              Toast.makeText(
                      context,
                      context.getString(R.string.activated_view, context.getString(textResId)),
                      LENGTH_LONG)
                  .show());
    } else {
      button.setOnClickListener(v -> pageButtonOnClickListener.onClick(context));
    }
    return view;
  }

  /** Links to the TalkBack Settings page. */
  private static void linkToSettings(Context context) {
    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_MAIN);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    intent.setComponent(new ComponentName(PACKAGE_NAME, SETTINGS_CLASS_NAME));
    context.startActivity(intent);
  }

  @VisibleForTesting
  public PageButtonOnClickListener getClickListener() {
    return pageButtonOnClickListener;
  }
}
