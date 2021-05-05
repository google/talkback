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

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.common.base.Ascii;
import java.util.List;

/** Multiple paragraphs of text. */
public class Text extends PageContentConfig {

  private static final int GAP_WIDTH = 50;
  private static final int BULLET_RADIUS = 8;

  @StringRes private final int textResId;
  @Nullable private final int[] textArgResIds;
  @StringRes private final int textWithActualGestureResId;
  @StringRes private final int actionKey;
  private final boolean hasBulletPoint;
  private final boolean isSubText;
  private final boolean isLink;

  public Text(@StringRes int textResId, int... textArgResIds) {
    this(
        textResId,
        UNKNOWN_RESOURCE_ID,
        UNKNOWN_RESOURCE_ID,
        /* hasBulletPoint= */ false,
        /* isSubText= */ false,
        /* isLink= */ false,
        textArgResIds);
  }

  public Text(@StringRes int textResId, boolean hasBulletPoint) {
    this(textResId, hasBulletPoint, false);
  }

  public Text(@StringRes int textResId, boolean hasBulletPoint, boolean isSubText) {
    this(
        textResId,
        UNKNOWN_RESOURCE_ID,
        UNKNOWN_RESOURCE_ID,
        hasBulletPoint,
        isSubText,
        /* isLink= */ false);
  }

  /**
   * The text with a gesture that should be replaced with actual gesture if user has customized it.
   * If no gesture is assigned, shows default text.
   */
  public Text(
      @StringRes int defaultTextResId,
      @StringRes int textWithActualGestureResId,
      @StringRes int actionKey,
      boolean hasBulletPoint) {
    this(
        defaultTextResId,
        textWithActualGestureResId,
        actionKey,
        hasBulletPoint,
        /* isSubText= */ false,
        /* isLink= */ false);
  }

  // TODO  Adds a builder to create different type of Text.
  /**
   * The text with a gesture that should be replaced with actual gesture if user has customized it.
   * If no gesture is assigned, shows default text.
   *
   * @param hasBulletPoint if the text has a bullet point
   * @param isLink if the text needs an empty {@link URLSpan} or not
   */
  public Text(
      @StringRes int defaultTextResId,
      @StringRes int textWithActualGestureResId,
      @StringRes int actionKey,
      boolean hasBulletPoint,
      boolean isSubText,
      boolean isLink,
      @Nullable int... textArgResIds) {
    this.textResId = defaultTextResId;
    this.textArgResIds = textArgResIds;
    this.textWithActualGestureResId = textWithActualGestureResId;
    this.actionKey = actionKey;
    this.hasBulletPoint = hasBulletPoint;
    this.isSubText = isSubText;
    this.isLink = isLink;
  }

  @Override
  public View createView(LayoutInflater inflater, ViewGroup container, Context context) {
    final View view = inflater.inflate(R.layout.training_text, container, false);
    final TextView textView = view.findViewById(R.id.training_text);
    String text = getText(context);

    if (isSubText) {
      LinearLayout.LayoutParams layoutParams =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      layoutParams.setMargins(0, 0, 0, 0);
      textView.setLayoutParams(layoutParams);
    }

    if (!hasBulletPoint) {
      textView.setText(isLink ? getSpannedText(context, text) : text);
      return view;
    }

    SpannableString spannableString = new SpannableString(text);
    if (FeatureSupport.customBulletRadius()) {
      spannableString.setSpan(
          new BulletSpan(GAP_WIDTH, context.getColor(R.color.training_text_color), BULLET_RADIUS),
          0,
          spannableString.length(),
          Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    } else {
      spannableString.setSpan(
          new BulletSpan(GAP_WIDTH, context.getColor(R.color.training_text_color)),
          0,
          spannableString.length(),
          Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    textView.setText(spannableString);
    return view;
  }

  private String getText(Context context) {
    if (textArgResIds == null || textArgResIds.length < 1) {
      return getTextWithRealGesture(context);
    }

    // Returns a text with format arguments.
    Object[] textArgs = new Object[textArgResIds.length];
    for (int i = 0; i < textArgResIds.length; i++) {
      textArgs[i] = Ascii.toLowerCase(context.getString(textArgResIds[i]));
    }
    return context.getString(textResId, textArgs);
  }

  /**
   * Returns a text includes a gesture that is replaced with an actual gesture. If no gesture is
   * assigned, returns the default text.
   */
  private String getTextWithRealGesture(Context context) {
    if (actionKey == UNKNOWN_RESOURCE_ID || textWithActualGestureResId == UNKNOWN_RESOURCE_ID) {
      return context.getString(textResId);
    }

    // Finds actual gesture.
    GestureShortcutMapping mapping = new GestureShortcutMapping(context);
    List<String> gestures = mapping.getGestureTextsFromActionKeys(context.getString(actionKey));
    return gestures.isEmpty()
        ? context.getString(textResId)
        : context.getString(textWithActualGestureResId, Ascii.toLowerCase(gestures.get(0)));
  }

  /** Adds a @link ToastURLSpan}. */
  private static CharSequence getSpannedText(Context context, String text) {
    SpannableString textWithLinks = new SpannableString(text);
    textWithLinks.setSpan(
        new ToastURLSpan(context, context.getString(R.string.activated_view, text)),
        0,
        text.length(),
        Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    return textWithLinks;
  }

  /** Shows a Toast when the span is clicked. */
  public static class ToastURLSpan extends URLSpan {

    private final Context context;
    private final String text;

    public ToastURLSpan(Context context, String text) {
      super("");
      this.context = context;
      this.text = text;
    }

    @Override
    public void onClick(View view) {
      super.onClick(view);
      Toast.makeText(context, text, LENGTH_LONG).show();
    }
  }
}
