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

package com.android.switchaccess;

import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import com.android.talkback.R;
import com.android.utils.SharedPreferencesUtils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Dialog to retrieve a key combination from the user for use in a preference.
 * This class assumes that all preferences are in the default shared preferences. It uses this
 * assumption to verify that key combinations can be assigned to at most one preference.
 */
public class KeyComboPreference extends DialogPreference implements DialogInterface.OnKeyListener {

    /**
     * A value guaranteed not to match any extended key code
     */
    public static final long INVALID_EXTENDED_KEY_CODE = -1;

    /* Adapter to display list of keys assigned to this preference */
    private final ArrayAdapter<CharSequence> mKeyListAdapter;

    /**
     * A set of longs which contain both the keys pressed along with information about modifiers
     */
    private Set<Long> mKeyCombos;

    /**
     * Convert a KeyEvent into a long which can be kept in settings and compared
     * to key presses when the service is in use.
     *
     * @param keyEvent The key event to convert. The (non-extended) keycode
     * must not be a modifier.
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
        return getKeyCodesForPreference(SharedPreferencesUtils.getSharedPreferences(context),
                key);
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
        try {
            Set<String> longPrefStringSet = prefs.getStringSet(key, Collections.EMPTY_SET);
            for (String longPrefString : longPrefStringSet) {
                result.add(Long.valueOf(longPrefString));
            }
        } catch (ClassCastException e) {
            /*
             * Key maps to preference that is not a set. Fall back on legacy behavior before we
             * supported multiple keys
             */
            long keyCode = prefs.getLong(key, KeyComboPreference.INVALID_EXTENDED_KEY_CODE);
            if (keyCode != INVALID_EXTENDED_KEY_CODE ) {
                result.add(keyCode);
            }
        } catch (NumberFormatException e) {
            /*
             * One of the strings in the string set can't be converted to a Long. This should
             * not be possible unless the preferences are corrupted. Remove the preference and
             * return an empty set.
             */
            prefs.edit().remove(key).apply();
        }
        return result;
    }

    /**
     * @param context Current context
     * @param attrs Attribute set passed to DialogInterface
     */
    public KeyComboPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mKeyListAdapter = new ArrayAdapter<CharSequence>(
                getContext(), android.R.layout.simple_list_item_1, new ArrayList<CharSequence>());
        setDialogLayoutResource(R.layout.switch_access_key_combo_preference_layout);
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        /* If we're ignoring this key, don't handle it */
        if (isKeyCodeToIgnore(keyCode)) {
            return false;
        }

        /* If this is a modifier key, ignore it */
        if (KeyEvent.isModifierKey(keyCode)) {
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            Long keyCombo = keyEventToExtendedKeyCode(event);
            if (mKeyCombos.contains(keyCombo)) {
                /* Don't check other keys - if it's a duplicate, it's being removed */
                mKeyCombos.remove(keyCombo);
                updateKeyListAdapter();
            } else {
                CharSequence titleOfOtherPrefForKey = getTitleOfOtherActionAssociatedWith(keyCombo);
                if (titleOfOtherPrefForKey != null) {
                    CharSequence toastText = String.format(
                            getContext().getString(R.string.toast_msg_key_already_assigned),
                            describeExtendedKeyCode(keyCombo),
                            titleOfOtherPrefForKey);
                    Toast.makeText(getContext(), toastText, Toast.LENGTH_SHORT).show();
                } else {
                    mKeyCombos.add(keyCombo);
                    updateKeyListAdapter();
                }
            }
        }

        return true;
    }

    @Override
    public CharSequence getSummary() {
        if (mKeyCombos == null) {
            mKeyCombos = getKeyCodesForPreference(getContext(), getKey());
        }
        int numKeysAssigned = mKeyCombos.size();
        if (numKeysAssigned == 1) {
            return describeExtendedKeyCode(mKeyCombos.iterator().next());
        } else {
            return getContext().getResources().getQuantityString(
                    R.plurals.label_num_keys_assigned_format, numKeysAssigned, numKeysAssigned);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            Set<String> longPrefStringSet = new HashSet<>(mKeyCombos.size());
            for (Long keyCombo : mKeyCombos) {
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
            mKeyCombos = getKeyCodesForPreference(getContext(), getKey());
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        /* Some translations of Key Combination overflow a single line. Allow wrapping. */
        TextView textView = (TextView) view.findViewById(android.R.id.title);
        if (textView != null) {
            textView.setSingleLine(false);
        }
    }

    @Override
    protected void onBindDialogView(@NonNull View view) {
        super.onBindDialogView(view);

        Button resetButton = (Button) view.findViewById(R.id.key_combo_preference_reset_button);
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            resetButton.setFocusable(false);
        }
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mKeyCombos.clear();
                updateKeyListAdapter();
            }
        });
        ListView listView = (ListView) view.findViewById(R.id.key_combo_preference_key_list);
        mKeyCombos = getKeyCodesForPreference(getContext(), getKey());
        updateKeyListAdapter();
        listView.setAdapter(mKeyListAdapter);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        AlertDialog alertDialog = (AlertDialog) getDialog();
        if (alertDialog == null) {
            return;
        }

        if (getContext().getPackageManager().hasSystemFeature("android.hardware.touchscreen")) {
            /* Disable focus for buttons to prevent them being highlighted when keys are pressed */
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setFocusable(false);
            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setFocusable(false);
        }
        alertDialog.setOnKeyListener(this);
        setPositiveButtonText(R.string.save);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        /* Take the title from the preference, not the xml file */
        builder.setTitle(getTitle());
    }

    /**
     * Create a string that describes the extended key code. This string can be
     * shown to the user to indicate the current choice of key.
     *
     * @param extendedKeyCode The key code to describe
     * @return A description of the key code
     */
    private String describeExtendedKeyCode(long extendedKeyCode) {
        if (extendedKeyCode == INVALID_EXTENDED_KEY_CODE) {
            return getContext().getString(R.string.no_key_assigned);
        }

        /* If meta keys are pressed, build a string to represent this combination of keys */
        StringBuilder keystrokeDescriptionBuilder = new StringBuilder();
        if ((extendedKeyCode & (((long) KeyEvent.META_CTRL_ON) << 32)) != 0) {
            keystrokeDescriptionBuilder.append(
                    getContext().getString(R.string.key_combo_preference_control_plus));
        }
        if ((extendedKeyCode & (((long) KeyEvent.META_ALT_ON) << 32)) != 0) {
            keystrokeDescriptionBuilder.append(
                    getContext().getString(R.string.key_combo_preference_alt_plus));
        }
        if ((extendedKeyCode & (((long) KeyEvent.META_SHIFT_ON) << 32)) != 0) {
            keystrokeDescriptionBuilder.append(
                    getContext().getString(R.string.key_combo_preference_shift_plus));
        }

        /* Try to obtain a localized representation of the key */
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, (int) extendedKeyCode);
        char displayLabel = keyEvent.getDisplayLabel();
        if (displayLabel != 0 && !Character.isWhitespace(displayLabel)) {
            keystrokeDescriptionBuilder.append(displayLabel);
        } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_SPACE) {
            keystrokeDescriptionBuilder.append(getContext().getString(R.string.name_of_space_bar));
        } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            keystrokeDescriptionBuilder.append(getContext().getString(R.string.name_of_enter_key));
        } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_TAB) {
            keystrokeDescriptionBuilder.append(getContext().getString(R.string.name_of_tab_key));
        } else {
            /* Fall back on non-localized descriptions */
            keystrokeDescriptionBuilder.append(KeyEvent.keyCodeToString((int) extendedKeyCode));
        }

        return keystrokeDescriptionBuilder.toString();
    }

    private boolean isKeyCodeToIgnore(int keyCode) {
        return ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
                || (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                || (keyCode == KeyEvent.KEYCODE_DPAD_UP)
                || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                || (keyCode == KeyEvent.KEYCODE_BACK)
                || (keyCode == KeyEvent.KEYCODE_DPAD_LEFT));
    }

    private void updateKeyListAdapter() {
        mKeyListAdapter.clear();
        for (long keyCombo : mKeyCombos) {
            mKeyListAdapter.add(describeExtendedKeyCode(keyCombo));
        }

        /* Sort the list so the keys appear in a consistent place */
        mKeyListAdapter.sort(new Comparator<CharSequence>() {
            @Override
            public int compare(CharSequence charSequence0, CharSequence charSequence1) {
                return String.CASE_INSENSITIVE_ORDER
                        .compare(charSequence0.toString(), charSequence1.toString());
            }
        });
    }

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
                Object preferenceObject = preferenceManager.findPreference(key);
                if (preferenceObject instanceof KeyComboPreference) {
                    KeyComboPreference otherPref = (KeyComboPreference) preferenceObject;
                    if (getKeyCodesForPreference(getContext(), key).contains(extendedKeyCode)) {
                        return preferenceManager.findPreference(key).getTitle();
                    }
                }
            }
        }
        return null;
    }
}
