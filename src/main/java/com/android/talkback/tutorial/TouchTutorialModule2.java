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
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.android.talkback.R;

/**
 * A tutorial lesson that introduces using two fingers to scroll through a list
 * and using scroll forward and backward gestures.
 */
@SuppressLint("ViewConstructor")
class TouchTutorialModule2 extends TutorialModule {
    private static final int MIN_SCROLLED_ITEMS = 4;

    private final InstrumentedListView mAllApps;

    /** A delegate used for detecting an initial focus event on a list item. */
    private final AccessibilityDelegate mListItemFocusDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
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
     * A delegate used for detecting that the user scrolled the list down at
     * least through a minimum number of items.
     */
    private final AccessibilityDelegate mViewScrolledDelegate = new AccessibilityDelegate() {
        @Override
        public void sendAccessibilityEventUnchecked(@NonNull View host,
                                                    @NonNull AccessibilityEvent event) {
            if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)
                    && (mAllApps.getFirstVisiblePosition() >= MIN_SCROLLED_ITEMS)) {
                mAllApps.setAccessibilityDelegate(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger2();
                    }
                });
            }
            super.sendAccessibilityEventUnchecked(host, event);
        }
    };

    /**
     * A delegate used for detecting a focus event on a list item after the list
     * has been scrolled.
     */
    private final AccessibilityDelegate mSecondListItemFocusDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
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

    /** A listener used for detecting a scroll forward action on the list. */
    private final InstrumentedListView.ListViewListener mScrollFowardListViewListener =
            new InstrumentedListView.ListViewListener() {
        @Override
        public void onPerformAccessibilityAction(int action) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                mAllApps.setInstrumentation(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger4();
                    }
                });
            }
        }
    };

    /**
     * A delegate used for detecting a focus event on a list item after the list
     * has been scrolled via two different methods.
     */
    private final AccessibilityDelegate mThirdListItemFocusDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                mAllApps.setAccessibilityDelegate(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger5();
                    }
                });
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /** A listener used for detecting a scroll backward action on the list. */
    private final InstrumentedListView.ListViewListener mScrollBackwardListViewListener =
            new InstrumentedListView.ListViewListener() {
        @Override
        public void onPerformAccessibilityAction(int action) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                mAllApps.setInstrumentation(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger6();
                    }
                });
            }
        }
    };

    public TouchTutorialModule2(AccessibilityTutorialActivity parentTutorial) {
        super(parentTutorial, R.layout.tutorial_lesson_2,
                R.string.accessibility_tutorial_lesson_2_title);

        mAllApps = (InstrumentedListView) findViewById(R.id.list_view);
        mAllApps.setAdapter(new AppsAdapter(getContext(), android.R.layout.simple_list_item_1,
                android.R.id.text1) {
            @Override
            protected void populateView(TextView text, CharSequence label, Drawable icon) {
                text.setText(label);
                text.setCompoundDrawables(icon, null, null, null);
            }
        });

        setSkipVisible(false);
        setBackVisible(true);
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
        mAllApps.setInstrumentation(null);

        AccessibilityTutorialActivity.setAllowContextMenus(true);
    }

    private void onTrigger0() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_1, true);

        // Next trigger is a focus event raised from a list item.
        mAllApps.setAccessibilityDelegate(mListItemFocusDelegate);
    }

    private void onTrigger1() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_2, true);

        // Next trigger is a scroll event raised from the list.
        mAllApps.setAccessibilityDelegate(mViewScrolledDelegate);
    }

    private void onTrigger2() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_3, true);

        // Next trigger is a focus event raised from a list item.
        mAllApps.setAccessibilityDelegate(mSecondListItemFocusDelegate);
    }

    private void onTrigger3() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_4, true);

        // Next trigger is a scroll action sent to the list.
        mAllApps.setInstrumentation(mScrollFowardListViewListener);
    }

    private void onTrigger4() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_5, true);

        // Next trigger is a focus event raised from a list item.
        mAllApps.setAccessibilityDelegate(mThirdListItemFocusDelegate);
    }

    private void onTrigger5() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_6, true);

        // Next trigger is a scroll action sent to the list.
        mAllApps.setInstrumentation(mScrollBackwardListViewListener);
    }

    private void onTrigger6() {
        // This is the last trigger in this lesson.
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_7,
                true, getContext().getString(R.string.accessibility_tutorial_next));
    }
}
