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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Trigger accessibility action when key mapped to that action in preferences is pressed.
 * There can be multiple keys mapped to single action (i.e. left half of keyboard).
 */
public class KeyboardAction {
    private final Runnable mAction;
    private int mEnabledResId = 0;
    private boolean mEnabledDefault = false;
    private final int mAssignedKeysResId;
    private Set<Long> mTriggerKeys = new HashSet<>();
    private final Set<Long> mPressedKeys = new HashSet<>();

    /**
     * @param assignedKeysResId - preference resource id where list of keys is stored
     * @param action - accessibility action to trigger when this keyboard switch is pressed
     */
    public KeyboardAction(int assignedKeysResId, Runnable action) {
        mAssignedKeysResId = assignedKeysResId;
        mAction = action;
    }

    /**
     * @param enabledResId - preference resource id where on/off guard is stored
     * @param enabledDefault - default value for guard
     */
    public void setEnableGuard(int enabledResId, boolean enabledDefault) {
        mEnabledResId = enabledResId;
        mEnabledDefault = enabledDefault;
    }

    /**
     * Process keys assigned to trigger mAction.
     * Action is sent to processor on the first key pressed down.
     * No other key in the group will trigger until after all keys in that group have been released.
     * @param event - hardware keyboard event
     * @param actionProcessor - will take care of executing action at proper time
     * @return If true then the event was consumed and should not be delivered to applications,
     * otherwise it will be delivered as usual.
     */
    public boolean onKeyEvent(KeyEvent event, ActionProcessor actionProcessor,
            KeyboardActionListener keyboardActionListener) {
        long extendedKeyCode = KeyComboPreference.keyEventToExtendedKeyCode(event);
        if (!mTriggerKeys.contains(extendedKeyCode)) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (mPressedKeys.isEmpty()) {
                actionProcessor.process(mAction);
                if (keyboardActionListener != null) {
                    keyboardActionListener.onKeyboardAction(mAssignedKeysResId);
                }
            }
            mPressedKeys.add(extendedKeyCode);
        }
        else if (event.getAction() == KeyEvent.ACTION_UP) {
            mPressedKeys.remove(extendedKeyCode);
        }
        // ignore repeated keys for now
        return true;
    }

    /**
     * Read key mapping from default preferences for context
     */
    public void refreshPreferences(Context context) {
        if (mEnabledResId != 0) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean(context.getString(mEnabledResId), mEnabledDefault)) {
                setTriggerKeys(Collections.<Long>emptySet());
                return;
            }
        }
        setTriggerKeys(KeyComboPreference.getKeyCodesForPreference(context, mAssignedKeysResId));
    }

    void setTriggerKeys(Set<Long> triggerKeys) {
        mTriggerKeys = triggerKeys;
        mPressedKeys.retainAll(mTriggerKeys); // remove keys not in new trigger set
    }

    public interface KeyboardActionListener {
        public void onKeyboardAction(int preferenceIdForAction);
    }
}
