/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.talkback.tutorial;

import android.annotation.SuppressLint;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.controller.FullScreenReadController;
import com.android.talkback.controller.GestureActionMonitor;

/**
 * A tutorial lesson that introduces the local and global context menus.
 */
@SuppressLint("ViewConstructor")
class TouchTutorialModule3 extends TutorialModule {
    /**
     * A monitor used for tracking and handling actions associated with
     * service-level gestures.
     */
    private final GestureActionMonitor mGestureActionMonitor = new GestureActionMonitor();

    /** A monitor used for tracking and handling context menu actions. */
    private final ContextMenuMonitor mContextMenuMonitor = new ContextMenuMonitor();

    /** A delegate used for detecting the global context menu gesture. */
    private final GestureActionMonitor.GestureActionListener mGlobalContextMenuGestureDelegate =
            new GestureActionMonitor.GestureActionListener() {
        @Override
        public void onGestureAction(String action) {
            if (action == null) return;
            if (action.equals(getContext().getString(
                            R.string.shortcut_value_talkback_breakout))) {
                mGestureActionMonitor.setListener(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger1();
                    }
                });
            }
        }
    };

    /**
     * A delegate used for detecting that the user performed a continuous
     * reading action starting from the top of the screen.
     */
    private final ContextMenuMonitor.ContextMenuListener mReadFromTopDelegate =
            new ContextMenuMonitor.ContextMenuListener() {
        @Override
        public void onShow() {
            // Do nothing.
        }

        @Override
        public void onHide(int menuId) {
            // The user hid the Global Context Menu without selecting the
            // "Read from top" menu item
            mContextMenuMonitor.setListener(null);
            installTriggerDelayed(new Runnable() {
                @Override
                public void run() {
                    onTrigger2Hidden();
                }
            });
        }

        @Override
        public void onItemClick(int itemId) {
            if (itemId == R.id.read_from_top) {
                mContextMenuMonitor.setListener(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        // Don't actually perform a full continuous reading.
                        interruptContinuousReading();

                        onTrigger2();
                    }
                });
            }
        }
    };

    /** A delegate used for detecting the local context menu gesture. */
    private final GestureActionMonitor.GestureActionListener mLocalContextMenuGestureDelegate =
            new GestureActionMonitor.GestureActionListener() {
        @Override
        public void onGestureAction(String action) {
            if (action == null) return;
            if (action.equals(getContext().getString(
                            R.string.shortcut_value_local_breakout))) {
                mGestureActionMonitor.setListener(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger3();
                    }
                });
            }
        }
    };

    /** A delegate used for detecting that the local context menu was hidden. */
    private final ContextMenuMonitor.ContextMenuListener mLocalContextMenuHiddenDelegate =
            new ContextMenuMonitor.ContextMenuListener() {
        @Override
        public void onShow() {
            // Do nothing.
        }

        @Override
        public void onHide(int menuId) {
            if (menuId != R.menu.local_context_menu) {
                return;
            }

            mContextMenuMonitor.setListener(null);
            installTriggerDelayedWithFeedback(new Runnable() {
                @Override
                public void run() {
                    onTrigger4();
                }
            });
        }

        @Override
        public void onItemClick(int itemId) {
            // Do nothing.
        }
    };

    public TouchTutorialModule3(AccessibilityTutorialActivity parentTutorial) {
        super(parentTutorial, R.string.accessibility_tutorial_lesson_3_title);

        setSkipVisible(false);
        setBackVisible(true);
        setNextVisible(true);
        setFinishVisible(false);
    }

    @Override
    public void onStart() {
        super.onStart();

        onTrigger0();
    }

    @Override
    public void onPause() {
        unregisterReceivers();
    }

    @Override
    public void onResume() {
        registerReceivers();
    }

    @Override
    public void onStop() {
        super.onStop();

        mGestureActionMonitor.setListener(null);
        mContextMenuMonitor.setListener(null);
    }

    private void onTrigger0() {
        addInstruction(R.string.accessibility_tutorial_lesson_3_text_1, true,
                getGestureDirectionForRequiredAction(
                        getContext().getString(R.string.shortcut_value_talkback_breakout)));

        // Next trigger is a global context menu gesture.
        mGestureActionMonitor.setListener(mGlobalContextMenuGestureDelegate);
    }

    private void onTrigger1() {
        addInstruction(R.string.accessibility_tutorial_lesson_3_text_2, true,
                getContext().getString(R.string.shortcut_read_from_top));

        // Next trigger is a read from top command.
        mContextMenuMonitor.setListener(mReadFromTopDelegate);
    }

    private void onTrigger2Hidden() {
        addInstruction(R.string.accessibility_tutorial_lesson_3_text_3_hidden, true,
                getGestureDirectionForRequiredAction(
                        getContext().getString(R.string.shortcut_value_talkback_breakout)));

        // Next trigger is a global context menu gesture.
        mGestureActionMonitor.setListener(mGlobalContextMenuGestureDelegate);
    }

    private void onTrigger2() {
        addInstruction(R.string.accessibility_tutorial_lesson_3_text_3, true,
                getGestureDirectionForRequiredAction(
                        getContext().getString(R.string.shortcut_value_local_breakout)));

        // Next trigger is a local context menu gesture.
        mGestureActionMonitor.setListener(mLocalContextMenuGestureDelegate);
    }

    private void onTrigger3() {
        addInstruction(R.string.accessibility_tutorial_lesson_3_text_4, true);

        // Next trigger is a cancel button press on the local context menu.
        mContextMenuMonitor.setListener(mLocalContextMenuHiddenDelegate);
    }

    private void onTrigger4() {
        // This is the last trigger in this lesson.
        addInstruction(R.string.accessibility_tutorial_lesson_3_text_5, true,
                getContext().getString(R.string.accessibility_tutorial_next));
    }

    private void interruptSpeech() {
        final SpeechController speechController = getParentTutorial().getSpeechController();
        if (speechController != null) {
            speechController.interrupt();
        }
    }

    private void interruptContinuousReading() {
        final FullScreenReadController fullScreenReadController =
                getParentTutorial().getFullScreenReadController();
        if (fullScreenReadController != null) {
            fullScreenReadController.interrupt();
        }

        interruptSpeech();
    }

    private void registerReceivers() {
        registerReceiver(mGestureActionMonitor, GestureActionMonitor.FILTER);
        registerReceiver(mContextMenuMonitor, ContextMenuMonitor.FILTER);
    }

    private void unregisterReceivers() {
        unregisterReceiver(mGestureActionMonitor);
        unregisterReceiver(mContextMenuMonitor);
    }
}
