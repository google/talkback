/*
 * Copyright (C) 2013 Google Inc.
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

package com.android.talkback.controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * A receiver that listens for and responds to actions performed in response to
 * gestures on {@code TalkBackService}.
 */
public class GestureActionMonitor extends BroadcastReceiver {
    public static final String ACTION_GESTURE_ACTION_PERFORMED =
            "com.android.talkback.controller.GestureActionPerformedAction";
    public static final String EXTRA_SHORTCUT_GESTURE_ACTION =
            "com.android.talkback.controller.ShortcutGestureExtraAction";

    /** The filter for which broadcast events this receiver should monitor. */
    public static final IntentFilter FILTER = new IntentFilter(ACTION_GESTURE_ACTION_PERFORMED);

    private GestureActionListener mListener;

    /**
     * Sets the {@link GestureActionListener} that will handle received
     * gesture actions.
     *
     * @param listener The listener that should handle gesture actions, or
     *            {@code null} if gesture actions should not be handled.
     */
    public void setListener(GestureActionListener listener) {
        mListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mListener == null) {
            return;
        }

        if (!ACTION_GESTURE_ACTION_PERFORMED.equals(intent.getAction())) {
            return;
        }

        String action = intent.getStringExtra(EXTRA_SHORTCUT_GESTURE_ACTION);
        if (action != null) {
            mListener.onGestureAction(action);
        }
    }

    /**
     * A delegate handling actions performed in response to shortcut gestures on
     * {@link com.google.android.marvin.talkback.TalkBackService}.
     */
    public interface GestureActionListener {
        /**
         * Handles an action performed in response to a shortcut gesture on
         * {@link com.google.android.marvin.talkback.TalkBackService}.
         *
         * @param action The type of gesture action performed.
         */
        void onGestureAction(String action);
    }

}
