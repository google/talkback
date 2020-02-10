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
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.KeyEvent;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.AutoScanController;
import com.google.android.accessibility.switchaccess.AutoScanController.ScanDirection;
import com.google.android.accessibility.switchaccess.OptionManager;
import com.google.android.accessibility.switchaccess.OptionManager.ScanEvent;
import com.google.android.accessibility.switchaccess.OptionManager.ScanStateChangeTrigger;
import com.google.android.accessibility.switchaccess.PerformanceMonitor;
import com.google.android.accessibility.switchaccess.PerformanceMonitor.KeyPressAction;
import com.google.android.accessibility.switchaccess.PerformanceMonitor.KeyPressEvent;
import com.google.android.accessibility.switchaccess.PointScanManager;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.keyboardactions.KeyboardAction.KeyboardActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Manage key events for Switch Access, dispatching them as needed to the option manager or global
 * actions as appropriate.
 *
 * <p>This version only handles NEXT and CLICK as options 0 and 1 for a decision tree.
 */
public class KeyboardEventManager {

  private final List<KeyboardAction> keyboardActions = new ArrayList<>();

  public KeyboardEventManager(
      final SwitchAccessService service,
      final OptionManager optionManager,
      final AutoScanController autoScanController,
      final PointScanManager pointScanManager) {
    for (final KeyboardBasedGlobalAction globalAction : KeyboardBasedGlobalAction.values()) {
      keyboardActions.add(
          new KeyboardAction(
              globalAction.getPreferenceResId(),
              () -> {
                logKeyboardActionRunnableExecuted();
                PerformanceMonitor.getOrCreateInstance()
                    .startNewTimerEvent(PerformanceMonitor.KeyPressAction.GLOBAL_ACTION);
                service.performGlobalAction(globalAction.getGlobalActionId());
                PerformanceMonitor.getOrCreateInstance()
                    .stopTimerEvent(
                        PerformanceMonitor.KeyPressAction.GLOBAL_ACTION,
                        false /* appendScanningMethod */);
                service.onUserInitiatedScreenChange();
              }));
    }

    KeyboardAction autoScanKeyboardAction =
        new KeyboardAction(
            R.string.pref_key_mapped_to_auto_scan_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                pointScanManager.onSelect(ScanStateChangeTrigger.KEY_POINT_SCAN);
              } else {
                PerformanceMonitor.getOrCreateInstance()
                    .startNewTimerEvent(PerformanceMonitor.KeyPressAction.UNKNOWN_KEY);
                // We should only start autoscan if point scanning is disabled.
                ScanEvent scanEvent = autoScanController.startAutoScan(ScanDirection.FORWARD);
                logKeyEventOnceKnownFromScanEvent(
                    scanEvent,
                    PerformanceMonitor.KeyPressAction.SCAN_START,
                    PerformanceMonitor.KeyPressAction.ITEM_SELECTED);
                service.onUserInitiatedScreenChange();
              }
            });
    autoScanKeyboardAction.setEnableGuard(
        R.string.pref_key_auto_scan_enabled,
        Boolean.parseBoolean(service.getString(R.string.pref_auto_scan_default_value)));
    keyboardActions.add(autoScanKeyboardAction);

    KeyboardAction reverseAutoScanKeyboardAction =
        new KeyboardAction(
            R.string.pref_key_mapped_to_reverse_auto_scan_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                // As a user can choose to assign keys only to Reverse Auto-scan and
                // have a working configuration, the user should still have a working
                // configuration after entering Point scan.
                pointScanManager.onSelect(ScanStateChangeTrigger.KEY_POINT_SCAN);
              } else {
                PerformanceMonitor.getOrCreateInstance()
                    .startNewTimerEvent(PerformanceMonitor.KeyPressAction.UNKNOWN_KEY);
                ScanEvent scanEvent = autoScanController.startAutoScan(ScanDirection.REVERSE);
                logKeyEventOnceKnownFromScanEvent(
                    scanEvent,
                    PerformanceMonitor.KeyPressAction.SCAN_REVERSE_START,
                    PerformanceMonitor.KeyPressAction.ITEM_SELECTED);
                service.onUserInitiatedScreenChange();
              }
            });
    autoScanKeyboardAction.setEnableGuard(
        R.string.pref_key_auto_scan_enabled,
        Boolean.parseBoolean(service.getString(R.string.pref_auto_scan_default_value)));
    keyboardActions.add(reverseAutoScanKeyboardAction);

    keyboardActions.add(
        new KeyboardAction(
            R.string.pref_key_mapped_to_click_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                pointScanManager.onSelect(ScanStateChangeTrigger.KEY_POINT_SCAN);
              } else {
                PerformanceMonitor.getOrCreateInstance()
                    .startNewTimerEvent(PerformanceMonitor.KeyPressAction.UNKNOWN_KEY);
                ScanStateChangeTrigger trigger =
                    (SwitchAccessPreferenceUtils.isGroupSelectionEnabled(service))
                        ? ScanStateChangeTrigger.KEY_GROUP_1
                        : ScanStateChangeTrigger.KEY_SELECT;
                ScanEvent scanEvent =
                    optionManager.selectOption(OptionManager.OPTION_INDEX_CLICK, trigger);
                logKeyEventOnceKnownFromScanEvent(
                    scanEvent,
                    PerformanceMonitor.KeyPressAction.SCAN_START,
                    PerformanceMonitor.KeyPressAction.ITEM_SELECTED);
                service.onUserInitiatedScreenChange();
              }
            }));
    keyboardActions.add(
        new KeyboardAction(
            R.string.pref_key_mapped_to_next_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (!SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                PerformanceMonitor.getOrCreateInstance()
                    .startNewTimerEvent(PerformanceMonitor.KeyPressAction.UNKNOWN_KEY);
                ScanStateChangeTrigger trigger =
                    SwitchAccessPreferenceUtils.isGroupSelectionEnabled(service)
                        ? ScanStateChangeTrigger.KEY_GROUP_2
                        : ScanStateChangeTrigger.KEY_NEXT;
                ScanEvent scanEvent =
                    optionManager.selectOption(OptionManager.OPTION_INDEX_NEXT, trigger);
                logKeyEventOnceKnownFromScanEvent(
                    scanEvent,
                    PerformanceMonitor.KeyPressAction.SCAN_START,
                    PerformanceMonitor.KeyPressAction.SCAN_MOVE_FORWARD);
                if (scanEvent == ScanEvent.SCAN_STARTED) {
                  service.onUserInitiatedScreenChange();
                }
              }
            }));
    keyboardActions.add(
        new KeyboardAction(
            R.string.pref_key_mapped_to_switch_3_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (!SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                if (optionManager.selectOption(2, ScanStateChangeTrigger.KEY_GROUP_3)
                    == ScanEvent.SCAN_STARTED) {
                  service.onUserInitiatedScreenChange();
                }
              }
            }));
    keyboardActions.add(
        new KeyboardAction(
            R.string.pref_key_mapped_to_switch_4_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (!SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                if (optionManager.selectOption(3, ScanStateChangeTrigger.KEY_GROUP_4)
                    == ScanEvent.SCAN_STARTED) {
                  service.onUserInitiatedScreenChange();
                }
              }
            }));
    keyboardActions.add(
        new KeyboardAction(
            R.string.pref_key_mapped_to_switch_5_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (!SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                if (optionManager.selectOption(4, ScanStateChangeTrigger.KEY_GROUP_5)
                    == ScanEvent.SCAN_STARTED) {
                  service.onUserInitiatedScreenChange();
                }
              }
            }));
    keyboardActions.add(
        new KeyboardAction(
            R.string.pref_key_mapped_to_previous_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                pointScanManager.undo();
              } else {
                PerformanceMonitor.getOrCreateInstance()
                    .startNewTimerEvent(PerformanceMonitor.KeyPressAction.UNKNOWN_KEY);
                ScanEvent scanEvent =
                    optionManager.moveToParent(true, ScanStateChangeTrigger.KEY_PREVIOUS);
                logKeyEventOnceKnownFromScanEvent(
                    scanEvent,
                    PerformanceMonitor.KeyPressAction.SCAN_REVERSE_START,
                    PerformanceMonitor.KeyPressAction.SCAN_MOVE_BACKWARD);
                if (scanEvent == ScanEvent.SCAN_STARTED) {
                  service.onUserInitiatedScreenChange();
                }
              }
            }));
    keyboardActions.add(
        new KeyboardAction(
            R.string.pref_key_mapped_to_long_click_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (!SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                optionManager.performLongClick();
              }
              service.onUserInitiatedScreenChange();
            }));
    keyboardActions.add(
        new KeyboardAction(
            R.string.pref_key_mapped_to_scroll_forward_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (!SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                optionManager.performScrollAction(
                    AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
              }
              service.onUserInitiatedScreenChange();
            }));
    keyboardActions.add(
        new KeyboardAction(
            R.string.pref_key_mapped_to_scroll_backward_key,
            () -> {
              logKeyboardActionRunnableExecuted();
              if (!SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
                optionManager.performScrollAction(
                    AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                service.onUserInitiatedScreenChange();
              }
            }));

    reloadPreferences(service);
  }

  /**
   * Process keys assigned to actions.
   *
   * @param keyEvent The hardware keyboard event
   * @param keyboardActionListener The listener that will take care of executing action
   * @param context The current context
   * @return {@code true} if the event was consumed and should not be delivered to other
   *     applications.
   */
  public boolean onKeyEvent(
      KeyEvent keyEvent, KeyboardActionListener keyboardActionListener, Context context) {
    for (KeyboardAction keyboardAction : keyboardActions) {
      if (keyboardAction.onKeyEvent(keyEvent, keyboardActionListener, context)) {
        PerformanceMonitor.getOrCreateInstance()
            .stopTimerEvent(KeyPressEvent.ASSIGNED_KEY_DETECTED, false /* appendScanningMethod */);
        return true;
      }
    }
    PerformanceMonitor.getOrCreateInstance()
        .stopTimerEvent(KeyPressEvent.UNASSIGNED_KEY_DETECTED, true /* appendScanningMethod */);
    return false;
  }

  /**
   * reloadPreferences should be called when user preferences change. It refreshes trigger keys for
   * all keyboard actions as well as the debounce time.
   *
   * @param context Activity context
   */
  public void reloadPreferences(Context context) {
    for (KeyboardAction keyboardAction : keyboardActions) {
      keyboardAction.refreshPreferences(context);
    }
  }

  private void logKeyEventOnceKnownFromScanEvent(
      ScanEvent scanEvent,
      PerformanceMonitor.KeyPressAction scanJustStartedKeyPressAction,
      PerformanceMonitor.KeyPressAction scanContinueKeyPressAction) {

    switch (scanEvent) {
      case SCAN_STARTED:
        PerformanceMonitor.getOrCreateInstance()
            .stopTimerEvent(scanJustStartedKeyPressAction, true /* appendScanningMethod */);
        break;
      case SCAN_CONTINUED:
        PerformanceMonitor.getOrCreateInstance()
            .stopTimerEvent(scanContinueKeyPressAction, true /* appendScanningMethod */);
        break;
      case IGNORED_EVENT:
        PerformanceMonitor.getOrCreateInstance()
            .cancelTimerEvent(
                PerformanceMonitor.KeyPressAction.UNKNOWN_KEY, true /* appendScanningMethod */);
        break;
    }
  }

  private void logKeyboardActionRunnableExecuted() {
    PerformanceMonitor.getOrCreateInstance()
        .stopTimerEvent(
            KeyPressAction.KEYBOARD_ACTION_RUNNABLE_EXECUTED, false /* appendScanningMethod */);
  }
}

