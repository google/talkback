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

package com.google.android.accessibility.switchaccess.keyboardactions;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.KeyEvent;
import com.google.android.accessibility.switchaccess.PerformanceMonitor;
import com.google.android.accessibility.switchaccess.PerformanceMonitor.KeyPressAction;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.keyassignment.KeyAssignmentUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Trigger accessibility action when key mapped to that action in preferences is pressed. There can
 * be multiple keys mapped to single action (i.e. left half of keyboard).
 */
public class KeyboardAction {
  private final Runnable action;
  private int enabledResId = 0;
  private boolean enabledDefault = false;
  private long debounceTimeMs = 0;
  private long lastProcessedKeyTimeMs = 0;
  private boolean pressOnRelease = false;
  private final int assignedKeysResId;
  private Set<Long> triggerKeys = new HashSet<>();
  private final Set<Long> pressedKeys = new HashSet<>();

  private final Handler handler = new Handler();
  // The amount of time, in milliseconds, for which the user needs to press and hold the volume
  // buttons in order to disable the screen switch.
  private static final int DISABLE_SCREEN_SWITCH_DELAY = 3000;

  private static final long EXTENDED_KEY_CODE_VOLUME_DOWN =
      KeyAssignmentUtils.keyEventToExtendedKeyCode(
          new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN));
  private static final long EXTENDED_KEY_CODE_VOLUME_UP =
      KeyAssignmentUtils.keyEventToExtendedKeyCode(
          new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP));

  // Runnable will disable the screen-as-a-switch functionality when run.
  private Runnable disableScreenSwitchRunnable;

  /**
   * @param assignedKeysResId The preference resource id where list of keys is stored
   * @param action The accessibility action to trigger when this keyboard switch is pressed
   */
  public KeyboardAction(int assignedKeysResId, Runnable action) {
    this.assignedKeysResId = assignedKeysResId;
    this.action = action;
  }

  /**
   * @param enabledResId The preference resource id where on/off guard is stored
   * @param enabledDefault The default value for guard
   */
  public void setEnableGuard(int enabledResId, boolean enabledDefault) {
    this.enabledResId = enabledResId;
    this.enabledDefault = enabledDefault;
  }

  /**
   * Process keys assigned to trigger action. Action is sent to processor on the first key pressed
   * down. No other key in the group will trigger until after all keys in that group have been
   * released.
   *
   * @param event The hardware keyboard event
   * @param keyboardActionListener The listener that will take care of executing action at proper
   *     time
   * @param context The current context.
   * @return If true then the event was consumed and should not be delivered to applications,
   *     otherwise it will be delivered as usual
   */
  public boolean onKeyEvent(
      KeyEvent event, KeyboardActionListener keyboardActionListener, Context context) {
    long extendedKeyCode = KeyAssignmentUtils.keyEventToExtendedKeyCode(event);
    maybeHandleExitScreenSwitchKeyEvent(event, context);

    if (!triggerKeys.contains(extendedKeyCode)) {
      return false;
    }

    // Post the rest of the processing to a handler so we can let the system know that we're
    // processing the event before it times out.
    long eventTime = event.getEventTime();
    if ((event.getAction() == KeyEvent.ACTION_DOWN)
        && (eventTime - lastProcessedKeyTimeMs >= debounceTimeMs)) {
      if (!pressOnRelease && pressedKeys.isEmpty()) {
        performAction(keyboardActionListener, eventTime);
      }
      pressedKeys.add(extendedKeyCode);
    } else if (event.getAction() == KeyEvent.ACTION_UP) {
      pressedKeys.remove(extendedKeyCode);
      if (pressOnRelease && pressedKeys.isEmpty()) {
        performAction(keyboardActionListener, eventTime);
      }
    }
    // Ignore repeated keys for now
    return true;
  }

  private void performAction(KeyboardActionListener keyboardActionListener, long eventTime) {
    PerformanceMonitor.getOrCreateInstance()
        .startNewTimerEvent(KeyPressAction.KEYBOARD_ACTION_RUNNABLE_EXECUTED);
    handler.post(action);
    if (keyboardActionListener != null) {
      keyboardActionListener.onKeyboardAction(assignedKeysResId);
    }
    lastProcessedKeyTimeMs = eventTime;
  }

  private void maybeHandleExitScreenSwitchKeyEvent(KeyEvent event, Context context) {
    if (!SwitchAccessPreferenceUtils.isScreenSwitchEnabled(context)) {
      return;
    }

    long extendedKeyCode = KeyAssignmentUtils.keyEventToExtendedKeyCode(event);
    if (!((extendedKeyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        || (extendedKeyCode == KeyEvent.KEYCODE_VOLUME_UP))) {
      return;
    }

    // If we update pressedKeys here without checking if the keycode is in triggerKeys, no action
    // can be performed using volume buttons.
    if (!triggerKeys.contains(extendedKeyCode)) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        pressedKeys.add(extendedKeyCode);
      } else {
        pressedKeys.remove(extendedKeyCode);
      }
    }

    if ((disableScreenSwitchRunnable != null) && (event.getAction() == KeyEvent.ACTION_UP)) {
      handler.removeCallbacks(disableScreenSwitchRunnable);
    }

    if ((pressedKeys.contains(EXTENDED_KEY_CODE_VOLUME_DOWN)
            && (extendedKeyCode == EXTENDED_KEY_CODE_VOLUME_UP))
        || (pressedKeys.contains(EXTENDED_KEY_CODE_VOLUME_UP)
            && (extendedKeyCode == EXTENDED_KEY_CODE_VOLUME_DOWN))) {
      if (disableScreenSwitchRunnable == null) {
        disableScreenSwitchRunnable =
            () -> {
              if (pressedKeys.contains(EXTENDED_KEY_CODE_VOLUME_DOWN)
                  && pressedKeys.contains(EXTENDED_KEY_CODE_VOLUME_UP)) {
                SwitchAccessPreferenceUtils.disableScreenSwitch(context);
              }
            };
      }
      handler.postDelayed(disableScreenSwitchRunnable, DISABLE_SCREEN_SWITCH_DELAY);
    }
  }

  /** Read the key mapping from the default preferences for context. */
  public void refreshPreferences(Context context) {
    if (enabledResId != 0) {
      final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
      if (!prefs.getBoolean(context.getString(enabledResId), enabledDefault)) {
        setTriggerKeys(Collections.emptySet());
        return;
      }
    }
    setTriggerKeys(KeyAssignmentUtils.getKeyCodesForPreference(context, assignedKeysResId));

    debounceTimeMs = SwitchAccessPreferenceUtils.getDebounceTimeMs(context);
    pressOnRelease = SwitchAccessPreferenceUtils.isPressOnReleaseEnabled(context);
  }

  private void setTriggerKeys(Set<Long> triggerKeys) {
    this.triggerKeys = triggerKeys;
    pressedKeys.retainAll(this.triggerKeys); // Remove keys that are not in the new trigger set
  }

  /**
   * Listener that is notified when a key press performed by the user should result in an action.
   */
  public interface KeyboardActionListener {
    void onKeyboardAction(int preferenceIdForAction);
  }
}
