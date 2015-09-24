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

import android.support.annotation.NonNull;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.android.talkback.CursorGranularity;
import com.android.talkback.R;
import com.android.talkback.controller.CursorController;

/**
 * A tutorial lesson that introduces editing text using the keyboard and the
 * commands on the local context menu.
 */
@SuppressLint("ViewConstructor")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class TouchTutorialModule5 extends TutorialModule {
    /** This module requires JellyBean MR2 (API 18). */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

    /** A delegate used for detecting a focus event on the edit text. */
    private final AccessibilityDelegate mEditTextFocusedDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if (child == mEditText) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    mLayout.setAccessibilityDelegate(null);
                    installTriggerDelayedWithFeedback(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger1();
                        }
                    });
                }
            }

            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /** A listener that watches for the user to start editing the text. */
    private final OnTouchListener mEditTextOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (v == mEditText) {
                mLayout.setAccessibilityDelegate(null);
                mEditText.setOnTouchListener(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger2();
                    }
                });
            }

            return false;
        }
    };

    /** A delegate used for detecting that the user started editing the text. */
    private final AccessibilityDelegate mEditTextClickDelegate = new AccessibilityDelegate() {
        @Override
        public boolean performAccessibilityAction(@NonNull View host, int action, Bundle args) {
            if ((host == mEditText) && (action == AccessibilityNodeInfo.ACTION_CLICK)) {
                mEditText.setOnTouchListener(null);
                mLayout.setAccessibilityDelegate(null);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger2();
                    }
                });
            }

            return super.performAccessibilityAction(host, action, args);
        }
    };

    /**
     * A delegate that watches for the user to move the cursor to the beginning
     * of the text.
     */
    private final AccessibilityDelegate mMoveCursorToBeginningDelegate =
            new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if (child == mEditText) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                    final int selectionStartIndex = event.getFromIndex();
                    final int selectionEndIndex = event.getToIndex();

                    if ((selectionStartIndex == 0) && (selectionEndIndex == 0)) {
                        mLayout.setAccessibilityDelegate(null);
                        installTriggerDelayedWithFeedback(new Runnable() {
                            @Override
                            public void run() {
                                onTrigger3();
                            }
                        });
                    }
                }
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /**
     * A delegate used for detecting that the user changed to word granularity.
     */
    private final CursorController.GranularityChangeListener mWordGranularityListener =
            new CursorController.GranularityChangeListener() {
        @Override
        public void onGranularityChanged(CursorGranularity granularity) {
            if (CursorGranularity.WORD.equals(granularity)) {
                removeGranularityListener(this);
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
     * A delegate that watches for the user to move the cursor to the end of the
     * text.
     */
    private final AccessibilityDelegate mMoveCursorToEndDelegate
            = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if (child == mEditText) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                    final int selectionStartIndex = event.getFromIndex();
                    final int selectionEndIndex = event.getToIndex();
                    final int textLength = event.getItemCount();

                    if ((selectionStartIndex == textLength) && (selectionEndIndex == textLength)) {
                        mLayout.setAccessibilityDelegate(null);
                        installTriggerDelayedWithFeedback(new Runnable() {
                            @Override
                            public void run() {
                                onTrigger5();
                            }
                        });
                    }
                }
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /** A delegate that watches for the user to select all the text. */
    private final AccessibilityDelegate mSelectAllDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if (child == mEditText) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                    final int selectionStartIndex = event.getFromIndex();
                    final int selectionEndIndex = event.getToIndex();
                    final int textLength = event.getItemCount();

                    if (selectionEndIndex - selectionStartIndex == textLength) {
                        mLayout.setAccessibilityDelegate(null);
                        installTriggerDelayedWithFeedback(new Runnable() {
                            @Override
                            public void run() {
                                onTrigger6();
                            }
                        });
                    }
                }
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };


    /**
     * A delegate that prevents TalkBack from handling text and text selection
     * changed events.
     */
    private final AccessibilityDelegate mIgnoreTextChangesDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            final int eventType = event.getEventType();
            return !((child == mEditText)
                            && ((eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
                                    || (eventType ==
                                            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)))
                    && super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    /** A watcher that watches for the user to enter custom text. */
    private final TextWatcher mCustomTextTypedWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable text) {
            final String editableText = getContext()
                    .getString(R.string.accessibility_tutorial_lesson_5_editable_text);

            if (text.length() >= MIN_CHARS_TO_TYPE && text.length() < editableText.length()) {
                mEditText.removeTextChangedListener(this);
                installTriggerDelayedWithFeedback(new Runnable() {
                    @Override
                    public void run() {
                        onTrigger7();
                    }
                });
            }
        }

        @Override
        public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}

        @Override
        public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
    };

    private static final int MIN_CHARS_TO_TYPE = 3;

    private LinearLayout mLayout;
    private EditText mEditText;

    public TouchTutorialModule5(AccessibilityTutorialActivity parentTutorial) {
        super(parentTutorial, R.layout.tutorial_lesson_5,
                R.string.accessibility_tutorial_lesson_5_title);

        mLayout = (LinearLayout) findViewById(R.id.accessibility_tutorial_lesson_5_layout);
        mEditText = (EditText) findViewById(R.id.accessibility_tutorial_lesson_5_edit_text);

        setSkipVisible(false);
        setBackVisible(true);
        setNextVisible(false);
        setFinishVisible(true);
    }

    @Override
    public void onPause() {
        /*
         * Because we start the lesson over on resume, we clear the delegates on
         * pause instead of on stop.
         */
        clearDelegates();
        removeGranularityListener(mWordGranularityListener);
    }

    @Override
    public void onResume() {
        // Reset the text and selection
        mLayout.setAccessibilityDelegate(mIgnoreTextChangesDelegate);
        final String editableText = getContext()
                .getString(R.string.accessibility_tutorial_lesson_5_editable_text);
        mEditText.clearComposingText();
        mEditText.setText(editableText);
        mEditText.clearFocus();
        mLayout.setAccessibilityDelegate(null);

        /*
         * For this module in particular, since the edit text losing focus sends
         * the user back to trigger 0, it makes sense to force the user to start
         * over when the lesson resumes. Thus, we call trigger 0 on resume
         * instead of on start.
         */
        onTrigger0();
    }

    /** The user started the lesson. */
    private void onTrigger0() {
        addInstruction(R.string.accessibility_tutorial_lesson_5_text_1, true);

        // Next trigger: The user focused the edit text.
        mLayout.setAccessibilityDelegate(mEditTextFocusedDelegate);
    }

    /** The user focused the edit text. */
    private void onTrigger1() {
        addInstruction(R.string.accessibility_tutorial_lesson_5_text_2, true);

        // Next trigger: The user started editing the text.
        mEditText.setOnTouchListener(mEditTextOnTouchListener);
        // Note: this second trigger is for compatibility with single-tap
        // selection and with BrailleBack.
        mLayout.setAccessibilityDelegate(mEditTextClickDelegate);
    }

    /** The user started editing the text. */
    private void onTrigger2() {
        Context context = getContext();
        String localContextMenuDirection = getGestureDirectionForRequiredAction(
                getContext().getString(R.string.shortcut_value_local_breakout));

        String cursorControl = context.getString(R.string.title_edittext_controls);
        String moveToBeginning =
                context.getString(R.string.title_edittext_breakout_move_to_beginning);

        addInstruction(R.string.accessibility_tutorial_lesson_5_text_3, true,
                localContextMenuDirection, cursorControl, moveToBeginning);

        // Next trigger: The user moved the cursor to the beginning of the text.
        mLayout.setAccessibilityDelegate(mMoveCursorToBeginningDelegate);
    }

    /** The user moved the cursor to the beginning of the text. */
    private void onTrigger3() {
        final Context context = getContext();
        final String localContextMenuDirection = getGestureDirectionForRequiredAction(
                getContext().getString(R.string.shortcut_value_local_breakout));
        final String changeGranularity = context.getString(R.string.title_granularity);
        final String wordGranularity = context.getString(R.string.granularity_word);

        addInstruction(R.string.accessibility_tutorial_lesson_5_text_4, true,
                localContextMenuDirection, changeGranularity, wordGranularity);

        // Next trigger: The user changed to word granularity.
        addGranularityListener(mWordGranularityListener);
    }

    /** The user changed to word granularity. */
    private void onTrigger4() {
        addInstruction(R.string.accessibility_tutorial_lesson_5_text_5, true);

        // Next trigger: The user traversed to the end of the text.
        mLayout.setAccessibilityDelegate(mMoveCursorToEndDelegate);
    }

    /** The user traversed to the end of the text. */
    private void onTrigger5() {
        final Context context = getContext();
        final String localContextMenuDirection = getGestureDirectionForRequiredAction(
                getContext().getString(R.string.shortcut_value_local_breakout));
        final String cursorControl = context.getString(R.string.title_edittext_controls);
        final String selectAll = context.getString(android.R.string.selectAll);

        addInstruction(R.string.accessibility_tutorial_lesson_5_text_6, true,
                localContextMenuDirection, cursorControl, selectAll);

        // Next trigger: The user selected all the text in the edit text.
        mLayout.setAccessibilityDelegate(mSelectAllDelegate);
    }

    /** The user selected all the text in the edit text. */
    private void onTrigger6() {
        // Reset the text (and selection).
        mLayout.setAccessibilityDelegate(mIgnoreTextChangesDelegate);
        final String editableText = getContext()
                .getString(R.string.accessibility_tutorial_lesson_5_editable_text);
        mEditText.setText(editableText);
        mEditText.selectAll();
        mLayout.setAccessibilityDelegate(null);

        addInstruction(R.string.accessibility_tutorial_lesson_5_text_7, true);

        // Next trigger: The user replaced the text with custom text.
        mEditText.addTextChangedListener(mCustomTextTypedWatcher);
    }

    /** The user replaced the text with custom text. */
    private void onTrigger7() {
        // This is the last trigger in this lesson.
        addInstruction(R.string.accessibility_tutorial_lesson_5_text_8, true,
                getContext().getString(R.string.accessibility_tutorial_finish));
    }

    private void clearDelegates() {
        mLayout.setAccessibilityDelegate(null);
        mEditText.setOnTouchListener(null);
        mEditText.setOnEditorActionListener(null);
    }
}
