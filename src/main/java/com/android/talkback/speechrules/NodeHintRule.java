/*
 * Copyright (C) 2012 Google Inc.
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

package com.android.talkback.speechrules;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.InputModeManager;
import com.android.talkback.KeyComboManager;
import com.android.talkback.R;
import com.android.talkback.keyboard.KeyComboModel;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.StringBuilderUtils;

import com.google.android.marvin.talkback.TalkBackService;

import java.util.List;

public interface NodeHintRule {
    /**
     * Determines whether this rule should process the specified node.
     *
     * @param node The node to filter.
     * @return {@code true} if this rule should process the node.
     */
    boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event);

    /**
     * Processes the specified node and returns hint text to speak, or {@code null} if the node
     * should not be spoken.
     *
     * @param context The parent context.
     * @param node The node to process.
     * @return A spoken hint description, or {@code null} if the node should not be spoken.
     */
    CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node);

    class NodeHintHelper {
        private static int sActionResId;
        private static boolean sAllowLongClick;
        private static boolean sAllowCustomActions;

        static {
            updateHints(false /* singleTap */, false /* television */);
        }

        public static void updateHints(boolean forceSingleTap, boolean isTelevision) {
            if (isTelevision) {
                sAllowLongClick = false;
                sAllowCustomActions = false;
                sActionResId = R.string.value_press_select;
            } else {
                sAllowLongClick = true;
                sAllowCustomActions = true;
                if (forceSingleTap) {
                    sActionResId = R.string.value_single_tap;
                } else {
                    sActionResId = R.string.value_double_tap;
                }
            }
        }

        /**
         * Get hint string from each action's label
         * @param context application context
         * @param node node to be examined
         * @return hint strings
         */
        public static CharSequence getDefaultHintString(Context context,
                                                        AccessibilityNodeInfoCompat node) {
            TalkBackService service = TalkBackService.getInstance();
            if (service == null) {
                // If TalkBackService is not available, falls back to touch operation.
                return getCustomHintString(context, node, null, null, false,
                        InputModeManager.INPUT_MODE_TOUCH, null /* keyComboManager */);
            } else {
                return getCustomHintString(context, node, null, null, false,
                        service.getInputModeManager().getInputMode(), service.getKeyComboManager());
            }
        }

        /**
         * Get hint string from each action's label, overriding the default click and long-click
         * action hints.
         * @param context application context
         * @param node node to be examined
         * @param customClickHint the custom hint for the click (or check) action
         * @param customLongClickHint the custom hint for the long-click action
         * @param skipClickHints set to true if we should skip the click/long-click action hints.
         * @return hint strings
         */
        public static CharSequence getCustomHintString(Context context,
                                                       AccessibilityNodeInfoCompat node,
                                                       @Nullable CharSequence customClickHint,
                                                       @Nullable CharSequence customLongClickHint,
                                                       boolean skipClickHints,
                                                       int inputMode,
                                                       @Nullable KeyComboManager keyComboManager) {
            final SpannableStringBuilder builder = new SpannableStringBuilder();

            // Speak custom actions first, if available
            if (sAllowCustomActions) {
                List<AccessibilityActionCompat> customActions =
                        AccessibilityNodeInfoUtils.getCustomActions(node);
                if (!customActions.isEmpty()) {
                    // TODO: Should describe how to get to custom actions
                    StringBuilderUtils.appendWithSeparator(builder,
                            NodeHintHelper.getHintString(context,
                                    R.string.template_hint_custom_actions));
                    for (AccessibilityActionCompat action : customActions) {
                        StringBuilderUtils.appendWithSeparator(builder, action.getLabel());
                    }
                }
            }

            // Get hints from available action's label
            final List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions
                    = node.getActionList();
            boolean hasClickActionHint = false;
            boolean hasLongClickActionHint = false;
            if (actions != null && !actions.isEmpty()) {
                for (AccessibilityNodeInfoCompat.AccessibilityActionCompat action : actions) {
                    if (!AccessibilityNodeInfoUtils.isCustomAction(action) &&
                            !TextUtils.isEmpty(action.getLabel())) {
                        switch (action.getId()) {
                            case AccessibilityNodeInfoCompat.ACTION_CLICK:
                                hasClickActionHint = true;
                                if (!skipClickHints) {
                                    StringBuilderUtils.appendWithSeparator(builder,
                                            getHintForInputMode(context, inputMode,
                                                keyComboManager,
                                                context.getString(
                                                        R.string.keycombo_shortcut_perform_click),
                                                R.string.template_custom_hint_for_actions,
                                                R.string.template_custom_hint_for_actions_keyboard,
                                                action.getLabel()));
                                }
                                break;
                            case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
                                if (sAllowLongClick) {
                                    hasLongClickActionHint = true;
                                    if (!skipClickHints) {
                                        int longClickShortcutId = R.string.
                                                keycombo_shortcut_perform_long_click;
                                        int longClickHintIdForTouch = R.string.
                                                template_custom_hint_for_long_clickable_actions;
                                        int longClickHintIdForKeyboard = R.string.
                                                template_custom_hint_for_actions_keyboard;
                                        StringBuilderUtils.appendWithSeparator(builder,
                                                getHintForInputMode(context, inputMode,
                                                    keyComboManager,
                                                    context.getString(longClickShortcutId),
                                                    longClickHintIdForTouch,
                                                    longClickHintIdForKeyboard,
                                                    action.getLabel()));
                                    }
                                }
                                break;
                            default:
                                StringBuilderUtils.appendWithSeparator(builder, action.getLabel());
                                break;
                        }
                    }
                }
            }

            if (skipClickHints) {
                return builder;
            }

            boolean checkable = node.isCheckable();
            boolean clickable = AccessibilityNodeInfoUtils.isClickable(node);
            boolean longClickable = AccessibilityNodeInfoUtils.isLongClickable(node);

            // Add a click hint if there's a click action but no corresponding label.
            if ((clickable || checkable) && !hasClickActionHint) {
                // If a custom click hint is provided, use that; otherwise choose a default hint
                // depending on whether we have a checkable node or not.
                if (!TextUtils.isEmpty(customClickHint)) {
                    StringBuilderUtils.appendWithSeparator(builder, customClickHint);
                } else if (checkable) {
                    StringBuilderUtils.appendWithSeparator(builder,
                            getHintForInputMode(context, inputMode, keyComboManager,
                                context.getString(R.string.keycombo_shortcut_perform_click),
                                R.string.template_hint_checkable,
                                R.string.template_hint_checkable_keyboard,
                                null /* label */));
                } else {
                    StringBuilderUtils.appendWithSeparator(builder,
                            getHintForInputMode(context, inputMode, keyComboManager,
                                context.getString(R.string.keycombo_shortcut_perform_click),
                                R.string.template_hint_clickable,
                                R.string.template_hint_clickable_keyboard,
                                null /* label */));
                }
            }

            // Add a long click hint if there's a long-click action but no corresponding label.
            if (sAllowLongClick && longClickable && !hasLongClickActionHint) {
                // If a custom long-click hint is provided, use that; otherwise use default.
                if (!TextUtils.isEmpty(customLongClickHint)) {
                    StringBuilderUtils.appendWithSeparator(builder, customLongClickHint);
                } else {
                    StringBuilderUtils.appendWithSeparator(builder,
                            getHintForInputMode(context, inputMode, keyComboManager,
                                context.getString(R.string.keycombo_shortcut_perform_long_click),
                                R.string.template_hint_long_clickable,
                                R.string.template_hint_long_clickable_keyboard,
                                null /* label */));
                }
            }

            return builder;
        }

        // TODO: make this method private and provide different interface to callers of this
        // method, i.e. refactor interfaces of this class considering keyboard based navigation
        // hints.
        public static CharSequence getHintForInputMode(
                Context context, int inputMode, @Nullable KeyComboManager keyComboManager,
                String keyboardShortcutKey, int templateResourceIdForTouch,
                int templateResourceIdForKeyboard, @Nullable CharSequence label) {
            // If keyCombo is not available (e.g. no key combo is assigned, keyComboManager is not
            // provided), falls back to touch operation hint.
            boolean doesProvideKeyboardHint = inputMode == InputModeManager.INPUT_MODE_KEYBOARD &&
                    keyComboManager != null &&
                    keyComboManager.getKeyComboModel().getKeyComboCodeForKey(keyboardShortcutKey) !=
                    KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;

            String action;
            int templateResourceId;
            if (doesProvideKeyboardHint) {
                KeyComboModel keyComboModel = keyComboManager.getKeyComboModel();
                long keyComboCode = keyComboModel.getKeyComboCodeForKey(keyboardShortcutKey);
                long keyComboCodeWithTriggerModifier = KeyComboManager.getKeyComboCode(
                        KeyComboManager.getModifier(keyComboCode) |
                        keyComboModel.getTriggerModifier(),
                        KeyComboManager.getKeyCode(keyComboCode));

                action = keyComboManager.getKeyComboStringRepresentation(
                        keyComboCodeWithTriggerModifier);
                templateResourceId = templateResourceIdForKeyboard;
            } else {
                action = context.getString(sActionResId);
                templateResourceId = templateResourceIdForTouch;
            }

            if (label == null) {
                return context.getString(templateResourceId, action);
            } else {
                return context.getString(templateResourceId, action, label);
            }
        }

        /**
         * Returns a hint string populated with the version-appropriate action
         * string.
         *
         * @param context The parent context.
         * @param hintResId The hint string's resource identifier.
         * @return A populated hint string.
         */
        public static CharSequence getHintString(Context context, int hintResId) {
            return context.getString(hintResId, context.getString(sActionResId));
        }
    }
}
