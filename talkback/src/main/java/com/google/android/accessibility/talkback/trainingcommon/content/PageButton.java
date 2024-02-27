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

package com.google.android.accessibility.talkback.trainingcommon.content;

import static android.widget.Toast.LENGTH_LONG;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.brailleime.BrailleImeUtils;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.TrainingFragment;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.Immutable;
import java.util.function.Consumer;

/** A button with a text on training pages. */
public class PageButton extends PageContentConfig {

  /** A callback to be invoked when a the page button is clicked */
  @Immutable
  public interface ButtonOnClickListener extends Consumer<Context> {}

  /** The common actions of the buttons on training pages. */
  @Immutable
  public enum PageButtonAction implements ButtonOnClickListener {
    OPEN_READING_MODE_PAGE(PageButton::openReadingModePage),
    OPEN_LOOKOUT_PAGE(PageButton::openLookoutPage),
    BRAILLE_TUTORIAL(PageButton::openBrailleTutorialForSpellCheck);

    private final ButtonOnClickListener onClickListener;

    PageButtonAction(ButtonOnClickListener onClickListener) {
      this.onClickListener = onClickListener;
    }

    @Override
    public void accept(Context context) {
      onClickListener.accept(context);
    }
  }

  @VisibleForTesting public static final String PACKAGE_NAME = "com.google.android.marvin.talkback";
  private static final String TAG = "PageButton";
  private static final String READING_MODE_PLAYSTORE_URL =
      "https://play.google.com/store/apps/details?id=com.google.android.accessibility.reader&hl=en_US&gl=US";
  private static final String LOOKOUT_PLAYSTORE_URL =
      "https://play.google.com/store/apps/details?id=com.google.android.apps.accessibility.reveal&referrer=utm_source%3Dgoogle%26utm_campaign%3Dtbtutorial%26anid%3Dadmob";

  @StringRes private final int textResId;
  @Nullable private final ButtonOnClickListener buttonOnClickListener;

  @Nullable private Button button;
  @Nullable private Message message;

  public PageButton(@StringRes int textResId) {
    this(textResId, /* buttonOnClickListener= */ null);
  }

  public PageButton(
      @StringRes int textResId, @Nullable ButtonOnClickListener buttonOnClickListener) {
    this.textResId = textResId;
    this.buttonOnClickListener = buttonOnClickListener;
  }

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    final View view = inflater.inflate(R.layout.training_button, container, false);
    final Button button = view.findViewById(R.id.training_button);
    button.setText(textResId);

    if (getMessage() == null) {
      if (buttonOnClickListener == null) {
        button.setOnClickListener(
            v ->
                Toast.makeText(
                        context,
                        context.getString(R.string.activated_view, context.getString(textResId)),
                        LENGTH_LONG)
                    .show());
      } else {
        button.setOnClickListener(v -> buttonOnClickListener.accept(context));
      }
    } else {
      // Non-null message will be set in TrainingFragment.addView().
      setButton(button);
    }
    return view;
  }

  /**
   * Sets a {@link Button} within the PageContent.
   *
   * <p>If the button needs to communicate with TalkBack by {@link #message}, a {@link
   * View.OnClickListener} of the button will be set in {@link TrainingFragment}.
   */
  private void setButton(@Nullable Button buttonView) {
    this.button = buttonView;
  }

  @Nullable
  public Button getButton() {
    return button;
  }

  /** Sets a {@link Message} which will be sent to TalkBack while the button be clicked. */
  public void setMessage(@Nullable Message message) {
    this.message = message;
  }

  @Nullable
  public Message getMessage() {
    return message;
  }

  private static void openReadingModePage(Context context) {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(READING_MODE_PLAYSTORE_URL));
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }

  private static void openLookoutPage(Context context) {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(LOOKOUT_PLAYSTORE_URL));
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }

  private static void openBrailleTutorialForSpellCheck(Context context) {
    Intent intent = BrailleImeUtils.getStartSpellCheckGestureCommandActivityIntent(context);
    if (intent == null) {
      LogUtils.e(TAG, "No intent to view braille tutorial for Spell Check.");
      return;
    }
    context.startActivity(intent);
  }

  @VisibleForTesting
  public ButtonOnClickListener getClickListener() {
    return buttonOnClickListener;
  }
}
