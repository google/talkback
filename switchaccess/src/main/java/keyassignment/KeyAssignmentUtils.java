/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.switchaccess.keyassignment;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for Switch Access key assignment.
 *
 * <p>Shared between key assignment preferences and key assignment screens in setup wizard.
 */
public class KeyAssignmentUtils {

  private static final String TAG = KeyAssignmentUtils.class.getSimpleName();

  private KeyAssignmentUtils() {
    /* This class should not be instantiated */
  }

  /** A value guaranteed not to match any extended key code. */
  private static final long INVALID_EXTENDED_KEY_CODE = -1;

  /**
   * Values used to indicate the outcome of a key event's processing. A keycombo already assigned
   * requires individual action in the calling class, thus it has a unique value. Otherwise, the
   * simply indicates whether or not the event was consumed.
   */
  public static final int IGNORE_EVENT = 0;

  public static final int KEYCOMBO_ALREADY_ASSIGNED = 1;

  public static final int CONSUME_EVENT = 2;

  /*
   * Set of all preference key strings that can have KeyComboPreferences assigned to them.
   */
  private static Set<String> prefKeys;

  /*
   * Set of all preference key ids that can have KeyComboPreferences assigned to them.
   */
  private static final int[] PREF_IDS =
      new int[] {
        R.string.pref_key_mapped_to_auto_scan_key,
        R.string.pref_key_mapped_to_reverse_auto_scan_key,
        R.string.pref_key_mapped_to_next_key,
        R.string.pref_key_mapped_to_previous_key,
        R.string.pref_key_mapped_to_click_key,
        R.string.pref_key_mapped_to_long_click_key,
        R.string.pref_key_mapped_to_scroll_forward_key,
        R.string.pref_key_mapped_to_scroll_backward_key,
        R.string.pref_key_mapped_to_back_key,
        R.string.pref_key_mapped_to_home_key,
        R.string.pref_key_mapped_to_notifications_key,
        R.string.pref_key_mapped_to_quick_settings_key,
        R.string.pref_key_mapped_to_overview_key,
        R.string.pref_key_mapped_to_switch_3_key,
        R.string.pref_key_mapped_to_switch_4_key,
        R.string.pref_key_mapped_to_switch_5_key
      };

  /* Key code associated with the screen switch. The 'screen switch' corresponds to the device's
   * screen when the screen is being used as a switch. */
  public static final int KEYCODE_SCREEN_SWITCH = Integer.MAX_VALUE;

  /* KeyEvent associated with the screen switch being released. */
  public static final KeyEvent SCREEN_SWITCH_EVENT_UP =
      new KeyEvent(KeyEvent.ACTION_UP, KEYCODE_SCREEN_SWITCH);

  /* KeyEvent associated with the screen switch being pressed. */
  public static final KeyEvent SCREEN_SWITCH_EVENT_DOWN =
      new KeyEvent(KeyEvent.ACTION_DOWN, KEYCODE_SCREEN_SWITCH);

  /**
   * Returns the set of long codes of the keys assigned to a preference.
   *
   * @param context The context to use for a PreferenceManager
   * @param resId The resource Id of the preference key
   * @return The {@code Set<Long>} of the keys assigned to the preference
   */
  public static Set<Long> getKeyCodesForPreference(Context context, int resId) {
    return getKeyCodesForPreference(context, context.getString(resId));
  }

  /**
   * Returns the set of long codes of the keys assigned to a preference.
   *
   * @param context The context to use for a PreferenceManager
   * @param key The preference key
   * @return The {@code Set<Long>} of the keys assigned to the preference
   */
  public static Set<Long> getKeyCodesForPreference(Context context, String key) {
    return getKeyCodesForPreference(SharedPreferencesUtils.getSharedPreferences(context), key);
  }

  /**
   * Returns the set of long codes of the keys assigned to a preference.
   *
   * @param prefs The shared preferences
   * @param key The preference key
   * @return The {@code Set<Long>} of the keys assigned to the preference
   */
  public static Set<Long> getKeyCodesForPreference(SharedPreferences prefs, String key) {
    Set<Long> result = new HashSet<>();
    Set<String> longPrefStringSet = getStringSet(prefs, key);
    for (String longPrefString : longPrefStringSet) {
      result.add(Long.valueOf(longPrefString));
    }
    return result;
  }

  /**
   * Returns the String set associated with the provided key.
   *
   * @param prefs The shared preferences
   * @param prefKey The preference key
   * @return The {@code Set<String>} of the key names assigned to the preference
   */
  public static Set<String> getStringSet(SharedPreferences prefs, String prefKey) {
    try {
      return prefs.getStringSet(prefKey, Collections.emptySet());
    } catch (ClassCastException exception) {
      /*
       * Key maps to preference that is not a set. Fall back on legacy behavior before we
       * supported multiple keys
       */
      long keyCode = prefs.getLong(prefKey, INVALID_EXTENDED_KEY_CODE);
      if (keyCode != INVALID_EXTENDED_KEY_CODE) {
        Set<String> prefSet = new HashSet<>();
        prefSet.add(Long.toString(keyCode));
        return prefSet;
      }
    } catch (NumberFormatException exception) {
      /*
       * One of the strings in the string set can't be converted to a Long. This should
       * not be possible unless the preferences are corrupted. Remove the preference and
       * return an empty set.
       */
      prefs.edit().remove(prefKey).apply();
      LogUtils.e(TAG, exception.toString());
    }
    // If no code was found, return an empty set.
    return Collections.emptySet();
  }

  public static boolean isKeyCodeToIgnore(Context context, int keyCode) {
    // If we're not on Android TV, don't ignore any keys.
    UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    if ((uiModeManager == null)
        || (uiModeManager.getCurrentModeType() != Configuration.UI_MODE_TYPE_TELEVISION)) {
      return false;
    }

    return ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
        || (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
        || (keyCode == KeyEvent.KEYCODE_DPAD_UP)
        || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        || (keyCode == KeyEvent.KEYCODE_BACK)
        || (keyCode == KeyEvent.KEYCODE_DPAD_LEFT));
  }

  // TODO: Currently, key events with FLAG_FALLBACK are able to be assigned to switches
  // but aren't received by the accessibility service. This mainly affects DPAD key events in game
  // controllers. Ideally, these key events would be received by the service, but this is a
  // temporary fix that prevents users from assigning switches to invalid keys until a solution can
  // be found.
  public static boolean hasFlagToIgnore(KeyEvent keyEvent) {
    return (keyEvent.getFlags() & KeyEvent.FLAG_FALLBACK) == KeyEvent.FLAG_FALLBACK;
  }

  /**
   * Convert a KeyEvent into a long which can be kept in settings and compared to key presses when
   * the service is in use.
   *
   * @param keyEvent The key event to convert. The (non-extended) keycode must not be a modifier.
   * @return An extended key code that includes modifier information
   */
  public static long keyEventToExtendedKeyCode(KeyEvent keyEvent) {
    long returnValue = keyEvent.getKeyCode();
    returnValue |= (keyEvent.isShiftPressed()) ? (((long) KeyEvent.META_SHIFT_ON) << 32) : 0;
    returnValue |= (keyEvent.isCtrlPressed()) ? (((long) KeyEvent.META_CTRL_ON) << 32) : 0;
    returnValue |= (keyEvent.isAltPressed()) ? (((long) KeyEvent.META_ALT_ON) << 32) : 0;
    return returnValue;
  }

  /**
   * Create a string that describes the extended key code. This string can be shown to the user to
   * indicate the current choice of key.
   *
   * @param extendedKeyCode The key code to describe
   * @param context The current Context
   * @return A description of the key code
   */
  public static String describeExtendedKeyCode(long extendedKeyCode, Context context) {
    if (extendedKeyCode == INVALID_EXTENDED_KEY_CODE) {
      return context.getString(R.string.no_key_assigned);
    }

    if (extendedKeyCode == KEYCODE_SCREEN_SWITCH) {
      return context.getString(R.string.name_of_screen_switch);
    }
    /* If meta keys are pressed, build a string to represent this combination of keys */
    StringBuilder keystrokeDescriptionBuilder = new StringBuilder();
    if ((extendedKeyCode & (((long) KeyEvent.META_CTRL_ON) << 32)) != 0) {
      keystrokeDescriptionBuilder.append(
          context.getString(R.string.key_combo_preference_control_plus));
    }
    if ((extendedKeyCode & (((long) KeyEvent.META_ALT_ON) << 32)) != 0) {
      keystrokeDescriptionBuilder.append(context.getString(R.string.key_combo_preference_alt_plus));
    }
    if ((extendedKeyCode & (((long) KeyEvent.META_SHIFT_ON) << 32)) != 0) {
      keystrokeDescriptionBuilder.append(
          context.getString(R.string.key_combo_preference_shift_plus));
    }

    /* Try to obtain a localized representation of the key */
    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, (int) extendedKeyCode);
    char displayLabel = keyEvent.getDisplayLabel();
    if (displayLabel != 0 && !Character.isWhitespace(displayLabel)) {
      keystrokeDescriptionBuilder.append(displayLabel);
    } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_SPACE) {
      keystrokeDescriptionBuilder.append(context.getString(R.string.name_of_space_bar));
    } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
      keystrokeDescriptionBuilder.append(context.getString(R.string.name_of_enter_key));
    } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_TAB) {
      keystrokeDescriptionBuilder.append(context.getString(R.string.name_of_tab_key));
    } else {
      /* Fall back on non-localized descriptions */
      keystrokeDescriptionBuilder.append(KeyEvent.keyCodeToString((int) extendedKeyCode));
    }

    return keystrokeDescriptionBuilder.toString();
  }

  /**
   * Check if any other preference already has the switch in question assigned to it.
   *
   * @param extendedKeyCode The key code to check for
   * @param context context the method call came from
   * @param key String key for the preference attempting to be assigned to
   * @return boolean that is true is another action is associated with the key
   */
  public static boolean otherActionAssociatedWithKey(
      Long extendedKeyCode, Context context, String key) {
    getPrefKeys(context);
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    Map<String, ?> prefMap = prefs.getAll();
    for (String prefKey : prefMap.keySet()) {
      if (!key.equals(prefKey)) {
        if (prefKeys.contains(prefKey)) {
          if (getKeyCodesForPreference(context, prefKey).contains(extendedKeyCode)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Handles a key assignment key press. If a key is a valid assignment, the calling screen's keyset
   * is updated and view refreshed. Otherwise, the calling method is told to pass the event up to be
   * handled, or display a message saying the key is already assigned elsewhere.
   *
   * @param context The context of the activity the method call came from
   * @param event The key press event to be processed
   * @param keyCombos The present set of assigned keys
   * @param key The key to ignore while checking for assignment
   * @param keyListAdapter The ArrayAdapter that displays the current list of keys assigned to this
   *     preference
   * @return descriptive constant of how the event was handled
   */
  public static int processKeyAssignment(
      Context context,
      KeyEvent event,
      Set<Long> keyCombos,
      String key,
      ArrayAdapter<CharSequence> keyListAdapter) {
    int keyCode = event.getKeyCode();
    /* If we're ignoring this key, don't handle it */
    if (isKeyCodeToIgnore(context, keyCode) || hasFlagToIgnore(event)) {
      return IGNORE_EVENT;
    }
    /* If this is a modifier key, ignore it */
    if (KeyEvent.isModifierKey(keyCode)) {
      return CONSUME_EVENT;
    }
    Long keyCombo = keyEventToExtendedKeyCode(event);
    if (keyCombos.contains(keyCombo)) {
      /* If this key was pressed already, remove it from the list */
      keyCombos.remove(keyCombo);
      updateKeyListAdapter(keyListAdapter, keyCombos, context);
    } else {
      if (otherActionAssociatedWithKey(keyCombo, context, key)) {
        /* If the key is assigned elsewhere, notify the calling method to show a message */
        return KEYCOMBO_ALREADY_ASSIGNED;
      } else {
        /* Add the key to the list to be mapped to the preference */
        keyCombos.add(keyCombo);
        updateKeyListAdapter(keyListAdapter, keyCombos, context);
      }
    }
    return CONSUME_EVENT;
  }

  /**
   * Updates the arrayadapter to reflect changes in the list of keys assigned.
   *
   * @param keyListAdapter The ArrayAdapter to be updated
   * @param keyCombos The present set of assigned keys
   * @param context The context of the activity the call came from
   */
  public static void updateKeyListAdapter(
      ArrayAdapter<CharSequence> keyListAdapter, Set<Long> keyCombos, Context context) {
    keyListAdapter.clear();
    for (long keyCombo : keyCombos) {
      keyListAdapter.add(KeyAssignmentUtils.describeExtendedKeyCode(keyCombo, context));
    }

    /* Sort the list so the keys appear in a consistent place */
    keyListAdapter.sort(
        (charSequence0, charSequence1) ->
            charSequence0.toString().compareToIgnoreCase(charSequence1.toString()));
  }

  /*
   * Checks that any keycombo preferences exist, indicating Switch Access is already configured.
   */
  public static boolean areKeysAssigned(Context context) {
    getPrefKeys(context);
    for (String key : prefKeys) {
      if (!getKeyCodesForPreference(context, key).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the Switch Access configuration is functional for scanning after the user completes
   * the setup guide.
   *
   * <p>Note: This function only checks the functionality of the configurations presented in the
   * setup guide.
   *
   * @param sharedPreferences The shared preferences
   * @param context The context that will be used to check Switch Access configuration
   */
  public static boolean isConfigurationFunctionalAfterSetup(
      SharedPreferences sharedPreferences, Context context) {
    boolean isAutoScanEnabled = SwitchAccessPreferenceUtils.isAutoScanEnabled(context);
    // If Auto-scan is enabled and we have an Auto-scan key assigned, then we have a complete
    // configuration. However, if we don't have keys assigned to Auto-scan, we could still have
    // as complete Next/Select assignment, so we can't eliminate the possibility of a two switch
    // configuration even if Auto-scan is enabled.
    Set<String> autoScanSwitchNames =
        getStringSet(
            sharedPreferences, context.getString(R.string.pref_key_mapped_to_auto_scan_key));
    if (isAutoScanEnabled && !autoScanSwitchNames.isEmpty()) {
      return true;
    }

    // Check if we have a complete two-switch configuration.
    Set<String> actionOneSwitchNames =
        getStringSet(sharedPreferences, context.getString(R.string.pref_key_mapped_to_next_key));
    Set<String> actionTwoSwitchNames =
        getStringSet(sharedPreferences, context.getString(R.string.pref_key_mapped_to_click_key));

    return !actionOneSwitchNames.isEmpty() && !actionTwoSwitchNames.isEmpty();
  }

  /*
   * Loads prefKeys set using PREF_IDS the first time prefKeys is needed.
   */
  public static Set<String> getPrefKeys(Context context) {
    if (prefKeys != null) {
      return prefKeys;
    }
    prefKeys = new HashSet<>();

    for (int id : PREF_IDS) {
      prefKeys.add(context.getString(id));
    }
    return prefKeys;
  }

  /*
   * Clears all keycombo preferences that have switches assigned to them.
   */
  public static void clearAllKeyPrefs(Context context) {
    getPrefKeys(context);
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(context);
    for (String key : prefKeys) {
      if (!getKeyCodesForPreference(context, key).isEmpty()) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(key, Collections.emptySet());
        editor.apply();
      }
    }
  }

  /**
   * Configures the screen switch preference toggle based on whether and where the screen switch is
   * currently assigned.
   *
   * @param screenSwitchToggle A switch that corresponds to the current assignment status of the
   *     screen switch.
   * @param keyCombos List of keys currently assigned to this action
   * @param context Context used to determine if the screen switch is currently assigned to a
   *     different action
   * @param key The key to the map that pairs an action with a set of switches currently assigned to
   *     said action.
   */
  public static void updateScreenAsASwitchToggle(
      Switch screenSwitchToggle, Set<Long> keyCombos, Context context, String key) {
    long screenSwitchKeyCombo =
        KeyAssignmentUtils.keyEventToExtendedKeyCode(SCREEN_SWITCH_EVENT_UP);

    if (!keyCombos.isEmpty() && keyCombos.contains(screenSwitchKeyCombo)) {
      screenSwitchToggle.setEnabled(true);
      screenSwitchToggle.setChecked(true);
      screenSwitchToggle.setText(R.string.key_combo_preference_screen_switch_toggle);
    } else if (KeyAssignmentUtils.otherActionAssociatedWithKey(
        screenSwitchKeyCombo, context, key)) {
      screenSwitchToggle.setEnabled(false);
      screenSwitchToggle.setChecked(false);
      screenSwitchToggle.setText(R.string.key_combo_preference_screen_switch_toggle_disabled);
    } else {
      screenSwitchToggle.setEnabled(true);
      screenSwitchToggle.setChecked(false);
      screenSwitchToggle.setText(R.string.key_combo_preference_screen_switch_toggle);
    }
  }
}
