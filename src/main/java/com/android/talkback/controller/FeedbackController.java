/*
 * Copyright (C) 2014 The Android Open Source Project
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

public interface FeedbackController {

    /**
     * Plays the vibration pattern associated with the given resource ID.
     *
     * @param resId The vibration pattern's resource identifier.
     * @return {@code true} if successful
     */
    boolean playHaptic(int resId);

    /**
     * Plays the auditory feedback associated with the given resource ID using the default rate,
     * volume, and panning.
     *
     * @param resId The auditory feedback's resource identifier.
     */
    void playAuditory(int resId);

    /**
     * Plays the auditory feedback associated with the given resource ID using the specified rate,
     * volume, and panning.
     *
     * @param resId The auditory feedback's resource identifier.
     * @param rate The playback rate adjustment, from 0.5 (half speed) to 2.0 (double speed).
     * @param volume The volume adjustment, from 0.0 (mute) to 1.0 (original volume).
     */
    void playAuditory(int resId, final float rate, float volume);

    /**
     * Interrupts all ongoing feedback.
     */
    void interrupt();

    /**
     * Releases all resources held by the feedback controller and clears the shared instance. No
     * calls should be made to this instance after calling this method.
     */
    void shutdown();

    /**
     * Add a listener to be called when haptic feedback begins.
     *
     * @param listener The listener to add
     */
    public void addHapticFeedbackListener(HapticFeedbackListener listener);

    /**
     * Remove a HapticFeedbackListener
     *
     * @param listener The listener to remove
     */
    public void removeHapticFeedbackListener(HapticFeedbackListener listener);

    /**
     * Some features, such as the tap detector, may be affected by haptic feedback and want to know
     * when we initiate it.
     */
    interface HapticFeedbackListener {

        /**
         * Alert the listener that haptic feedback is about to start
         *
         * @param currentNanoTime The current system time.
         */
        void onHapticFeedbackStarting(long currentNanoTime);
    }
}
