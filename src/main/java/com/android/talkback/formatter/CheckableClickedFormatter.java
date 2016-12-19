/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.talkback.formatter;

import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.R;
import com.android.utils.Role;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.utils.AccessibilityEventUtils;

/**
 * Filters and formats click events from checkable items.
 *
 * @deprecated Only use this class for legacy support in KitKat or earlier. For new development
 * targeting Lollipop or later, please use the {@link ClickFormatter} instead.
 */
@Deprecated
public class CheckableClickedFormatter implements EventSpeechRule.AccessibilityEventFilter,
        EventSpeechRule.AccessibilityEventFormatter {

    private long mClickedTime = -1;
    private AccessibilityNodeInfoCompat mClickedNode;

    // accept and format are not called from the same class instance
    private static AccessibilityNodeInfoCompat sCachedCheckableNode;

    private boolean findClickedCheckableNode(AccessibilityNodeInfoCompat source) {
        if (source == null) return false;

        if (source.equals(mClickedNode)) {
            boolean ret = findCheckableNode(source);
            if (mClickedNode != null) {
                mClickedNode.recycle();
                mClickedNode = null;
            }

            mClickedTime = -1;
            return ret;
        }

        int children = source.getChildCount();
        for (int i = 0; i < children; i++) {
            AccessibilityNodeInfoCompat node = source.getChild(i);
            if (findClickedCheckableNode(node)) {
                if (!sCachedCheckableNode.equals(node)) {
                    node.recycle();
                }
                return true;
            }

            if (node != null) {
                node.recycle();
            }
        }

        return false;
    }

    private boolean findCheckableNode(AccessibilityNodeInfoCompat source) {
        if (source == null) {
            return false;
        }
        if (source.isCheckable()) {
            sCachedCheckableNode = source;
            return true;
        }

        int children = source.getChildCount();
        for (int i = 0; i < children; i++) {
            AccessibilityNodeInfoCompat node = source.getChild(i);
            if (findCheckableNode(node)) {
                if (!sCachedCheckableNode.equals(node)) {
                    node.recycle();
                }
                return true;
            }

            if (node != null) {
                node.recycle();
            }
        }

        return false;
    }

    @Override
    public boolean accept(AccessibilityEvent event, TalkBackService context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) return false;

        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            mClickedNode = null;
            mClickedTime = -1;

            if (event.isChecked()) {
                return true;
            }

            AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
            AccessibilityNodeInfoCompat source = record.getSource();
            if (source != null) {
                if (source.isCheckable()) {
                    return true;
                }

                // it is bug in settings application that does not include clicked state on node
                // so we need to restore it later from TYPE_WINDOW_CONTENT_CHANGED event
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mClickedNode = source;
                    mClickedTime = System.currentTimeMillis();
                }
            }

            return false;
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
            if (mClickedTime == -1 || mClickedNode == null) return false;

            long now = System.currentTimeMillis();
            if ((mClickedTime + 1000) < now) {
                mClickedTime = -1;
                if (mClickedNode != null) {
                    mClickedNode.recycle();
                    mClickedNode = null;
                }
                return false;
            }

            AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
            AccessibilityNodeInfoCompat source = record.getSource();
            return findClickedCheckableNode(source);
        }

        return false;
    }

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (sCachedCheckableNode == null) return false;

            // Need to get latest state of cached node before accessing.
            sCachedCheckableNode.refresh();

            utterance.addAuditory(R.raw.tick);
            utterance.addHaptic(R.array.view_clicked_pattern);
            utterance.addSpoken(context.getString(sCachedCheckableNode.isChecked() ?
                    R.string.value_checked : R.string.value_not_checked));
            sCachedCheckableNode.recycle();
            sCachedCheckableNode = null;
            return true;
        }

        AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        AccessibilityNodeInfoCompat source = record.getSource();

        utterance.addAuditory(R.raw.tick);
        utterance.addHaptic(R.array.view_clicked_pattern);

        CharSequence eventText = AccessibilityEventUtils.getEventTextOrDescription(event);
        if (!TextUtils.isEmpty(eventText)) {
            utterance.addSpoken(eventText);
        }

        // Switch and ToggleButton state is sent along with the event, so only
        // append checked / not checked state for other types of controls.
        // TODO: node.isTwoState()
        if (Role.getRole(source) == Role.ROLE_TOGGLE_BUTTON ||
                Role.getRole(source) == Role.ROLE_SWITCH) {
            return true;
        }

        if (source == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (event.isChecked()) {
                utterance.addSpoken(context.getString(R.string.value_checked));
            } else {
                utterance.addSpoken(context.getString(R.string.value_not_checked));
            }

            return true;
        }

        if (source.isCheckable()) {
            if (source.isChecked()) {
                utterance.addSpoken(context.getString(R.string.value_checked));
            } else {
                utterance.addSpoken(context.getString(R.string.value_not_checked));
            }
        }

        return true;
    }
}

