/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.talkback.contextmenu;

import android.annotation.TargetApi;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class BreakoutMenuUtils {
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.ICE_CREAM_SANDWICH;

    public abstract static class JogDial {
        /** The number of segments in a jog dial. */
        private final int mSegmentCount;

        public JogDial(int segmentCount) {
            mSegmentCount = segmentCount;
        }

        /**
         * Clears the specified menu and populates it with jog dial segments.
         * <p>
         * Though the dial segments will interpret selection events as rotation,
         * the radial menu will continue to receive selection and click events
         * for all segments.
         *
         * @param radialMenu The radial menu to populate.
         */
        public void populateMenu(RadialMenu radialMenu) {
            radialMenu.clear();

            for (int i = 0; i < mSegmentCount; i++) {
                final RadialMenuItem item = radialMenu.add(RadialMenu.NONE, i, i, "");
                item.setOnMenuItemSelectionListener(mJogListener);
            }
        }

        /** Returns the number of segments in this jog dial. */
        public int getSegmentCount() {
            return mSegmentCount;
        }

        /** Called when the user first touches the jog dial. */
        protected abstract void onFirstTouch();

        /** Called when the user moves counter-clockwise. */
        protected abstract void onPrevious();

        /** Called when the user moves clockwise. */
        protected abstract void onNext();

        /**
         * Jog listener added to individual segments. Interprets movement
         * between adjacent segments as rotation.
         */
        private final RadialMenuItem.OnMenuItemSelectionListener mJogListener =
                new RadialMenuItem.OnMenuItemSelectionListener() {
            private int mLastItem = -1;

            @Override
            public boolean onMenuItemSelection(RadialMenuItem item) {
                final int itemId = item.getItemId();
                final int diff = (itemId - mLastItem);

                if (mLastItem >= 0) {
                    if ((diff == -1) || (diff == -mSegmentCount)) {
                        onPrevious();
                    } else if ((diff == 1) || (diff == mSegmentCount)) {
                        onNext();
                    }
                } else {
                    onFirstTouch();
                }

                mLastItem = item.getItemId();

                // Don't swallow this event, let the parent handle it as well.
                return false;
            }
        };
    }
}
