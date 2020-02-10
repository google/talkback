/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.switchaccess.keyassignment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.accessibility.switchaccess.FeatureFlags;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Dialog to retrieve a key combination from the user for use in a preference. This class assumes
 * that all preferences are in the default shared preferences. It uses this assumption to verify
 * that key combinations can be assigned to at most one preference.
 */
public class KeyComboPreference extends DialogPreference implements DialogInterface.OnKeyListener {

  /** A value guaranteed not to match any extended key code. */
  public static final long INVALID_EXTENDED_KEY_CODE = -1;

  /* Key used to save and retrieve the super state when this preference is recreated. */
  private static final String KEY_SUPER_STATE = "super_state";

  /* Key used to store and retrieve the current key combos when this preference is recreated. */
  private static final String KEY_KEY_COMBOS = "key_combos";

  /**
   * {@link KeyEventCallback} that has been registered to this KeyComboPreference to receive key
   * related events.
   */
  private static KeyEventCallback callback;

  /* Adapter to display list of keys assigned to this preference. */
  private final ArrayAdapter<CharSequence> keyListAdapter;

  /* Button to reset key adapter to empty. */
  private Button resetButton;

  /* Switch to toggle using the screen as a switch. */
  private Switch screenSwitchToggle;

  /** A set of longs which contain both the keys pressed along with information about modifiers */
  private Set<Long> keyCombos;

  /**
   * @param context Current context
   * @param attrs Attribute set passed to DialogInterface
   */
  public KeyComboPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    keyListAdapter =
        new ArrayAdapter<>(
            context, android.R.layout.simple_list_item_1, new ArrayList<CharSequence>());
    super.setDialogLayoutResource(R.layout.switch_access_key_combo_preference_layout);
  }

  /**
   * Accepts and handles key event from parent. KeyAssignmentUtils updates the keycombo list and
   * view. Displays a message if the key is assigned elsewhere, and indicates if the press was
   * consumed or not.
   *
   * @param dialog The Dialog on which the key press occurred
   * @param keyCode The key code of the key that was pressed
   * @param event The {@link KeyEvent} to process
   * @return {@code true} if event was consumed, {@code false} otherwise
   */
  @Override
  public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
    // Process on ACTION_UP since ACTION_DOWN can be sent multiple times if the switch is
    // pressed and held or not at all in other cases (e.g. the first time the switch is pressed
    // when no keys are assigned).
    if (event.getAction() == KeyEvent.ACTION_UP) {
      // If the key press is from the back button when using a virtual hard key, close the dialog.
      // Otherwise, allow the back button to be assigned as usual. This is because the back button
      // is unable to be used as a switch when it's a virtual hard key, so it shouldn't be
      // assignable.
      if (keyCode == KeyEvent.KEYCODE_BACK
          && ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY)
              == KeyEvent.FLAG_VIRTUAL_HARD_KEY)) {
        dialog.dismiss();
        return true;
      }

      long keyCombo = KeyAssignmentUtils.keyEventToExtendedKeyCode(event);

      int keyPressResult =
          KeyAssignmentUtils.processKeyAssignment(
              getContext(), event, keyCombos, getKey(), keyListAdapter);

      CharSequence titleOfOtherPrefForKey = getTitleOfOtherActionAssociatedWith(keyCombo);
      if ((keyPressResult == KeyAssignmentUtils.KEYCOMBO_ALREADY_ASSIGNED)
          && (titleOfOtherPrefForKey != null)) {
        CharSequence toastText =
            getContext()
                .getString(
                    R.string.toast_msg_key_already_assigned,
                    KeyAssignmentUtils.describeExtendedKeyCode(keyCombo, getContext()),
                    titleOfOtherPrefForKey);
        Toast.makeText(getContext(), toastText, Toast.LENGTH_SHORT).show();
      } else {
        resetButton.setEnabled(hasSwitchesAdded());

        if ((keyPressResult == KeyAssignmentUtils.CONSUME_EVENT) && (callback != null)) {
          callback.onKeyEventConsumed();
        }

        /* If the event was ignored, return that the event was not consumed. */
        return keyPressResult != KeyAssignmentUtils.IGNORE_EVENT;
      }
    }
    resetButton.setEnabled(hasSwitchesAdded());
    return true;
  }

  @Override
  public CharSequence getSummary() {
    Set<Long> assignedKeyCombos =
        KeyAssignmentUtils.getKeyCodesForPreference(getContext(), getKey());

    int numKeysAssigned = assignedKeyCombos.size();
    if (numKeysAssigned == 1) {
      return KeyAssignmentUtils.describeExtendedKeyCode(
          assignedKeyCombos.iterator().next(), getContext());
    } else {
      return getContext()
          .getResources()
          .getQuantityString(
              R.plurals.label_num_keys_assigned_format, numKeysAssigned, numKeysAssigned);
    }
  }

  /**
   * Registers a {@link KeyEventCallback} to KeyComboPreference. A registered callback will be able
   * to capture key press events defined by {@link KeyEventCallback}.
   *
   * @param callback The {@link KeyEventCallback} to register to this KeyComboPreference
   */
  public static void registerEventCallback(KeyEventCallback callback) {
    KeyComboPreference.callback = callback;
  }

  @Override
  protected Parcelable onSaveInstanceState() {
    Bundle state = new Bundle();
    Parcelable superState = super.onSaveInstanceState();
    state.putParcelable(KEY_SUPER_STATE, superState);

    if (keyCombos != null) {
      // Store keyCombos in the bundle.
      long[] keyComboArray = new long[keyCombos.size()];
      Iterator<Long> keyComboIterator = keyCombos.iterator();
      for (int i = 0; i < keyCombos.size(); i++) {
        keyComboArray[i] = keyComboIterator.next();
      }
      state.putLongArray(KEY_KEY_COMBOS, keyComboArray);
    }
    return state;
  }

  @Override
  @SuppressWarnings("argument.type.incompatible")
  protected void onRestoreInstanceState(Parcelable state) {
    Bundle bundle = (Bundle) state;
    if (bundle.containsKey(KEY_KEY_COMBOS)) {
      // Read keyCombos from the bundle and update the list adapter.
      if (keyCombos == null) {
        keyCombos = new HashSet<>();
      } else {
        keyCombos.clear();
      }
      long[] keyComboArray = bundle.getLongArray(KEY_KEY_COMBOS);
      if (keyComboArray != null) {
        for (long keyCombo : keyComboArray) {
          keyCombos.add(keyCombo);
        }
      }
      KeyAssignmentUtils.updateKeyListAdapter(keyListAdapter, keyCombos, getContext());
    }

    super.onRestoreInstanceState(bundle.getParcelable(KEY_SUPER_STATE));
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);
    if (positiveResult) {
      Set<String> longPrefStringSet = new HashSet<>(keyCombos.size());
      for (Long keyCombo : keyCombos) {
        longPrefStringSet.add(keyCombo.toString());
      }
      SharedPreferences sharedPreferences = getSharedPreferences();
      SharedPreferences.Editor editor = sharedPreferences.edit();
      String key = getKey();
      editor.putStringSet(key, longPrefStringSet);
      editor.apply();
      callChangeListener(longPrefStringSet);
      notifyChanged();
    } else {
      keyCombos = KeyAssignmentUtils.getKeyCodesForPreference(getContext(), getKey());
    }
  }

  @Override
  protected void onBindView(View view) {
    super.onBindView(view);
    /* Some translations of Key Combination overflow a single line. Allow wrapping. */
    TextView textView = view.findViewById(android.R.id.title);
    if (textView != null) {
      textView.setSingleLine(false);
    }
  }

  @Override
  protected void onBindDialogView(View view) {
    super.onBindDialogView(view);

    // Show a summary of the key's functionality as part of the dialog message.
    CharSequence keyDescription = super.getSummary();
    if (keyDescription != null) {
      ((TextView) view.findViewById(R.id.key_combo_preference_action_description))
          .setText(keyDescription);
    }

    resetButton = view.findViewById(R.id.key_combo_preference_reset_button);
    if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
      resetButton.setFocusable(false);
    }

    if (FeatureFlags.screenSwitch()) {
      screenSwitchToggle = view.findViewById(R.id.key_combo_preference_screen_switch_toggle);
    }

    resetButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if ((screenSwitchToggle != null)
                && (keyCombos.contains(
                    KeyAssignmentUtils.keyEventToExtendedKeyCode(
                        KeyAssignmentUtils.SCREEN_SWITCH_EVENT_UP)))) {
              screenSwitchToggle.setChecked(false);
            }

            keyCombos.clear();
            KeyAssignmentUtils.updateKeyListAdapter(keyListAdapter, keyCombos, getContext());
            resetButton.setEnabled(hasSwitchesAdded());
          }
        });
    ListView listView = view.findViewById(R.id.key_combo_preference_key_list);
    // If keyCombos are not null, then we've just restored them after changing orientation. Do
    // not clear the existing ones.
    if (keyCombos == null) {
      keyCombos = KeyAssignmentUtils.getKeyCodesForPreference(getContext(), getKey());
    }
    // Always update the list to make sure that any previous (unsaved) list state is not
    // persisted.
    KeyAssignmentUtils.updateKeyListAdapter(keyListAdapter, keyCombos, getContext());

    listView.setAdapter(keyListAdapter);
    resetButton.setEnabled(hasSwitchesAdded());

    if (screenSwitchToggle != null) {
      screenSwitchToggle.setVisibility(View.VISIBLE);
      CompoundButton.OnCheckedChangeListener screenAsASwitchChangeListener =
          (buttonView, isChecked) ->
              onKey(getDialog(), Integer.MAX_VALUE, KeyAssignmentUtils.SCREEN_SWITCH_EVENT_UP);
      screenSwitchToggle.setOnCheckedChangeListener(screenAsASwitchChangeListener);
      KeyAssignmentUtils.updateScreenAsASwitchToggle(
          screenSwitchToggle, keyCombos, getContext(), getKey());
    }
  }

  @Override
  protected void showDialog(Bundle state) {
    super.showDialog(state);
    AlertDialog alertDialog = (AlertDialog) getDialog();
    if (alertDialog == null) {
      return;
    }

    if (getContext().getPackageManager().hasSystemFeature("android.hardware.touchscreen")) {
      /* Disable focus for buttons to prevent them being highlighted when keys are pressed. */
      alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setFocusable(false);
      alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setFocusable(false);
    }
    alertDialog.setOnKeyListener(this);
    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setText(R.string.save);
    alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setText(android.R.string.cancel);
  }

  @Override
  protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
    super.onPrepareDialogBuilder(builder);
    /* Take the title from the preference, not the xml file. */
    builder.setTitle(getTitle());
  }

  @Nullable
  private CharSequence getTitleOfOtherActionAssociatedWith(Long extendedKeyCode) {
    /*
     * Collect all KeyComboPreferences. It's somewhat inefficient to iterate through all
     * preferences every time, but it's only done during configuration when the user presses a
     * key. Lazily-initializing a static list would assume that there's no way a preference
     * will be added after the initialization. That assumption was not true during testing,
     * which may have been specific to the testing environment but may also indicate that
     * problematic situations can arise.
     */
    PreferenceManager preferenceManager = getPreferenceManager();
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(getContext());
    Map<String, ?> prefMap = prefs.getAll();
    String myKey = getKey();
    for (String key : prefMap.keySet()) {
      if (!myKey.equals(key)) {
        Preference preference = preferenceManager.findPreference(key);
        if (preference instanceof KeyComboPreference
            && KeyAssignmentUtils.getKeyCodesForPreference(getContext(), key)
                .contains(extendedKeyCode)) {
          return preference.getTitle();
        }
      }
    }
    return null;
  }

  private boolean hasSwitchesAdded() {
    return !keyListAdapter.isEmpty();
  }

  /** Interface for conveying information about KeyComboPreference events. */
  public interface KeyEventCallback {
    /** Called when a key press has a result of {@link KeyAssignmentUtils#CONSUME_EVENT}. */
    void onKeyEventConsumed();
  }
}
