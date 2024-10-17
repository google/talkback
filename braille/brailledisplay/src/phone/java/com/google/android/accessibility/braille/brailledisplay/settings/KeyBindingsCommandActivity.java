/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.settings;

import static com.google.android.accessibility.braille.brailledisplay.settings.KeyBindingsActivity.PROPERTY_KEY;
import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplay;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Category;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Subcategory;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer.AspectConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer.CreationArguments;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleKeyBinding;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import java.util.ArrayList;
import java.util.Objects;

/** Shows key bindings for the currently connected Braille display by selected the category. */
public class KeyBindingsCommandActivity extends PreferencesActivity {
  private static final String TAG = "KeyBindingCommandActivity";
  public static final String TITLE_KEY = "title";
  public static final String TYPE_KEY = "type";

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new KeyBindingsCommandFragment();
  }

  @Override
  protected String getFragmentTag() {
    return TAG;
  }

  /** Fragment that holds the braille elements preference. */
  public static class KeyBindingsCommandFragment extends PreferenceFragmentCompat {
    private Category supportedCommandCategory;
    private Connectioneer connectioneer;
    private Preference descriptionPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      connectioneer =
          Connectioneer.getInstance(
              new CreationArguments(
                  getContext().getApplicationContext(),
                  BrailleDisplay.ENCODER_FACTORY.getDeviceNameFilter()));
    }

    @Override
    public void onResume() {
      super.onResume();
      connectioneer.aspectDisplayProperties.attach(displayPropertyCallback);
      connectioneer.aspectConnection.attach(connectionCallback);
    }

    @Override
    public void onPause() {
      super.onPause();
      connectioneer.aspectDisplayProperties.detach(displayPropertyCallback);
      connectioneer.aspectConnection.detach(connectionCallback);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.key_bindings_command);
      Intent intent = getActivity().getIntent();
      if (intent.getExtras() == null) {
        return;
      }
      getActivity().setTitle(intent.getStringExtra(TITLE_KEY));
      supportedCommandCategory = (Category) intent.getSerializableExtra(TYPE_KEY);
      String description = supportedCommandCategory.getDescription(getResources());
      descriptionPreference =
          findPreference(getString(R.string.pref_key_bd_keybindings_editing_description));
      if (TextUtils.isEmpty(description)) {
        getPreferenceScreen().removePreference(descriptionPreference);
      } else {
        descriptionPreference.setSummary(description);
      }

      refresh(intent.getParcelableExtra(PROPERTY_KEY));
    }

    private void refresh(BrailleDisplayProperties props) {
      for (int i = getPreferenceScreen().getPreferenceCount() - 1; i >= 0; i--) {
        Preference preference = getPreferenceScreen().getPreference(i);
        if (!Objects.equals(preference.getKey(), descriptionPreference.getKey())) {
          getPreferenceScreen().removePreference(preference);
        }
      }
      for (SupportedCommand supportedCommand :
          BrailleKeyBindingUtils.getSupportedCommands(getContext())) {
        if (supportedCommand.getCategory().equals(supportedCommandCategory)) {
          String keys = getKeyDescription(supportedCommand, props);
          if (TextUtils.isEmpty(keys)) {
            continue;
          }
          addPreference(
              getPreferenceScreen(),
              supportedCommand.getSubcategory(),
              createPreference(supportedCommand.getCommandDescription(getResources()), keys));
        }
      }
    }

    private String getKeyDescription(SupportedCommand command, BrailleDisplayProperties props) {
      if (props == null) {
        BrailleDisplayLog.v(TAG, "no property");
        // Use default KeyBindings when display properties is null.
        return command.getKeyDescription(getResources());
      } else {
        ArrayList<BrailleKeyBinding> sortedBindings =
            BrailleKeyBindingUtils.getSortedBindingsForDisplay(props);
        BrailleKeyBinding binding =
            BrailleKeyBindingUtils.getBrailleKeyBindingForCommand(
                command.getCommand(), sortedBindings);
        if (binding == null) {
          // No supported binding for this display. That's normal.
          return "";
        }
        return BrailleKeyBindingUtils.getFriendlyKeyNamesForCommand(
            binding, props.getFriendlyKeyNames(), getContext());
      }
    }

    private void addPreference(
        PreferenceScreen preferenceScreen, Subcategory subcategory, Preference preference) {
      if (subcategory == Subcategory.UNDEFINED) {
        preferenceScreen.addPreference(preference);
      } else {
        PreferenceCategory preferenceCategory =
            preferenceScreen.getPreferenceManager().findPreference(subcategory.name());
        if (preferenceCategory == null) {
          preferenceCategory = new PreferenceCategory(getContext());
          preferenceScreen.addPreference(preferenceCategory);
          preferenceCategory.setKey(subcategory.name());
          preferenceCategory.setTitle(subcategory.getTitle(getResources()));
        }
        preferenceCategory.addPreference(preference);
      }
    }

    private Preference createPreference(
        CharSequence commandDescription, CharSequence keyDescription) {
      String title =
          getResources()
              .getString(
                  R.string.bd_commands_description_template, commandDescription, keyDescription);
      Preference preference = new Preference(getContext());
      // In order to let title color to not change to gray, when the Preference is not
      // selectable.
      preference.setTitle(changeTextColor(title, R.color.settings_primary_text));
      preference.setSelectable(false);
      return preference;
    }

    private SpannableString changeTextColor(CharSequence text, int colorResId) {
      SpannableString spannableString = new SpannableString(text);
      spannableString.setSpan(
          new ForegroundColorSpan(
              getContext().getResources().getColor(colorResId, /* theme= */ null)),
          /* start= */ 0,
          text.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return spannableString;
    }

    private final Connectioneer.AspectDisplayProperties.Callback displayPropertyCallback =
        new Connectioneer.AspectDisplayProperties.Callback() {
          @Override
          public void onDisplayPropertiesArrived(
              BrailleDisplayProperties brailleDisplayProperties) {
            refresh(brailleDisplayProperties);
          }
        };

    private final Connectioneer.AspectConnection.Callback connectionCallback =
        new AspectConnection.Callback() {
          @Override
          public void onScanningChanged() {}

          @Override
          public void onDeviceListCleared() {}

          @Override
          public void onConnectHidStarted() {}

          @Override
          public void onConnectRfcommStarted() {}

          @Override
          public void onConnectableDeviceSeenOrUpdated(ConnectableDevice device) {}

          @Override
          public void onConnectableDeviceDeleted(ConnectableDevice device) {}

          @Override
          public void onConnectionStatusChanged(ConnectStatus status, ConnectableDevice device) {
            if (status != ConnectStatus.CONNECTED) {
              refresh(null);
            }
          }

          @Override
          public void onConnectFailed(boolean manual, @Nullable String deviceName) {}
        };
  }
}
