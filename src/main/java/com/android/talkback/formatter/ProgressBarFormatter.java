/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.talkback.formatter;

import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.LogUtils;

/**
 * Formatter for progress bar events.
 */
public class ProgressBarFormatter implements EventSpeechRule.AccessibilityEventFormatter {

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        if (shouldDropEvent(event)) {
            LogUtils.log(this, Log.VERBOSE, "Dropping unwanted progress bar event");
            return false;
        }

        final CharSequence text = AccessibilityEventUtils.getEventTextOrDescription(event);
        if (!TextUtils.isEmpty(text)) {
            utterance.addSpoken(text);
            return true;
        }

        final float percent = getProgressPercent(event);
        final float rate = (float) Math.pow(2.0, (percent / 50.0) - 1);

        utterance.addAuditory(R.raw.scroll_tone);
        utterance.getMetadata().putFloat(Utterance.KEY_METADATA_EARCON_RATE, rate);
        utterance.getMetadata().putFloat(Utterance.KEY_METADATA_EARCON_VOLUME, 0.5f);

        return true;
    }

    private boolean shouldDropEvent(AccessibilityEvent event) {
        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        // Don't drop if we're on pre-ICS or the event was generated (e.g.
        // missing a node).
        if (source == null) {
            return false;
        }

        // Don't drop if the node is currently focused or accessibility focused.
        if (source.isFocused() || source.isAccessibilityFocused()) {
            return false;
        }

        // Don't drop if the node was recently explored.
        return true;
    }

    private float getProgressPercent(AccessibilityEvent event) {
        final int maxProgress = event.getItemCount();
        final int progress = event.getCurrentItemIndex();
        final float percent = (progress / (float) maxProgress);

        return (100.0f * Math.max(0.0f, Math.min(1.0f, percent)));
    }

}
