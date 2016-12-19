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

package com.android.talkback.tutorial;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.os.BuildCompat;
import android.text.TextUtils;
import android.text.TextUtils.SimpleStringSplitter;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.utils.AccessibilityEventListener;
import com.google.android.marvin.talkback.TalkBackService;

import java.lang.reflect.Method;

public class TutorialMainFragment extends Fragment implements AccessibilityEventListener {

    private static final String TUTORIAL_CLASS_NAME = AccessibilityTutorialActivity.class.getName();

    private TutorialNavigationCallback mLessonSelectedCallback;
    private TutorialController mTutorialController;
    private HoverTrackingButton mOffButton;
    private HoverTrackingLinearLayout mParentLayout;
    private boolean mOtherViewHovered;

    public void setOnLessonSelectedCallback(TutorialNavigationCallback callback) {
        mLessonSelectedCallback = callback;
    }

    public void setTutorialController(TutorialController controller) {
        mTutorialController = controller;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setRetainInstance(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity != null) {
            activity.getActionBar().setDisplayHomeAsUpEnabled(false);
            activity.getActionBar().setDisplayShowTitleEnabled(true);
            activity.getActionBar().setDisplayShowCustomEnabled(false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.tutorial_main_fragment , container, false);

        ListView lessonListView = (ListView) view.findViewById(R.id.list);
        LessonsAdapter adapter = new LessonsAdapter(getActivity(),
                mTutorialController.getTutorial(), mLessonSelectedCallback);
        lessonListView.setAdapter(adapter);
        mOffButton = (HoverTrackingButton) view.findViewById(R.id.offButton);
        mParentLayout = (HoverTrackingLinearLayout) view.findViewById(R.id.parentLayout);

        if (BuildCompat.isAtLeastN() && TalkBackService.getInstance() != null) {
            TalkBackService.getInstance().addEventListener(this);
        } else if (mOffButton != null) {
            mOffButton.setVisibility(View.GONE);
        }

        return view;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mOffButton == null || mParentLayout == null) {
            return;
        }

        // There are three types of areas that we're detecting: the button area, areas of the
        // activity that are a11y-focusable, and areas of the activity that are blank.
        // 1) The HoverTrackingButton keeps track of the button area.
        // 2) The HoverTrackingLinearLayout keeps track of blank activity areas.
        // 3) We use TYPE_VIEW_HOVER_ENTER to track a11y-focusable activity areas.
        // The user must begin and end the touch interaction within the Turn TalkBack Off button
        // without moving their finger into other areas of the activity in order to turn TB off.

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
                mOtherViewHovered = false;
                mOffButton.clearTracking();
                mParentLayout.clearTracking();
                break;
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
                if (mOffButton.didHoverEnter() && !mParentLayout.didHoverEnter() &&
                        !mOtherViewHovered) {
                    if (TalkBackService.getInstance() != null) {
                        TalkBackService.getInstance().disableTalkBack();
                    }
                }
                break;
            case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
                CharSequence className = event.getClassName();
                // Hovering over the button gives an event with TUTORIAL_CLASS_NAME class.
                // But empty areas of the activity should be tracked by HoverTrackingLinearLayout.
                if (className == null || !className.equals(TUTORIAL_CLASS_NAME)) {
                    mOtherViewHovered = true;
                }
                break;
        }
    }
}
