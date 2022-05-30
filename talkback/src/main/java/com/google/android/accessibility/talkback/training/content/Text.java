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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BulletSpan;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.training.TrainingIpcClient.ServiceData;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;

/** Multiple paragraphs of text. */
public class Text extends PageContentConfig {

  private static final int GAP_WIDTH = 50;
  private static final int BULLET_RADIUS = 8;

  private final Paragraph[] paragraphs;

  public Text(Paragraph... paragraphs) {
    this.paragraphs = paragraphs;
  }

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    final View view = inflater.inflate(R.layout.training_text, container, false);
    final TextView textView = view.findViewById(R.id.training_text);
    boolean isSubText = false;
    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
    for (Paragraph paragraph : paragraphs) {
      if (paragraph.subText()) {
        isSubText = true;
      }

      if (!TextUtils.isEmpty(spannableStringBuilder)) {
        spannableStringBuilder.append("\n\n");
      }
      spannableStringBuilder.append(getText(context, paragraph, data));
    }

    if (isSubText) {
      LinearLayout.LayoutParams layoutParams =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      layoutParams.setMargins(0, 0, 0, 0);
      textView.setLayoutParams(layoutParams);
    }

    textView.setText(spannableStringBuilder);
    return view;
  }

  private SpannableString getText(Context context, Paragraph paragraph, ServiceData data) {
    String text;
    if (paragraph.textArgResIds() == null || paragraph.textArgResIds().size() < 1) {
      text = getTextWithRealGesture(context, paragraph, data);
    } else {
      // Returns a text with format arguments.
      Object[] textArgs = new Object[paragraph.textArgResIds().size()];
      for (int i = 0; i < paragraph.textArgResIds().size(); i++) {
        textArgs[i] = Ascii.toLowerCase(context.getString(paragraph.textArgResIds().get(i)));
      }
      text = context.getString(paragraph.textResId(), textArgs);
    }

    SpannableString spannableString = new SpannableString(text);
    if (paragraph.link()) {
      setURLSpan(context, spannableString, text);
    }

    if (paragraph.bulletPoint()) {
      setBulletSpan(context, spannableString, text);
    }

    return spannableString;
  }

  /**
   * Returns a text includes a gesture that is replaced with an actual gesture. If no gesture is
   * assigned, returns the default text.
   */
  private String getTextWithRealGesture(Context context, Paragraph paragraph, ServiceData data) {
    if (paragraph.actionKey() == UNKNOWN_RESOURCE_ID
        || paragraph.textWithActualGestureResId() == UNKNOWN_RESOURCE_ID) {
      return context.getString(paragraph.textResId());
    }

    // Finds actual gesture.
    String gesture = data.getGestureFromActionKey(paragraph.actionKey());
    return gesture == null
        ? context.getString(paragraph.textResId())
        : context.getString(paragraph.textWithActualGestureResId(), Ascii.toLowerCase(gesture));
  }

  /** Adds a @link ToastURLSpan}. */
  private static SpannableString setURLSpan(
      Context context, SpannableString spannableString, String text) {
    spannableString.setSpan(
        new ToastURLSpan(context, context.getString(R.string.activated_view, text)),
        0,
        text.length(),
        Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    return spannableString;
  }

  private static SpannableString setBulletSpan(
      Context context, SpannableString spannableString, String text) {
    if (FeatureSupport.customBulletRadius()) {
      spannableString.setSpan(
          new BulletSpan(GAP_WIDTH, context.getColor(R.color.training_text_color), BULLET_RADIUS),
          0,
          1,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    } else {
      spannableString.setSpan(
          new BulletSpan(GAP_WIDTH, context.getColor(R.color.training_text_color)),
          0,
          1,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return spannableString;
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

  /** The information of a paragraph in TextView, including style and data. */
  @AutoValue
  public abstract static class Paragraph {
    @StringRes
    public abstract int textResId();

    public abstract ImmutableList<Integer> textArgResIds();

    @StringRes
    public abstract int textWithActualGestureResId();

    @StringRes
    public abstract int actionKey();

    /** Return true, if the text has a bullet point. */
    public abstract boolean bulletPoint();

    /** Return true, if the text is a subtext whose size is smaller than others. */
    public abstract boolean subText();

    /** Return true, if the text needs a {@link ToastURLSpan}. */
    public abstract boolean link();

    public static Builder builder(@StringRes int textResId) {
      return new AutoValue_Text_Paragraph.Builder()
          .setTextResId(textResId)
          .setTextArgResIds(ImmutableList.of())
          .setTextWithActualGestureResId(UNKNOWN_RESOURCE_ID)
          .setActionKey(UNKNOWN_RESOURCE_ID)
          .setBulletPoint(false)
          .setSubText(false)
          .setLink(false);
    }

    /** Builder for a paragraph in TextView. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setTextResId(@StringRes int textResId);

      public abstract Builder setTextArgResIds(ImmutableList<Integer> textArgResIds);

      public abstract Builder setTextWithActualGestureResId(
          @StringRes int textWithActualGestureResId);

      public abstract Builder setActionKey(@StringRes int actionKey);

      public abstract Builder setBulletPoint(boolean hasBulletPoint);

      public abstract Builder setSubText(boolean isSubText);

      public abstract Builder setLink(boolean isLink);

      abstract Paragraph autoBuild();

      public Paragraph build() {
        return autoBuild();
      }
    }
  }
}
