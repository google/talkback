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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.KeyEvent;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Trigger accessibility action when key mapped to that action in preferences is pressed. There can
 * be multiple keys mapped to single action (i.e. left half of keyboard).
 */
public class KeyboardAction {
  private final KeyboardActionRunnable mAction;
  private int mEnabledResId = 0;
  private boolean mEnabledDefault = false;
  private long mDebounceTimeMs = 0;
  private long mLastProcessedKeyTimeMs = 0;
  private boolean mPressOnRelease = false;
  private final int mAssignedKeysResId;
  private Set<Long> mTriggerKeys = new HashSet<>();
  private final Set<Long> mPressedKeys = new HashSet<>();

  private final Handler mHandler = new Handler();

  /**
   * @param assignedKeysResId The preference resource id where list of keys is stored
   * @param action The accessibility action to trigger when this keyboard switch is pressed
   */
  public KeyboardAction(int assignedKeysResId, KeyboardActionRunnable action) {
    mAssignedKeysResId = assignedKeysResId;
    mAction = action;
  }

  /**
   * @param enabledResId The preference resource id where on/off guard is stored
   * @param enabledDefault The default value for guard
   */
  public void setEnableGuard(int enabledResId, boolean enabledDefault) {
    mEnabledResId = enabledResId;
    mEnabledDefault = enabledDefault;
  }

  /**
   * Process keys assigned to trigger mAction. Action is sent to processor on the first key pressed
   * down. No other key in the group will trigger until after all keys in that group have been
   * released.
   *
   * @param event The hardware keyboard event
   * @param keyboardActionListener The listener that will take care of executing action at proper
   *     time
   * @return If true then the event was consumed and should not be delivered to applications,
   *     otherwise it will be delivered as usual
   */
  public boolean onKeyEvent(KeyEvent event, KeyboardActionListener keyboardActionListener) {
    long extendedKeyCode = KeyAssignmentUtils.keyEventToExtendedKeyCode(event);
    if (!mTriggerKeys.contains(extendedKeyCode)) {
      return false;
    }

    // Post the rest of the processing to a handler so we can let the system know that we're
    // processing the event before it times out.
    long eventTime = event.getEventTime();
    if ((event.getAction() == KeyEvent.ACTION_DOWN)
        && (eventTime - mLastProcessedKeyTimeMs >= mDebounceTimeMs)) {
      if (!mPressOnRelease && mPressedKeys.isEmpty()) {
        performAction(keyboardActionListener, eventTime);
      }
      mPressedKeys.add(extendedKeyCode);
    } else if (event.getAction() == KeyEvent.ACTION_UP) {
      mPressedKeys.remove(extendedKeyCode);
      if (mPressOnRelease && mPressedKeys.isEmpty()) {
        performAction(keyboardActionListener, eventTime);
      }
    }
    // Ignore repeated keys for now
    return true;
  }

  private void performAction(KeyboardActionListener keyboardActionListener, long eventTime) {
    mAction.setEventTime(eventTime);
    mHandler.post(mAction);
    if (keyboardActionListener != null) {
      keyboardActionListener.onKeyboardAction(mAssignedKeysResId);
    }
    mLastProcessedKeyTimeMs = eventTime;
  }

  /** Read the key mapping from the default preferences for context. */
  public void refreshPreferences(Context context) {
    if (mEnabledResId != 0) {
      final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
      if (!prefs.getBoolean(context.getString(mEnabledResId), mEnabledDefault)) {
        setTriggerKeys(Collections.<Long>emptySet());
        return;
      }
    }
    setTriggerKeys(KeyAssignmentUtils.getKeyCodesForPreference(context, mAssignedKeysResId));

    mDebounceTimeMs = SwitchAccessPreferenceActivity.getDebounceTimeMs(context);
    mPressOnRelease = SwitchAccessPreferenceActivity.isPressOnReleaseEnabled(context);
  }

  void setTriggerKeys(Set<Long> triggerKeys) {
    mTriggerKeys = triggerKeys;
    mPressedKeys.retainAll(mTriggerKeys); // Remove keys that are not in the new trigger set
  }

  /**
   * Listener that is notified when a key press performed by the user should result in an action.
   */
  public interface KeyboardActionListener {
    public void onKeyboardAction(int preferenceIdForAction);
  }
}
