/*
 * Copyright (C) 2011 Google Inc.
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

import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.FeedbackItem;
import com.android.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;

/**
 * Formatter that returns an utterance to announce scrolling.
 */
public class ScrollFormatter implements EventSpeechRule.AccessibilityEventFormatter {
    /** The minimum interval (in milliseconds) between scroll feedback events. */
    private static final long MIN_SCROLL_INTERVAL = 250;

    private static long mLastScrollEvent = -1;

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final long currentTime = SystemClock.uptimeMillis();

        if ((currentTime - mLastScrollEvent) < MIN_SCROLL_INTERVAL) {
            // TODO: We shouldn't just reject events, since we'll get weird
            // rhythms when scrolling, e.g. if we're getting an event every
            // 300ms and we reject at the 250ms mark.
            return false;
        }

        mLastScrollEvent = currentTime;

        final float percent = getScrollPercent(event);
        final float rate = (float) Math.pow(2.0, (percent / 50.0) - 1);

        utterance.addAuditory(R.raw.scroll_tone);
        utterance.getMetadata().putFloat(Utterance.KEY_METADATA_EARCON_RATE, rate);
        utterance.addSpokenFlag(FeedbackItem.FLAG_NO_SPEECH);

        return true;
    }

    /**
     * Returns the percentage scrolled within a scrollable view. The value will
     * be in the range {0..100} where 100 is the maximum scroll amount.
     *
     * @param event The event from which to obtain the scroll position.
     * @return The percentage scrolled within a scrollable view.
     */
    private float getScrollPercent(AccessibilityEvent event) {
        final float position = getScrollPosition(event);

        return (100.0f * Math.max(0.0f, Math.min(1.0f, position)));
    }

    /**
     * Returns a floating point value representing the scroll position of an
     * {@link AccessibilityEvent}. This value may be outside the range {0..1}.
     * If there's no valid way to obtain a position, this method returns 0.5.
     *
     * @param event The event from which to obtain the scroll position.
     * @return A floating point value representing the scroll position.
     */
    private float getScrollPosition(AccessibilityEvent event) {
        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        final int itemCount = event.getItemCount();
        final int fromIndex = event.getFromIndex();

        // First, attempt to use (fromIndex / itemCount).
        if ((fromIndex >= 0) && (itemCount > 0)) {
            return (fromIndex / (float) itemCount);
        }

        final int scrollY = record.getScrollY();
        final int maxScrollY = record.getMaxScrollY();

        // Next, attempt to use (scrollY / maxScrollY). This will fail if the
        // getMaxScrollX() method is not available.
        if ((scrollY >= 0) && (maxScrollY > 0)) {
            return (scrollY / (float) maxScrollY);
        }

        // Finally, attempt to use (scrollY / itemCount).
        // TODO: Hack from previous versions -- is it still needed?
        if ((scrollY >= 0) && (itemCount > 0) && (scrollY <= itemCount)) {
            return (scrollY / (float) itemCount);
        }

        return 0.5f;
    }
}
