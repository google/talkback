package com.google.android.accessibility.braille.brailledisplay.settings;

import static com.google.android.accessibility.braille.brailledisplay.settings.KeyBindingsActivity.PROPERTY_KEY;
import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
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
import java.util.Map;

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
    private String supportedCommandTypeName;
    private Connectioneer connectioneer;

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
      getActivity().setTitle(getActivity().getIntent().getStringExtra(TITLE_KEY));
      supportedCommandTypeName = getActivity().getIntent().getStringExtra(TYPE_KEY);
      refresh(getActivity().getIntent().getParcelableExtra(PROPERTY_KEY));
    }

    private void refresh(BrailleDisplayProperties props) {
      PreferenceScreen preferenceScreen = getPreferenceScreen();
      preferenceScreen.removeAll();

      // Use default KeyBindings when display properties is null.
      if (props == null) {
        BrailleDisplayLog.v(TAG, "no property");
        for (SupportedCommand supportedCommand :
            BrailleKeyBindingUtils.getSupportedCommands(getContext())) {
          if (supportedCommand.getCategory().name().equals(supportedCommandTypeName)) {
            addPreference(
                preferenceScreen,
                supportedCommand.getSubcategory(),
                createPreference(
                    supportedCommand.getCommandDescription(getResources()),
                    supportedCommand.getKeyDescription(getResources())));
          }
        }
      } else {
        ArrayList<BrailleKeyBinding> sortedBindings =
            BrailleKeyBindingUtils.getSortedBindingsForDisplay(props);
        Map<String, String> friendlyKeyNames = props.getFriendlyKeyNames();
        for (SupportedCommand supportedCommand :
            BrailleKeyBindingUtils.getSupportedCommands(getContext())) {
          if (supportedCommand.getCategory().name().equals(supportedCommandTypeName)) {
            BrailleKeyBinding binding =
                BrailleKeyBindingUtils.getBrailleKeyBindingForCommand(
                    supportedCommand.getCommand(), sortedBindings);
            if (binding == null) {
              // No supported binding for this display. That's normal.
              continue;
            }
            String keys =
                BrailleKeyBindingUtils.getFriendlyKeyNamesForCommand(
                    binding, friendlyKeyNames, getContext());
            addPreference(
                preferenceScreen,
                supportedCommand.getSubcategory(),
                createPreference(supportedCommand.getCommandDescription(getResources()), keys));
          }
        }
      }
    }

    private void addPreference(
        PreferenceScreen preferenceScreen, Subcategory subcategory, Preference preference) {
      String title = getSubcategoryTitle(subcategory);
      if (subcategory == Subcategory.UNDEFINED) {
        preferenceScreen.addPreference(preference);
      } else {
        PreferenceCategory preferenceCategory =
            preferenceScreen.getPreferenceManager().findPreference(subcategory.name());
        if (preferenceCategory == null) {
          preferenceCategory = new PreferenceCategory(getContext());
          preferenceScreen.addPreference(preferenceCategory);
          preferenceCategory.setKey(subcategory.name());
          preferenceCategory.setTitle(title);
        }
        preferenceCategory.addPreference(preference);
      }
    }

    private String getSubcategoryTitle(Subcategory subcategory) {
      switch (subcategory) {
        case BASIC:
          return getString(R.string.bd_cmd_subcategory_title_basic);
        case WINDOW:
          return getString(R.string.bd_cmd_subcategory_title_window);
        case PLACE_ON_PAGE:
          return getString(R.string.bd_cmd_subcategory_title_place_on_page);
        case WEB_CONTENT:
          return getString(R.string.bd_cmd_subcategory_title_web_content);
        case READING_CONTROLS:
          return getString(R.string.bd_cmd_subcategory_title_reading_controls);
        case AUTO_SCROLL:
          return getString(R.string.bd_cmd_subcategory_title_auto_scroll);
        case MOVE_CURSOR:
          return getString(R.string.bd_cmd_subcategory_title_move_cursor);
        case SELECT:
          return getString(R.string.bd_cmd_subcategory_title_select);
        case EDIT:
          return getString(R.string.bd_cmd_subcategory_title_edit);
        default:
          return "";
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
          public void onConnectStarted() {}

          @Override
          public void onConnectableDeviceSeenOrUpdated(ConnectableDevice device) {}

          @Override
          public void onConnectionStatusChanged(ConnectStatus status, ConnectableDevice device) {
            if (status != ConnectStatus.CONNECTED) {
              refresh(null);
            }
          }

          @Override
          public void onConnectFailed(@Nullable String deviceName) {}
        };
  }
}
