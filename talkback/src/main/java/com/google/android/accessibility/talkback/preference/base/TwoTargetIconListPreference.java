/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.android.accessibility.talkback.preference.base;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceViewHolder;

/** A custom list preference that provides inline icon. */
public class TwoTargetIconListPreference extends ListPreference {
  private TwoTargetPreferenceAction twoTargetAction;

  public TwoTargetIconListPreference(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init();
  }

  public TwoTargetIconListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  public TwoTargetIconListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public TwoTargetIconListPreference(Context context) {
    super(context);
    init();
  }

  @Override
  public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    twoTargetAction.onBindViewHolder(holder);
  }

  private void init() {
    twoTargetAction = new TwoTargetPreferenceAction(getContext(), this);
  }
}
