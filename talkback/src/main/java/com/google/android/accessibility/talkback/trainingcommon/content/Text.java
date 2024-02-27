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
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageContentPredicate;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;
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
      setURLSpan(context, spannableString, text, paragraph.urlLink());
    }

    if (paragraph.bulletPoint()) {
      setBulletSpan(context, spannableString);
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
      return getParagraphString(paragraph, context);
    }

    // Finds actual gesture.
    String gesture = data.getGestureFromActionKey(paragraph.actionKey());
    return gesture == null
        ? (paragraph.defaultGestureResId() == UNKNOWN_RESOURCE_ID
            ? getParagraphString(paragraph, context)
            : context.getString(
                paragraph.textWithActualGestureResId(),
                Ascii.toLowerCase(context.getString(paragraph.defaultGestureResId()))))
        : context.getString(paragraph.textWithActualGestureResId(), Ascii.toLowerCase(gesture));
  }

  private static String getParagraphString(Paragraph paragraph, Context context) {
    String textString = paragraph.textString();
    return textString != null ? textString : context.getString(paragraph.textResId());
  }

  /**
   * Sets SpannableString with a {@link TextURLSpan} and shows a new activity by link when clicks.
   * If urlResId is INVALID_URL_RESID, it shows a toast when clicks.
   *
   * @param context The current context
   * @param spannableString The SpannableString which is set up with URLSpan
   * @param urlText The text of URL
   * @param urlLink The link of URL
   */
  private static void setURLSpan(
      Context context, SpannableString spannableString, String urlText, String urlLink) {

    spannableString.setSpan(
        new TextURLSpan(context, urlText, urlLink),
        /* start= */ 0,
        urlText.length(),
        Spanned.SPAN_INCLUSIVE_INCLUSIVE);
  }

  /**
   * Sets SpannableString with a {@link BulletSpan}.
   *
   * @param context The current context
   * @param spannableString The SpannableString which is set up with URLSpan
   */
  private static void setBulletSpan(Context context, SpannableString spannableString) {
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
  }

  @VisibleForTesting
  public ImmutableList<Paragraph> getParagraphs() {
    return ImmutableList.copyOf(paragraphs);
  }

  /** A URL span to use for a link of a text. If there is no url link, it will show a toast. */
  private static class TextURLSpan extends URLSpan {

    private final Context context;
    private final String urlText;

    TextURLSpan(Context context, String urlText, String urlLink) {
      super(urlLink);
      this.context = context;
      this.urlText = urlText;
    }

    @Override
    public void onClick(View view) {
      String urlLink = super.getURL();
      if (TextUtils.isEmpty(urlLink)) {
        super.onClick(view);
        Toast.makeText(context, context.getString(R.string.activated_view, urlText), LENGTH_LONG)
            .show();
      } else {
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlLink)));
      }
    }
  }

  /** The information of a paragraph in TextView, including style and data. */
  @AutoValue
  public abstract static class Paragraph {
    private static final String INVALID_URL_LINK = "";

    /** Can be overridden by {@link #textString()}. */
    @StringRes
    public abstract int textResId();

    /** Has precedence over {@link #textResId()}. */
    @Nullable
    public abstract String textString();

    public abstract ImmutableList<Integer> textArgResIds();

    @StringRes
    public abstract int textWithActualGestureResId();

    @StringRes
    public abstract int actionKey();

    @StringRes
    public abstract int defaultGestureResId();

    /** Return true, if the text has a bullet point. */
    public abstract boolean bulletPoint();

    /** Return true, if the text is a subtext whose size is smaller than others. */
    public abstract boolean subText();

    /** Return true, if the text needs a {@link TextURLSpan}. */
    public abstract boolean link();

    public abstract String urlLink();

    public static Builder builder(@StringRes int textResId) {
      return builder().setTextResId(textResId);
    }

    public static Builder builder(String textString) {
      return builder().setTextString(textString);
    }

    private static Builder builder() {
      return new AutoValue_Text_Paragraph.Builder()
          .setTextResId(UNKNOWN_RESOURCE_ID)
          .setTextString(null)
          .setTextArgResIds(ImmutableList.of())
          .setTextWithActualGestureResId(UNKNOWN_RESOURCE_ID)
          .setActionKey(UNKNOWN_RESOURCE_ID)
          .setDefaultGestureResId(UNKNOWN_RESOURCE_ID)
          .setBulletPoint(false)
          .setSubText(false)
          .setLink(false)
          .setUrlLink(INVALID_URL_LINK);
    }

    /** Builder for a paragraph in TextView. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setTextResId(@StringRes int textResId);

      public abstract Builder setTextString(@Nullable String textString);

      public abstract Builder setTextArgResIds(ImmutableList<Integer> textArgResIds);

      public abstract Builder setTextWithActualGestureResId(
          @StringRes int textWithActualGestureResId);

      public abstract Builder setActionKey(@StringRes int actionKey);

      public abstract Builder setDefaultGestureResId(@StringRes int defaultGestureResId);

      public abstract Builder setBulletPoint(boolean hasBulletPoint);

      public abstract Builder setSubText(boolean isSubText);

      public abstract Builder setLink(boolean isLink);

      public abstract Builder setUrlLink(String urlLink);

      abstract Paragraph autoBuild();

      public Paragraph build() {
        return autoBuild();
      }
    }
  }

  /** The parameters to create a {@link Text} with actual gesture and predicate. */
  public static class TextWithActualGestureParameter {
    public final @StringRes int textWithActualGestureResId;
    public final int actionKey;
    public final @StringRes int defaultGestureResId;
    public final @Nullable PageContentPredicate predicate;

    private TextWithActualGestureParameter(
        @StringRes int textWithActualGestureResId,
        int actionKey,
        @StringRes int defaultGestureResId,
        @Nullable PageContentPredicate predicate) {
      this.textWithActualGestureResId = textWithActualGestureResId;
      this.actionKey = actionKey;
      this.defaultGestureResId = defaultGestureResId;
      this.predicate = predicate;
    }

    public static TextWithActualGestureParameter create(
        @StringRes int textWithActualGestureResId,
        int actionKey,
        @StringRes int defaultGestureResId,
        @Nullable PageContentPredicate predicate) {
      return new TextWithActualGestureParameter(
          textWithActualGestureResId, actionKey, defaultGestureResId, predicate);
    }
  }
}
