/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.view.accessibility.AccessibilityEvent;

interface NodeSpeechRule {
    /**
     * Determines whether this rule should process the specified node.
     *
     * @param node The node to filter.
     * @param event The source event, may be {@code null} when called with non-source nodes.
     * @return {@code true} if this rule should process the node.
     */
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event);

    /**
     * Processes the specified node and returns text to speak, or {@code null}
     * if the node should not be spoken.
     *
     * @param context The parent context.
     * @param node The node to process.
     * @param event The source event, may be {@code null} when called with non-source nodes.
     * @return A spoken description, or {@code null} if the node should not be spoken.
     */
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node,
            AccessibilityEvent event);
}
