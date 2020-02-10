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

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.MenuActionInterceptor;
import com.google.android.accessibility.talkback.contextmenu.MenuManager;
import com.google.android.accessibility.talkback.contextmenu.MenuTransformer;
import com.google.android.accessibility.talkback.controller.GestureActionMonitor;
import com.google.android.accessibility.talkback.tutorial.exercise.Exercise;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.SpeechController;

/** Fragment used to show a single lesson in the Tutorial */
public class TutorialLessonFragment extends Fragment
    implements View.OnClickListener,
        Exercise.ExerciseCallback,
        GestureActionMonitor.GestureActionListener {

  private static final int DELAY_BEFORE_ANNOUNCE_LESSON = 100;
  private static final int DELAY_BEFORE_AUTO_MOVE_TO_NEXT_LESSON = 1000;

  private TutorialController tutorialController;
  private TutorialLesson lesson;
  private TutorialLessonPage page;
  private Exercise exercise;
  private int currentPage;
  private TutorialNavigationCallback callback;
  private TextView description;
  private SpeechController speechController;
  private GestureActionMonitor actionMonitor = new GestureActionMonitor();
  private Handler handler = new Handler(Looper.getMainLooper());

  public void setLesson(TutorialLesson lesson, int currentPage) {
    this.lesson = lesson;
    page = this.lesson.getLessonPage(currentPage);
    this.currentPage = currentPage;
    exercise = page.getExercise();
  }

  public void setTutorialController(TutorialController tutorialController) {
    this.tutorialController = tutorialController;
  }

  public void setTutorialNavigationCallback(TutorialNavigationCallback callback) {
    this.callback = callback;
  }

  @Override
  public void onCreate(Bundle instance) {
    super.onCreate(instance);
    setRetainInstance(true);
    setHasOptionsMenu(true);
    if (exercise != null) {
      exercise.setExerciseCallBack(this);
    }

    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      speechController = service.getSpeechController();
    }

    actionMonitor.setListener(this);
  }

  @Override
  public void onStart() {
    super.onStart();

    MenuTransformer menuTransformer = exercise.getContextMenuTransformer();
    MenuActionInterceptor menuActionInterceptor = exercise.getContextMenuActionInterceptor();
    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      MenuManager menuManager = service.getMenuManager();
      menuManager.setMenuTransformer(menuTransformer);
      menuManager.setMenuActionInterceptor(menuActionInterceptor);
    }

    // We need to post the announcements delayed in order to ensure that the view changed
    // event gets sent beforehand. This makes the TalkBack speech response flow more logical.
    Handler handler = new Handler();
    handler.postDelayed(
        new Runnable() {
          @Override
          public void run() {
            View view = getView();
            if (view == null) {
              // Something terrible has happened, e.g. the fragment is gone.
              return;
            }

            view.announceForAccessibility(getTitle());
            view.announceForAccessibility(page.getSubtitle());
            description.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
          }
        },
        DELAY_BEFORE_ANNOUNCE_LESSON);
    exercise.onInitialized(getActivity());
  }

  @Override
  public void onStop() {
    super.onStop();
    exercise.clear();
    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      service.getMenuManager().setMenuTransformer(null);
      service.getMenuManager().setMenuActionInterceptor(null);
      service.getSpeechController().interrupt(false /* stopTtsSpeechCompletely */);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    AppCompatActivity activity = (AppCompatActivity) getActivity();
    if (activity != null) {
      // show up arrow and specific lesson title
      ActionBar actionBar = activity.getSupportActionBar();
      if (actionBar != null) {
        actionBar.setTitle(getTitle());
        actionBar.setDisplayHomeAsUpEnabled(true);
      }

      LocalBroadcastManager.getInstance(activity)
          .registerReceiver(actionMonitor, GestureActionMonitor.FILTER);
    }

    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      service.addEventListener(exercise);
    }
  }

  private String getTitle() {
    if (!TextUtils.isEmpty(page.getTitle())) {
      return page.getTitle();
    } else {
      return lesson.getTitle();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      service.postRemoveEventListener(exercise);
    }

    Activity activity = getActivity();
    if (activity != null) {
      LocalBroadcastManager.getInstance(activity).unregisterReceiver(actionMonitor);
    }

    handler.removeCallbacksAndMessages(null);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
    int layoutId;
    if (exercise == null || tutorialController == null || callback == null) {
      return null;
    }
    if (exercise.needScrollableContainer()) {
      layoutId = R.layout.tutorial_lesson_fragment_scrollable;
    } else {
      layoutId = R.layout.tutorial_lesson_fragment;
    }
    View view = inflater.inflate(layoutId, container, false);

    description = (TextView) view.findViewById(R.id.description);
    description.setText(page.getDescription());

    TextView subTitle = (TextView) view.findViewById(R.id.part_subtitle);
    subTitle.setText(page.getSubtitle());

    TextView currentPage = (TextView) view.findViewById(R.id.current_page);
    TextView next = (TextView) view.findViewById(R.id.next);
    if (this.currentPage < lesson.getPagesCount() - 1) {
      next.setText(R.string.tutorial_next);
      currentPage.setVisibility(View.VISIBLE);
      currentPage.setText(
          getString(
              R.string.tutorial_page_number_of, this.currentPage + 1, lesson.getPagesCount() - 1));
    } else if (tutorialController.getNextLesson(lesson) == null) {
      next.setText(R.string.tutorial_home);
    } else {
      next.setText(R.string.tutorial_next_lesson);
    }
    next.setOnClickListener(this);

    View previous = view.findViewById(R.id.previous_page);
    previous.setOnClickListener(this);
    previous.setContentDescription(getString(R.string.tutorial_previous));

    ViewGroup contentContainer = (ViewGroup) view.findViewById(R.id.practice_area);
    View contentView = page.getExercise().getContentView(inflater, contentContainer);
    ViewGroup.LayoutParams params =
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    contentContainer.addView(contentView, params);

    return view;
  }

  @Override
  public void onClick(View v) {
    final int viewId = v.getId();
    if (viewId == R.id.next) {
      if (callback != null) {
        callback.onNextPageClicked(lesson, currentPage);
      }
    } else if (viewId == R.id.previous_page) {
      if (callback != null) {
        callback.onPreviousPageClicked(lesson, currentPage);
      }
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      // Handle action bar up button.
      if (callback != null) {
        callback.onNavigateUpClicked();
      }
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onExerciseCompleted(boolean autoSwitchLesson, int completeMessageResId) {
    if (speechController == null) {
      return;
    }

    SpeechController.UtteranceCompleteRunnable speechCallback = null;

    if (autoSwitchLesson) {
      speechCallback =
          new SpeechController.UtteranceCompleteRunnable() {
            @Override
            public void run(int status) {
              moveToNextPageWithDelay(DELAY_BEFORE_AUTO_MOVE_TO_NEXT_LESSON);
            }
          };
    }
    notifyExerciseCompleted(speechCallback, completeMessageResId);
  }

  private void notifyExerciseCompleted(
      final SpeechController.UtteranceCompleteRunnable callback, final int completeMessageResId) {
    if (!isResumed()) {
      return;
    }

    handler.postDelayed(
        new Runnable() {
          @Override
          public void run() {
            speechController.speak(
                getString(completeMessageResId), /* text */
                null, /* earcons */
                null, /* haptics */
                SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH, /* queueMode */
                0, /* flags */
                0, /* utteranceGroup */
                null, /* speechParams */
                null, /* nonSpeechParams */
                null, /* startAction */
                null, /* rangeStartCallback */
                callback, /*completeAction*/
                (EventId) null);
          }
        },
        DELAY_BEFORE_AUTO_MOVE_TO_NEXT_LESSON);
  }

  private void moveToNextPageWithDelay(long delay) {
    if (!isResumed()) {
      return;
    }

    handler.postDelayed(
        new Runnable() {
          @Override
          public void run() {
            if (getActivity() != null) {
              callback.onNextPageClicked(lesson, currentPage);
            }
          }
        },
        delay);
  }

  @Override
  public void onGestureAction(String action) {
    if (exercise == null || TextUtils.isEmpty(action) || getActivity() == null) {
      return;
    }

    exercise.onAction(getActivity(), action);
  }
}
