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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SeekBarPreference;
import com.google.android.accessibility.talkback.R;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Customized SeekBarPreference to match the ones in system settings. */
public class AccessibilitySeekBarPreference extends SeekBarPreference {

  public AccessibilitySeekBarPreference(@NonNull Context context) {
    super(context);
  }

  public AccessibilitySeekBarPreference(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);

    // Make talkback UI consistent with SeekBarPreference in system settings.
    final TextView titleView = (TextView) holder.findViewById(androidx.preference.R.id.title);
    final SeekBar seekbar = (SeekBar) holder.findViewById(androidx.preference.R.id.seekbar);
    if (titleView != null && seekbar != null) {
      final CharSequence title = getTitle();
      if (!TextUtils.isEmpty(title)) {
        seekbar.setContentDescription(title);
      }
      ViewCompat.setLabelFor(titleView, View.NO_ID);
    }

    holder.itemView.setClickable(false);
  }
}
