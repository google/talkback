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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.android.talkback.R;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.StringBuilderUtils;

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

        static {
            updateActionResId(false);
        }

        public static void updateActionResId(boolean forceSingleTap) {
            if (forceSingleTap) {
                sActionResId = R.string.value_single_tap;
            } else {
                sActionResId = R.string.value_double_tap;
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
            final SpannableStringBuilder builder = new SpannableStringBuilder();

            // Get hints from available action's label
            final List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions
                    = node.getActionList();
            boolean hasClickActionHint = false;
            boolean hasLongClickActionHint = false;
            if (actions != null && !actions.isEmpty()) {
                for(AccessibilityNodeInfoCompat.AccessibilityActionCompat action: actions) {
                    if (!AccessibilityNodeInfoUtils.isCustomAction(action) &&
                        !TextUtils.isEmpty(action.getLabel())) {
                        switch (action.getId()) {
                            case AccessibilityNodeInfoCompat.ACTION_CLICK:
                                hasClickActionHint = true;
                                break;
                            case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
                                hasLongClickActionHint = true;
                                break;
                            default:
                                // left bank on purpose
                        }
                        StringBuilderUtils.appendWithSeparator(builder, action.getLabel());
                    }
                }
            }

            // Add default clickable hint if action doesn't have corresponding label
            // Don't read both the checkable AND clickable hints!
            if (node.isCheckable()) {
                StringBuilderUtils.appendWithSeparator(builder,
                        NodeHintHelper.getHintString(context, R.string.template_hint_checkable));
            } else if (AccessibilityNodeInfoUtils.isClickable(node)
                    && !hasClickActionHint) {
                StringBuilderUtils.appendWithSeparator(builder,
                        NodeHintHelper.getHintString(context, R.string.template_hint_clickable));
            }

            // Add default long click hint if action doesn't have corresponding label
            if (AccessibilityNodeInfoUtils.isLongClickable(node)
                    && !hasLongClickActionHint) {
                StringBuilderUtils.appendWithSeparator(builder,
                        NodeHintHelper.getHintString(context,
                                R.string.template_hint_long_clickable));
            }

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

            return builder;
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
