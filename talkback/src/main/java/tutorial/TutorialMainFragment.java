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

package com.google.android.accessibility.talkback.tutorial;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ListView;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;

public class TutorialMainFragment extends Fragment implements AccessibilityEventListener {

  private static final String TUTORIAL_CLASS_NAME = AccessibilityTutorialActivity.class.getName();

  /** Event types that are handled by TutorialMainFragment. */
  private static final int MASK_EVENTS_HANDLED_BY_TUTORIAL_MAIN_FRAGMENT =
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
          | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER;

  private TutorialNavigationCallback lessonSelectedCallback;
  private TutorialController tutorialController;
  private HoverTrackingButton offButton;
  private HoverTrackingLinearLayout parentLayout;
  private boolean otherViewHovered;
  /** Flag to indicate presence of navigation up button. */
  private boolean navigationUpFlag;

  public void setOnLessonSelectedCallback(TutorialNavigationCallback callback) {
    lessonSelectedCallback = callback;
  }

  public void setTutorialController(TutorialController controller) {
    tutorialController = controller;
  }

  /**
   * Sets the flag to decide if the navigation up button should appear depending on the source of
   * the intent
   *
   * @param source
   */
  public void setIfBackNavigationReq(String source) {
    if (TextUtils.equals(source, TalkBackService.TUTORIAL_SRC)) {
      navigationUpFlag = false;
    } else if (TextUtils.equals(source, TalkBackPreferencesActivity.TUTORIAL_SRC)) {
      navigationUpFlag = true;
    } else {
      navigationUpFlag = false;
    }
  }

  @Override
  public void onCreate(Bundle savedState) {
    super.onCreate(savedState);
    setRetainInstance(true);
  }

  @Override
  public void onResume() {
    super.onResume();
    // show general tutorial title, no up arrow
    AppCompatActivity activity = (AppCompatActivity) getActivity();
    ActionBar actionBar = (activity == null) ? null : activity.getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.tutorial_title);
      actionBar.setDisplayHomeAsUpEnabled(navigationUpFlag);
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
    if (tutorialController == null || lessonSelectedCallback == null) {
      return null;
    }

    View view = inflater.inflate(R.layout.tutorial_main_fragment, container, false);

    ListView lessonListView = (ListView) view.findViewById(R.id.list);
    LessonsAdapter adapter =
        new LessonsAdapter(getActivity(), tutorialController.getTutorial(), lessonSelectedCallback);
    lessonListView.setAdapter(adapter);
    offButton = (HoverTrackingButton) view.findViewById(R.id.offButton);
    parentLayout = (HoverTrackingLinearLayout) view.findViewById(R.id.parentLayout);

    if (BuildVersionUtils.isAtLeastN() && TalkBackService.getInstance() != null) {
      TalkBackService.getInstance().addEventListener(this);
    } else if (offButton != null) {
      offButton.setVisibility(View.GONE);
    }

    return view;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_TUTORIAL_MAIN_FRAGMENT;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (offButton == null || parentLayout == null) {
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
        otherViewHovered = false;
        offButton.clearTracking();
        parentLayout.clearTracking();
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        if (offButton.didHoverEnter() && !parentLayout.didHoverEnter() && !otherViewHovered) {
          if (TalkBackService.getInstance() != null) {
            TalkBackService.getInstance().disableTalkBackFromTutorial((EventId) null);
          }
        }
        break;
      case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
        CharSequence className = event.getClassName();
        // Hovering over the button gives an event with TUTORIAL_CLASS_NAME class.
        // But empty areas of the activity should be tracked by HoverTrackingLinearLayout.
        if (className == null || !className.equals(TUTORIAL_CLASS_NAME)) {
          otherViewHovered = true;
        }
        break;
      default: // fall out
    }
  }
}
