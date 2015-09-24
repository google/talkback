/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.talkback;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.KeyEvent;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Manages state related to detecting key combinations.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class KeyComboManager implements TalkBackService.KeyEventListener {

    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

    public static final int NO_MATCH = -1;
    public static final int PARTIAL_MATCH = 1;
    public static final int EXACT_MATCH = 2;

    public static final int ACTION_NAVIGATE_NEXT = 1;
    public static final int ACTION_NAVIGATE_PREVIOUS = 2;
    public static final int ACTION_NAVIGATE_FIRST = 3;
    public static final int ACTION_NAVIGATE_LAST = 4;
    public static final int ACTION_PERFORM_CLICK = 5;
    public static final int ACTION_BACK = 6;
    public static final int ACTION_HOME = 7;
    public static final int ACTION_RECENTS = 8;
    public static final int ACTION_NOTIFICATION = 9;
    public static final int ACTION_SUSPEND = 10;
    public static final int ACTION_GRANULARITY_INCREASE = 11;
    public static final int ACTION_GRANULARITY_DECREASE = 12;
    public static final int ACTION_READ_FROM_TOP = 13;
    public static final int ACTION_READ_FROM_NEXT_ITEM = 14;
    public static final int ACTION_TOGGLE_SEARCH = 15;
    public static final int ACTION_LOCAL_CONTEXT_MENU = 16;
    public static final int ACTION_GLOBAL_CONTEXT_MENU = 17;

    public static final int KEY_COMBO_CODE_UNASSIGNED = KeyEvent.KEYCODE_UNKNOWN;

    private static final int KEY_EVENT_MODIFIER_MASK = KeyEvent.META_SHIFT_ON |
            KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;

    private static final String SHIFT_STR = "Shift";
    private static final String CTRL_STR = "Ctrl";
    private static final String ALT_STR = "Alt";
    private static final String CONCATINATION_STR = " + ";

    /**
     * Returns kecComboCode that represent keyEvent.
     */
    public static long getKeyComboCode(KeyEvent keyEvent) {
        if (keyEvent == null) {
            return KEY_COMBO_CODE_UNASSIGNED;
        }

        int modifier = keyEvent.getModifiers() & KEY_EVENT_MODIFIER_MASK;
        return getKeyComboCode(modifier, keyEvent.getKeyCode());
    }

    private static long getKeyComboCode(int modifier, int keycode) {
        return (((long) modifier) << 32) + keycode;
    }

    private static int getModifier(long keyComboCode) {
        return (int) (keyComboCode >> 32);
    }

    private static int getKeyCode(long keyComboCode) {
        return (int) (keyComboCode);
    }

    /** List of possible key combinations. */
    private List<KeyCombo> mKeyCombos = new ArrayList<>();

    private Map<String, KeyCombo> mKeyComboMap = new HashMap<>();

    /** The number of keys currently being pressed. */
    private int mKeyCount;

    /** Whether the user performed a combo during the current interaction. */
    private boolean mPerformedCombo;

    /** Whether the user may be performing a combo and we should intercept keys. */
    private boolean mHasPartialMatch;

    /** The listener that receives callbacks when a combo is recognized. */
    private final List<KeyComboListener> mListeners = new LinkedList<>();

    private Context mContext;
    private KeyComboPersister mPersister;
    private boolean mMatchKeyCombo = true;

    public KeyComboManager(Context context) {
        mContext = context;
        mPersister = new KeyComboPersister(context);
        loadCombos();
    }

    /**
     * Loads default key combinations.
     */
    private void loadCombos() {
        addCombo(ACTION_NAVIGATE_NEXT, mContext.getString(
                R.string.keycombo_shortcut_navigate_next));
        addCombo(ACTION_NAVIGATE_PREVIOUS, mContext.getString(
                R.string.keycombo_shortcut_navigate_previous));
        addCombo(ACTION_NAVIGATE_FIRST, mContext.getString(
                R.string.keycombo_shortcut_navigate_first));
        addCombo(ACTION_NAVIGATE_LAST, mContext.getString(
                R.string.keycombo_shortcut_navigate_last));
        addCombo(ACTION_PERFORM_CLICK, mContext.getString(
                R.string.keycombo_shortcut_perform_click));
        addCombo(ACTION_BACK, mContext.getString(
                R.string.keycombo_shortcut_global_back));
        addCombo(ACTION_HOME, mContext.getString(
                R.string.keycombo_shortcut_global_home));
        addCombo(ACTION_RECENTS, mContext.getString(
                R.string.keycombo_shortcut_global_recents));
        addCombo(ACTION_NOTIFICATION, mContext.getString(
                R.string.keycombo_shortcut_global_notifications));
        addCombo(ACTION_SUSPEND, mContext.getString(
                R.string.keycombo_shortcut_global_suspend));
        addCombo(ACTION_GRANULARITY_INCREASE, mContext.getString(
                R.string.keycombo_shortcut_granularity_increase));
        addCombo(ACTION_GRANULARITY_DECREASE, mContext.getString(
                R.string.keycombo_shortcut_granularity_decrease));
        addCombo(ACTION_READ_FROM_TOP, mContext.getString(
                R.string.keycombo_shortcut_other_read_from_top));
        addCombo(ACTION_READ_FROM_NEXT_ITEM, mContext.getString(
                R.string.keycombo_shortcut_other_read_from_next_item));
        addCombo(ACTION_TOGGLE_SEARCH, mContext.getString(
                R.string.keycombo_shortcut_other_toggle_search));
        addCombo(ACTION_LOCAL_CONTEXT_MENU, mContext.getString(
                R.string.keycombo_shortcut_other_local_context_menu));
        addCombo(ACTION_GLOBAL_CONTEXT_MENU, mContext.getString(
                R.string.keycombo_shortcut_other_global_context_menu));
    }

    // TODO(KM): Look into ways to add key combos for specific listeners
    private void addCombo(int id, String key) {
        if (!mPersister.contains(key)) {
            mPersister.saveKeyCombo(key, getDefaultValue(key));
        }
        long keyComboCode = mPersister.getKeyComboCode(key);
        KeyCombo keyCombo = new KeyCombo(id, key, keyComboCode);
        mKeyCombos.add(keyCombo);
        mKeyComboMap.put(key, keyCombo);
    }

    public long getDefaultValue(String key) {
        if (key == null) {
            return KEY_COMBO_CODE_UNASSIGNED;
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_DPAD_RIGHT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_DPAD_LEFT);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_first))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_DPAD_UP);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_last))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_DPAD_DOWN);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_perform_click))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_ENTER);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_back))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_DEL);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_home))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_H);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_recents))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_R);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_notifications))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_N);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_other_toggle_search))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_SLASH);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_suspend))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_Z);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_granularity_increase))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON,
                    KeyEvent.KEYCODE_PLUS);
        }

        if (key.equals(mContext.getString(R.string.keycombo_shortcut_granularity_decrease))) {
            return getKeyComboCode(KeyEvent.META_ALT_ON,
                    KeyEvent.KEYCODE_MINUS);
        }

        return KEY_COMBO_CODE_UNASSIGNED;
    }

    /**
     * Sets the listener that receives callbacks when the user performs key
     * combinations.
     *
     * @param listener The listener that receives callbacks.
     */
    public void addListener(KeyComboListener listener) {
        mListeners.add(listener);
    }

    /**
     * Set whether to process keycombo
     */
    public void setMatchKeyCombo(boolean value) {
        mMatchKeyCombo = value;
        mKeyCount = 0;
        mHasPartialMatch = false;
        mPerformedCombo = false;
    }

    /**
     * get key for preference that is assigned for keyComboCode
     */
    public String getKeyForKeyComboCode(long keyComboCode) {
        for (KeyCombo keyCombo : mKeyCombos) {
            if (keyCombo.keyComboCode == keyComboCode) {
                return keyCombo.key;
            }
        }

        return null;
    }

    /**
     * clears keycombo assigned for preference key
     */
    public void clearKeyCombo(String key) {
        saveKeyCombo(key, KEY_COMBO_CODE_UNASSIGNED);
    }

    public boolean isEligibleKeyCombo(long keyComboCode) {
        int modifier = getModifier(keyComboCode);
        if (modifier == 0 || (modifier & ~KeyEvent.META_SHIFT_ON) == 0) {
            return false;
        }
        int keyCode = getKeyCode(keyComboCode);
        return keyCode != 0 &&
                keyCode != KeyEvent.KEYCODE_SHIFT_LEFT && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT &&
                keyCode != KeyEvent.KEYCODE_ALT_LEFT && keyCode != KeyEvent.KEYCODE_ALT_RIGHT &&
                keyCode != KeyEvent.KEYCODE_CTRL_LEFT && keyCode != KeyEvent.KEYCODE_CTRL_RIGHT;

    }

    /**
     * assign keycombo for preference
     * @param key - preference key
     * @param keyComboCode keycombo code that represents key combination
     */
    public void saveKeyCombo(String key, long keyComboCode) {
        mPersister.saveKeyCombo(key, keyComboCode);
        KeyCombo keyCombo = mKeyComboMap.get(key);
        if (keyCombo != null) {
            keyCombo.keyComboCode = keyComboCode;
        }
    }

    /**
     * get keycombo code that is assigned for preference
     * @param key - preference key
     */
    public long getKeyComboCodeForKey(String key) {
        return mPersister.getKeyComboCode(key);
    }

    /**
     * returns user friendly string representations of keycombo code
     */
    public String getKeyComboStringRepresentation(long keyComboCode) {
        if (keyComboCode == KEY_COMBO_CODE_UNASSIGNED) {
            return mContext.getString(R.string.keycombo_unassigned);
        }

        int modifier = getModifier(keyComboCode);
        int keyCode = getKeyCode(keyComboCode);
        StringBuilder sb = new StringBuilder();
        if ((modifier & KeyEvent.META_ALT_ON) > 0) {
            sb.append(ALT_STR);

            if (keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                    keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
                keyCode = KEY_COMBO_CODE_UNASSIGNED;
            }
        }

        if ((modifier & KeyEvent.META_SHIFT_ON) > 0) {
            appendPlusSignIfNotEmpty(sb);
            sb.append(SHIFT_STR);

            if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                    keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                keyCode = KEY_COMBO_CODE_UNASSIGNED;
            }
        }

        if ((modifier & KeyEvent.META_CTRL_ON) > 0) {
            appendPlusSignIfNotEmpty(sb);
            sb.append(CTRL_STR);

            if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                    keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
                keyCode = KEY_COMBO_CODE_UNASSIGNED;
            }
        }

        if (keyCode > 0) {
            appendPlusSignIfNotEmpty(sb);
            String keyCodeString = KeyEvent.keyCodeToString(keyCode);
            if (keyCodeString != null) {
                sb.append(keyCodeString.replace('_', ' '));
            }

        }

        if (sb.length() == 0) {
            return mContext.getString(R.string.keycombo_unassigned);
        }

        return sb.toString();
    }

    private void appendPlusSignIfNotEmpty(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append(CONCATINATION_STR);
        }
    }

    /**
     * Handles incoming key events. May intercept keys if the user seems to be
     * performing a key combo.
     *
     * @param event The key event.
     * @return {@code true} if the key was intercepted.
     */
    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (!mMatchKeyCombo || mListeners.isEmpty()) {
            return false;
        }

        switch (event.getAction()) {
            case KeyEvent.ACTION_DOWN:
                return onKeyDown(event);
            case KeyEvent.ACTION_MULTIPLE:
                return mHasPartialMatch;
            case KeyEvent.ACTION_UP:
                return onKeyUp();
            default:
                return false;
        }
    }

    @Override
    public boolean processWhenServiceSuspended() {
        return false;
    }

    private boolean onKeyDown(KeyEvent event) {
        mKeyCount++;

        // If the current set of keys is a partial combo, consume the event.
        mHasPartialMatch = false;

        for (KeyCombo keyCombo : mKeyCombos) {
            final int match = keyCombo.matches(event);
            if (match == EXACT_MATCH) {
                for (KeyComboListener listener : mListeners) {
                    if (listener.onComboPerformed(keyCombo.id)) {
                        mPerformedCombo = true;
                        return true;
                    }
                }
            }

            if (match == PARTIAL_MATCH) {
                mHasPartialMatch = true;
            }
        }

        return mHasPartialMatch;
    }

    private boolean onKeyUp() {
        final boolean handled = mPerformedCombo;

        mKeyCount--;

        if (mKeyCount == 0) {
            // The interaction is over, reset the state.
            mPerformedCombo = false;
            mHasPartialMatch = false;
        }

        return handled;
    }

    public interface KeyComboListener {
        public boolean onComboPerformed(int id);
    }

    private static class KeyCombo {
        public final int id;
        public final String key;
        public long keyComboCode;

        public KeyCombo(int id, String key, long keyComboCode) {
            this.id = id;
            this.key = key;
            this.keyComboCode = keyComboCode;
        }

        public int matches(KeyEvent event) {
            int keyCode = event.getKeyCode();
            int metaState = event.getMetaState() & KEY_EVENT_MODIFIER_MASK;

            int targetKeyCode = getKeyCode(keyComboCode);
            int targetMetaState = getModifier(keyComboCode);

            // Handle exact matches first.
            if (metaState == targetMetaState && keyCode == targetKeyCode) {
                return EXACT_MATCH;
            }

            if (targetMetaState != 0 && metaState == 0) {
                return NO_MATCH;
            }

            // Otherwise, all modifiers must be down.
            if (KeyEvent.isModifierKey(keyCode) && targetMetaState != 0 &&
                    (targetMetaState & metaState) != 0) {
                // Partial match.
                return PARTIAL_MATCH;
            }

            // No match.
            return NO_MATCH;
        }
    }

    private static class KeyComboPersister {

        private SharedPreferences mPrefs;

        public KeyComboPersister(Context context) {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        }

        public void saveKeyCombo(String key, long keyComboCode) {
            mPrefs.edit().putLong(key, keyComboCode).apply();
        }

        public boolean contains(String key) {
            return mPrefs.contains(key);
        }

        public Long getKeyComboCode(String key) {
            return mPrefs.getLong(key, KEY_COMBO_CODE_UNASSIGNED);
        }
    }
}
