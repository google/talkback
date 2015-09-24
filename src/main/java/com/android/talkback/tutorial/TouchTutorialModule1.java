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

package com.android.talkback.tutorial;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

import com.android.talkback.R;

/**
 * A tutorial lesson that introduces using a finger to explore and interact with
 * on-screen content.
 */
@SuppressLint("ViewConstructor")
class TouchTutorialModule1 extends TutorialModule {
    private static final int TARGET_POSITION = 0;

    private final AppsAdapter mAppsAdapter;
    private final GridView mAllApps;

    /**
     * A delegate used for detecting an initial focus event on an application
     * icon.
     */
    private final AccessibilityDelegate mFirstIconFocusDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if (event.getEventType()
                    == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                child.setTag(R.id.accessibility_tutorial_tag_touched, true);
                mAllApps.setAccessibilityDelegate(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger1();
                    }
                });
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /**
     * A delegate used for detecting a focus event on an application icon
     * different from the one that was focused in the first trigger.
     */
    private final AccessibilityDelegate mUntouchedIconFocusDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    && (child.getTag(R.id.accessibility_tutorial_tag_touched) == null)) {
                child.setTag(R.id.accessibility_tutorial_tag_touched, true);
                mAllApps.setAccessibilityDelegate(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger2();
                    }
                });
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /**
     * A delegate used for detecting a focus event on an application icon after
     * the user is instructed to swipe from left to right.
     */
    private final AccessibilityDelegate mSwipeFocusChangeDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            // TODO: Is there any way to ensure this is the result of focus
            // traversal?
            if ((event.getEventType()
                    == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                mAllApps.setAccessibilityDelegate(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger3();
                    }
                });
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /**
     * A delegate used for detecting a focus event on the target application
     * icon.
     */
    private final AccessibilityDelegate mTargetIconFocusDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    && (mAllApps.getPositionForView(child) == TARGET_POSITION)) {
                mAllApps.setAccessibilityDelegate(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger4();
                    }
                });
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /**
     * A delegate used for detecting a focus event on another view after the
     * target application icon has received focus.
     */
    private final AccessibilityDelegate mTargetIconLoseFocusDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    && (mAllApps.getPositionForView(child) != TARGET_POSITION)) {
                mAllApps.setAccessibilityDelegate(null);
                installTriggerDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger4FocusLost();
                    }
                });
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /**
     * A listener used for detecting a click event on the target application
     * icon.
     */
    private final OnItemClickListener mTargetIconClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position == TARGET_POSITION) {
                mAllApps.setAccessibilityDelegate(null);
                mAllApps.setOnItemClickListener(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger5();
                    }
                });
            }
        }
    };

    public TouchTutorialModule1(AccessibilityTutorialActivity parentTutorial) {
        super(parentTutorial, R.layout.tutorial_lesson_1,
                R.string.accessibility_tutorial_lesson_1_title);

        mAppsAdapter = new AppsAdapter(getContext(), R.layout.tutorial_app_icon, R.id.app_icon);

        mAllApps = (GridView) findViewById(R.id.all_apps);
        mAllApps.setAdapter(mAppsAdapter);

        setSkipVisible(true);
        setBackVisible(false);
        setNextVisible(true);
        setFinishVisible(false);
    }

    @Override
    public void onStart() {
        super.onStart();

        AccessibilityTutorialActivity.setAllowContextMenus(false);

        onTrigger0();
    }

    @Override
    public void onPause() {
        // Do nothing.
    }

    @Override
    public void onResume() {
        // Do nothing.
    }

    @Override
    public void onStop() {
        super.onStop();

        mAllApps.setAccessibilityDelegate(null);
        mAllApps.setOnItemClickListener(null);

        AccessibilityTutorialActivity.setAllowContextMenus(true);
    }

    /**
     * Triggered when the lesson is first shown.
     */
    private void onTrigger0() {
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_1, true);

        // Next trigger is a focus event raised from an icon.
        mAllApps.setAccessibilityDelegate(mFirstIconFocusDelegate);
    }

    /**
     * Triggered when one application icon receives accessibility focus.
     */
    private void onTrigger1() {
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_2_more, true);

        /*
         * Next trigger is a focus event raised from an icon that hasn't been
         * touched.
         */
        mAllApps.setAccessibilityDelegate(mUntouchedIconFocusDelegate);
    }

    /**
     * Triggered when a second application icon receives accessibility focus.
     */
    private void onTrigger2() {
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_3, true);

        /*
         * Next trigger is a focus event raised from an icon after the user is
         * instructed to swipe from left to right.
         */
        mAllApps.setAccessibilityDelegate(mSwipeFocusChangeDelegate);
    }

    /**
     * Triggered when the accessibility focus changes (hopefully because the
     * user swiped from left to right).
     */
    private void onTrigger3() {
        final CharSequence targetName = mAppsAdapter.getLabel(TARGET_POSITION);
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_4, true, targetName);

        // Next trigger is a focus event raised from the target icon.
        mAllApps.setAccessibilityDelegate(mTargetIconFocusDelegate);
    }

    /**
     * Triggered when the target application icon receives accessibility focus.
     */
    private void onTrigger4() {
        final CharSequence targetName = mAppsAdapter.getLabel(TARGET_POSITION);
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_5, true, targetName);

        /*
         * One possible trigger is leaving the target icon. This doesn't
         * advance.
         */
        mAllApps.setAccessibilityDelegate(mTargetIconLoseFocusDelegate);

        /*
         * The other possible trigger is a click event raised from the target
         * icon.
         */
        mAllApps.setOnItemClickListener(mTargetIconClickListener);
    }

    /**
     * Triggered when the target application icon loses accessibility focus.
     */
    private void onTrigger4FocusLost() {
        final CharSequence targetName = mAppsAdapter.getLabel(TARGET_POSITION);
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_5_exited, true, targetName);

        // Watch for a focus event returning to the target icon.
        mAllApps.setAccessibilityDelegate(mTargetIconFocusDelegate);
    }

    /**
     * Triggered when the target application icon is activated.
     */
    private void onTrigger5() {
        // This is the last trigger in this lesson.
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_6, true,
                getContext().getString(R.string.accessibility_tutorial_next));
    }
}
