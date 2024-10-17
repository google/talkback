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

import static com.google.android.accessibility.talkback.keyboard.KeyComboManager.getModifier;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;

/** Performs actions in the two target preference. */
public class TwoTargetPreferenceAction {
  private final Context context;
  private final Preference preference;

  public TwoTargetPreferenceAction(Context context, Preference preference) {
    this.context = context;
    this.preference = preference;
    preference.setLayoutResource(R.layout.preference_two_target);
    preference.setWidgetLayoutResource(R.layout.keyboard_shortcut_settings_preference_icon);
  }

  /** Sets the contents of the view holder's views. */
  public void onBindViewHolder(PreferenceViewHolder holder) {
    final View divider = holder.findViewById(R.id.divider);
    final View widgetFrame = holder.findViewById(android.R.id.widget_frame);
    final boolean shouldHideSecondTarget = shouldHideSecondTarget();
    if (divider != null) {
      divider.setVisibility(shouldHideSecondTarget ? View.GONE : View.VISIBLE);
    }
    if (widgetFrame != null) {
      widgetFrame.setVisibility(shouldHideSecondTarget ? View.GONE : View.VISIBLE);
    }

    final LinearLayout mainFrame = holder.itemView.findViewById(R.id.main_frame);
    if (mainFrame != null) {
      mainFrame.setOnClickListener(view -> holder.itemView.performClick());
    }
    widgetFrame.setOnClickListener(v -> showResetKeyboardShortcutDialog());
  }

  private boolean shouldHideSecondTarget() {
    int modifier = getKeyComboManager(context).getKeyComboModel().getTriggerModifier();
    Long keyCombo =
        getKeyComboManager(context)
            .getKeyComboModel()
            .getKeyComboCodeMap()
            .get(preference.getKey());
    boolean isSearchKey = modifier == KeyEvent.META_META_ON;
    if (keyCombo == null) {
      return !isSearchKey;
    }
    // For case such as Alt + Search + Left arrow, system action BACK will take effect so for those
    // keycombos has Search key, we also show warning.
    boolean hasSearchKey = (getModifier(keyCombo) & KeyEvent.META_META_ON) == KeyEvent.META_META_ON;
    return !(isSearchKey || hasSearchKey);
  }

  private static KeyComboManager getKeyComboManager(Context context) {
    KeyComboManager keyComboManager;
    if (TalkBackService.getInstance() != null) {
      keyComboManager = TalkBackService.getInstance().getKeyComboManager();
    } else {
      keyComboManager = KeyComboManager.create(context);
    }

    return keyComboManager;
  }

  private void showResetKeyboardShortcutDialog() {
    MaterialComponentUtils.alertDialogBuilder(context)
        .setTitle(R.string.keycombo_warning_dialog_title)
        .setMessage(R.string.keycombo_warning_dialog_message)
        .setPositiveButton(R.string.keycombo_warning_dialog_close_button, null)
        .create()
        .show();
  }
}
