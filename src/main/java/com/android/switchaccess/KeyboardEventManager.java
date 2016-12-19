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

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.KeyEvent;
import com.android.talkback.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Manage key events for Switch Access, dispatching them as needed to
 * the option manager or global actions as appropriate.
 *
 * This version only handles NEXT and CLICK as options 0 and 1 for
 * a decision tree.
 */
public class KeyboardEventManager {

    private final List<KeyboardAction> mKeyboardActions = new ArrayList<>();

    public KeyboardEventManager(final AccessibilityService service,
            final OptionManager optionManager, final AutoScanController autoScanController) {
        for (final KeyboardBasedGlobalAction globalAction : KeyboardBasedGlobalAction.values()) {
            mKeyboardActions
                    .add(new KeyboardAction(globalAction.getPreferenceResId(), new Runnable() {
                        @Override
                        public void run() {
                            service.performGlobalAction(globalAction.getGlobalActionId());
                        }
                    }));
        }

        KeyboardAction autoScanKeyboardAction = new KeyboardAction(
                R.string.pref_key_mapped_to_auto_scan_key,
                new Runnable() {
                    @Override
                    public void run() {
                        autoScanController.autoScanActivated(false);
                    }
                });
        autoScanKeyboardAction.setEnableGuard(R.string.pref_key_auto_scan_enabled,
                Boolean.parseBoolean(service.getString(R.string.pref_auto_scan_default_value)));
        mKeyboardActions.add(autoScanKeyboardAction);

        KeyboardAction reverseAutoScanKeyboardAction = new KeyboardAction(
                R.string.pref_key_mapped_to_reverse_auto_scan_key,
                new Runnable() {
                    @Override
                    public void run() {
                        autoScanController.autoScanActivated(true);
                    }
                });
        autoScanKeyboardAction.setEnableGuard(R.string.pref_key_auto_scan_enabled,
                Boolean.parseBoolean(service.getString(R.string.pref_auto_scan_default_value)));
        mKeyboardActions.add(reverseAutoScanKeyboardAction);

        mKeyboardActions
                .add(new KeyboardAction(R.string.pref_key_mapped_to_click_key, new Runnable() {
                    @Override
                    public void run() {
                        optionManager.selectOption(OptionManager.OPTION_INDEX_CLICK);
                    }
                }));
        mKeyboardActions
                .add(new KeyboardAction(R.string.pref_key_mapped_to_next_key, new Runnable() {
                    @Override
                    public void run() {
                        optionManager.selectOption(OptionManager.OPTION_INDEX_NEXT);
                    }
                }));
        mKeyboardActions
                .add(new KeyboardAction(R.string.pref_key_mapped_to_switch_3_key, new Runnable() {
                    @Override
                    public void run() {
                        optionManager.selectOption(2);
                    }
                }));
        mKeyboardActions
                .add(new KeyboardAction(R.string.pref_key_mapped_to_switch_4_key, new Runnable() {
                    @Override
                    public void run() {
                        optionManager.selectOption(3);
                    }
                }));
        mKeyboardActions
                .add(new KeyboardAction(R.string.pref_key_mapped_to_switch_5_key, new Runnable() {
                    @Override
                    public void run() {
                        optionManager.selectOption(4);
                    }
                }));
        mKeyboardActions
                .add(new KeyboardAction(R.string.pref_key_mapped_to_previous_key, new Runnable() {
                    @Override
                    public void run() {
                        optionManager.moveToParent(true);
                    }
                }));
        mKeyboardActions
                .add(new KeyboardAction(R.string.pref_key_mapped_to_long_click_key, new Runnable() {
                    @Override
                    public void run() {
                        optionManager.performLongClick();
                    }
                }));
        mKeyboardActions.add(
                new KeyboardAction(R.string.pref_key_mapped_to_scroll_forward_key, new Runnable() {
                    @Override
                    public void run() {
                        optionManager.performScrollAction(
                                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                    }
                }));
        mKeyboardActions.add(
                new KeyboardAction(R.string.pref_key_mapped_to_scroll_backward_key, new Runnable() {
                    @Override
                    public void run() {
                        optionManager.performScrollAction(
                                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                    }
                }));

        reloadPreferences(service);
    }

    public boolean onKeyEvent(KeyEvent keyEvent, ActionProcessor actionProcessor,
            KeyboardAction.KeyboardActionListener keyboardActionListener) {
        for (KeyboardAction keyboardAction : mKeyboardActions) {
            if (keyboardAction.onKeyEvent(keyEvent, actionProcessor, keyboardActionListener)) {
                return true;
            }
        }
        return false;
    }

    /**
     * reloadPreferences should be called when user preferences mapping keys to  action changed.
     * It refreshes trigger keys for all keyboard actions.
     * @param context Activity context
     */
    public void reloadPreferences(Context context) {
        for (KeyboardAction keyboardAction : mKeyboardActions) {
            keyboardAction.refreshPreferences(context);
        }
    }
}
