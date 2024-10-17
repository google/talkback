/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.android.accessibility.talkback.preference.base;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.google.common.base.Splitter;
import java.util.List;

/** Customized Preference for bullet list format. */
public final class AccessibilityFooterPreference extends Preference {

  // The gap, in pixels, before and after the bullet point
  private static final int GAP_WIDTH = 30;

  public AccessibilityFooterPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public AccessibilityFooterPreference(Context context) {
    this(context, /* attrs= */ null);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);

    // Format the title to be bullet lists.
    // TODO: b/317271201 - Add utils to format bullet lists
    TextView title = holder.itemView.findViewById(android.R.id.title);
    String baseString = getTitle().toString();
    if (title == null || TextUtils.isEmpty(baseString)) {
      return;
    }
    SpannableString spannableString = new SpannableString(baseString);
    List<String> strings = Splitter.on("\n\n").splitToList(baseString);
    if (strings.size() != 2) {
      return;
    }
    Iterable<String> bulletStrings = Splitter.on('\n').split(strings.get(1));
    int spanBeginning = strings.get(0).length() + 2;

    spannableString.setSpan(
        new LeadingMarginSpan.Standard(GAP_WIDTH, GAP_WIDTH),
        spanBeginning,
        spannableString.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    for (String bulletString : bulletStrings) {
      spannableString.setSpan(
          new BulletSpan(GAP_WIDTH),
          spanBeginning,
          spanBeginning + bulletString.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      // Add 1 to skip new line character
      spanBeginning += bulletString.length() + 1;
    }
    title.setText(spannableString);
  }
}
