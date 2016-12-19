/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkback.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.KeyEvent;

import com.android.talkback.KeyComboManager;
import com.android.talkback.R;

import com.android.utils.SharedPreferencesUtils;

import com.google.android.marvin.talkback.TalkBackService;

import java.util.Map;
import java.util.TreeMap;

/**
 * Default key combo model.
 */
public class DefaultKeyComboModel implements KeyComboModel {
    public static final String PREF_KEY_PREFIX = "default_key_combo_model";

    private static final boolean IS_IN_ARC = TalkBackService.isInArc();

    private final Context mContext;
    private final Map<String, Long> mKeyComboCodeMap = new TreeMap<>();
    private final KeyComboPersister mPersister;

    private int mTriggerModifier = KeyEvent.META_ALT_ON;

    public DefaultKeyComboModel(Context context) {
        mContext = context;
        mPersister = new KeyComboPersister(context, PREF_KEY_PREFIX);

        loadTriggerModifierFromPreferences();
        addKeyCombos();
    }

    private void loadTriggerModifierFromPreferences() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        int defaultTriggerModifier = IS_IN_ARC ?
                R.string.trigger_modifier_meta_entry_value :
                R.string.trigger_modifier_alt_entry_value;

        if (!prefs.contains(getPreferenceKeyForTriggerModifier())) {
            // Store default value in preferences to show it in preferences UI.
            prefs.edit().putString(getPreferenceKeyForTriggerModifier(),
                    mContext.getString(defaultTriggerModifier)).apply();
        }

        String triggerModifier = prefs.getString(getPreferenceKeyForTriggerModifier(),
                mContext.getString(defaultTriggerModifier));
        if (triggerModifier.equals(mContext.getString(R.string.trigger_modifier_alt_entry_value))) {
            mTriggerModifier = KeyEvent.META_ALT_ON;
        } else if (triggerModifier.equals(
                mContext.getString(R.string.trigger_modifier_meta_entry_value))) {
            mTriggerModifier = KeyEvent.META_META_ON;
        }
    }

    private void addKeyCombos() {
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_perform_click));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_perform_long_click));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_other_read_from_top));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_other_read_from_next_item));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_other_global_context_menu));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_other_local_context_menu));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_other_toggle_search));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_global_back));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_up));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_down));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_first));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_last));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_word));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_word));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_character));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_character));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_button));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_button));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_control));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_control));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_checkbox));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_checkbox));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_aria_landmark));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_aria_landmark));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_edit_field));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_edit_field));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_focusable_item));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_focusable_item));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_graphic));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_graphic));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_heading));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_1));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_heading_1));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_2));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_heading_2));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_3));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_heading_3));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_4));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_heading_4));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_5));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_heading_5));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_6));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_heading_6));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_list_item));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_list_item));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_link));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_link));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_list));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_list));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_table));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_table));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_combobox));
        addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_combobox));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !IS_IN_ARC) {
            addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next_window));
            addKeyCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous_window));
        }
        if (IS_IN_ARC) {
            addKeyCombo(mContext.getString(
                    R.string.keycombo_shortcut_open_manage_keyboard_shortcuts));
            addKeyCombo(mContext.getString(R.string.keycombo_shortcut_open_talkback_settings));
        }
        if (!IS_IN_ARC) {
            addKeyCombo(mContext.getString(R.string.keycombo_shortcut_global_suspend));
            addKeyCombo(mContext.getString(R.string.keycombo_shortcut_global_home));
            addKeyCombo(mContext.getString(R.string.keycombo_shortcut_global_recents));
            addKeyCombo(mContext.getString(R.string.keycombo_shortcut_global_notifications));
        }
    }

    private void addKeyCombo(String key) {
        if (!mPersister.contains(key)) {
            mPersister.saveKeyCombo(key, getDefaultKeyComboCode(key));
        }

        mKeyComboCodeMap.put(key, mPersister.getKeyComboCode(key));
    }

    @Override
    public int getTriggerModifier() {
        return mTriggerModifier;
    }

    public String getTriggerModifierName() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        String triggerModifier = prefs.getString(getPreferenceKeyForTriggerModifier(),
                mContext.getString(R.string.trigger_modifier_alt_entry_value));
        String TriggerModifierName = "";

        if (triggerModifier.equals(mContext.getString(R.string.trigger_modifier_alt_entry_value))) {
            TriggerModifierName = mContext.getString(R.string.keycombo_key_modifier_alt);
        } else if (triggerModifier.equals(
                mContext.getString(R.string.trigger_modifier_meta_entry_value))) {
            TriggerModifierName = mContext.getString(R.string.keycombo_key_modifier_meta);
        }

        return TriggerModifierName;
    }

    @Override
    public void notifyTriggerModifierChanged() {
        loadTriggerModifierFromPreferences();
    }

    @Override
    public String getPreferenceKeyForTriggerModifier() {
        return mContext.getString(R.string.pref_default_keymap_trigger_modifier_key);
    }

    @Override
    public Map<String, Long> getKeyComboCodeMap() {
        return mKeyComboCodeMap;
    }

    @Override
    public String getKeyForKeyComboCode(long keyComboCode) {
        for (Map.Entry<String, Long> entry : mKeyComboCodeMap.entrySet()) {
            if (entry.getValue() == keyComboCode) {
                return entry.getKey();
            }
        }

        return null;
    }

    @Override
    public long getKeyComboCodeForKey(String key) {
        if (key != null && mKeyComboCodeMap.containsKey(key)) {
            return mKeyComboCodeMap.get(key);
        } else {
            return KEY_COMBO_CODE_UNASSIGNED;
        }
    }

    @Override
    public long getDefaultKeyComboCode(String key) {
        if (key == null) {
            return KEY_COMBO_CODE_UNASSIGNED;
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_perform_click))) {
            if (IS_IN_ARC) {
                return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_SPACE);
            } else {
                return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_ENTER);
            }
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_perform_long_click))) {
            if (IS_IN_ARC) {
                return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON,
                        KeyEvent.KEYCODE_SPACE);
            } else {
                return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON,
                        KeyEvent.KEYCODE_ENTER);
            }
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_suspend))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_Z);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_other_read_from_top))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_ENTER);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_other_read_from_next_item))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_ENTER);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_other_global_context_menu))) {
            if (IS_IN_ARC) {
                return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_ENTER);
            } else {
                return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_SPACE);
            }
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_other_local_context_menu))) {
            if (IS_IN_ARC) {
                return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON,
                        KeyEvent.KEYCODE_ENTER);
            } else {
                return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON,
                        KeyEvent.KEYCODE_SPACE);
            }
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_other_toggle_search))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_SLASH);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_home))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_H);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_recents))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_R);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_back))) {
            return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DEL);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_notifications))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_N);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next))) {
            return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DPAD_RIGHT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous))) {
            return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DPAD_LEFT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_up))) {
            return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DPAD_UP);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_down))) {
            return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DPAD_DOWN);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_first))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON,
                    KeyEvent.KEYCODE_DPAD_LEFT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_last))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON,
                    KeyEvent.KEYCODE_DPAD_RIGHT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_word))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_DPAD_RIGHT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_word))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_DPAD_LEFT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_character))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_DPAD_RIGHT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_character))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_DPAD_LEFT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_button))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_B);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_button))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_B);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_control))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_C);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_control))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_C);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_checkbox))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_X);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_checkbox))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_X);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_next_aria_landmark))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_D);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_aria_landmark))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_D);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_edit_field))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_E);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_edit_field))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_E);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_next_focusable_item))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_F);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_focusable_item))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_F);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_graphic))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_G);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_graphic))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_G);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_H);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_heading))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_H);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_1))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_1);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_heading_1))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_1);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_2))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_2);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_heading_2))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_2);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_3))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_3);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_heading_3))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_3);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_4))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_4);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_heading_4))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_4);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_5))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_5);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_heading_5))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_5);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_heading_6))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_6);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_heading_6))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_6);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_list_item))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_I);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_navigate_previous_list_item))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_I);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_link))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_L);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_link))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_L);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_list))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_O);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_list))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_O);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_table))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_T);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_table))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_T);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_combobox))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_Z);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_combobox))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_Z);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next_window))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON,
                    KeyEvent.KEYCODE_DPAD_DOWN);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous_window))) {
            return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_DPAD_UP);
        }

        if (key.equals(mContext.getString(
                R.string.keycombo_shortcut_open_manage_keyboard_shortcuts))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_K);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_open_talkback_settings))) {
            return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_0);
        }

        return KEY_COMBO_CODE_UNASSIGNED;
    }

    @Override
    public void saveKeyComboCode(String key, long keyComboCode) {
        mPersister.saveKeyCombo(key, keyComboCode);
        mKeyComboCodeMap.put(key, keyComboCode);
    }

    @Override
    public void clearKeyComboCode(String key) {
        saveKeyComboCode(key, KEY_COMBO_CODE_UNASSIGNED);
    }

    @Override
    public boolean isEligibleKeyComboCode(long keyComboCode) {
        if (keyComboCode == KEY_COMBO_CODE_UNASSIGNED) {
            return true;
        }

        // Do not allow to set key combo which is consisted only with modifiers.
        int keyCode = KeyComboManager.getKeyCode(keyComboCode);
        if (KeyEvent.isModifierKey(keyCode) || keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false;
        }

        // It's not allowed to use trigger modifier as part of key combo code.
        return (KeyComboManager.getModifier(keyComboCode) & getTriggerModifier()) == 0;
    }

    @Override
    public String getDescriptionOfEligibleKeyCombo() {
        return mContext.getString(R.string.keycombo_assign_dialog_default_keymap_instruction,
                getTriggerModifierName());
    }

    @Override
    public void updateVersion(int previousVersion) { }
}
