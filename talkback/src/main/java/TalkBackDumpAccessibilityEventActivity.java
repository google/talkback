/*
 * Copyright 2016 Google Inc.
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

package com.google.android.accessibility.talkback;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.TtsSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityEvent;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;
import java.util.List;

/** Activity used to set TalkBack's dump events preferences. */
public class TalkBackDumpAccessibilityEventActivity extends BasePreferencesActivity {

  private MenuItem.OnMenuItemClickListener listener;

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    TalkBackDumpAccessibilityEventFragment fragment = new TalkBackDumpAccessibilityEventFragment();
    listener = fragment;
    return fragment;
  }

  /**
   * Finishes the activity when the up button is pressed on the action bar. Perform function in
   * TalkBackAccessibilityEventFragment when clear_all/check_all button is pressed.
   */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      finish();
      return true;
    } else if (itemId == R.id.disable_all || itemId == R.id.enable_all) {
      return listener.onMenuItemClick(item);
    } else {
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.dump_a11y_event_menu, menu);
    return true;
  }

  /** Fragment used to display TalkBack's dump events preferences. */
  public static class TalkBackDumpAccessibilityEventFragment extends PreferenceFragmentCompat
      implements MenuItem.OnMenuItemClickListener {

    private final List<EventDumperSwitchPreference> switchPreferences = new ArrayList<>();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      Context context = getActivity();

      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.dump_events_preferences);

      PreferenceScreen screen = getPreferenceScreen();

      for (int eventType : AccessibilityEventUtils.getAllEventTypes()) {
        EventDumperSwitchPreference preference =
            new EventDumperSwitchPreference(context, eventType);
        switchPreferences.add(preference);
        screen.addPreference(preference);
      }
    }

    private void setDumpAllEvents(boolean enabled) {
      Context context = getActivity().getApplicationContext();
      SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
      prefs
          .edit()
          .putInt(
              context.getString(R.string.pref_dump_event_mask_key),
              enabled ? AccessibilityEvent.TYPES_ALL_MASK : 0)
          .apply();
      for (EventDumperSwitchPreference preference : switchPreferences) {
        preference.setChecked(enabled);
      }
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
      final int itemId = menuItem.getItemId();
      if (itemId == R.id.disable_all) {
        setDumpAllEvents(false);
        return true;
      } else if (itemId == R.id.enable_all) {
        setDumpAllEvents(true);
        return true;
      } else {
        return false;
      }
    }

    private static class EventDumperSwitchPreference extends SwitchPreference
        implements OnPreferenceChangeListener {
      private final int eventType;

      EventDumperSwitchPreference(Context context, int eventType) {
        super(context);
        this.eventType = eventType;
        setOnPreferenceChangeListener(this);

        String title = AccessibilityEvent.eventTypeToString(eventType);
        // Add TtsSpan to the titles to improve readability.
        SpannableString spannableString = new SpannableString(title);
        TtsSpan ttsSpan = new TtsSpan.TextBuilder(title.replaceAll("_", " ")).build();
        spannableString.setSpan(ttsSpan, 0, title.length(), 0 /* no flag */);
        setTitle(spannableString);

        // Set initial value.
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(getContext());
        int value = prefs.getInt(getContext().getString(R.string.pref_dump_event_mask_key), 0);
        setChecked((value & eventType) != 0);
      }

      @Override
      public boolean onPreferenceChange(Preference preference, Object o) {
        boolean enabled = (Boolean) o;
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(getContext());
        int value = prefs.getInt(getContext().getString(R.string.pref_dump_event_mask_key), 0);
        if (enabled) {
          value |= eventType;
        } else {
          value &= ~eventType;
        }
        prefs
            .edit()
            .putInt(getContext().getString(R.string.pref_dump_event_mask_key), value)
            .apply();
        return true;
      }
    }
  }
}
